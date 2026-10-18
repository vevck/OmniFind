package com.omnifind.app.search

import com.omnifind.app.index.FileKind
import com.omnifind.app.index.IndexStore
import com.omnifind.app.index.SegmentKind
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/** Cosines of a query with every stored vector, in native code (`vectors.cpp`). */
object VectorMath {
    init {
        System.loadLibrary("omnifind")
    }

    fun scores(matrix: ByteBuffer, rows: Int, query: FloatArray): FloatArray =
        if (rows == 0) FloatArray(0) else nativeScores(matrix, rows, query)

    @JvmStatic private external fun nativeScores(matrix: ByteBuffer, rows: Int, query: FloatArray): FloatArray
}

data class SearchHit(
    val path: String,
    val kind: FileKind,
    val score: Double,
    /** How far its best piece stands out by meaning for this query, in standard deviations; null: words only. */
    val z: Double?,
    val segment: SegmentKind,
    /** A PDF's page (from 1), or a document chunk's index. */
    val loc: Int?,
    /** The matched words marked « », or the piece's start. */
    val excerpt: String?,
    val modified: Double,
    val size: Long,
    /** For pictures: width, height and the date taken (yyyy-MM-dd), when known. */
    val width: Int? = null,
    val height: Int? = null,
    val taken: String? = null,
)

/**
 * Hybrid search, ported from DigUp's `Searcher.swift` (MIT, Copyright (c) 2026 Abdur Rahim), its tuned numbers kept:
 * meaning (EmbeddingGemma 2's vectors, put on one scale per query) plus words (FTS5). A file qualifies by standing out in
 * meaning (z ≥ 2) or by holding at least half of the query's words; words count double for lookups (codes, numbers).
 *
 * Pieces still waiting for their vector are found by their words meanwhile, so search works from the first minutes of
 * indexing and gets better as embedding catches up.
 */
class Searcher(private val store: IndexStore) {
    private var matrix: ByteBuffer? = null
    private var rowSegment = LongArray(0)
    private var rowFile = LongArray(0)
    /** True for picture vectors, false for text. */
    private var rowPicture = BooleanArray(0)
    private var loaded = -1L
    private val fileKind = HashMap<Long, FileKind>()

    val vectorCount: Int get() = rowFile.size

    /** Loads the vectors again if indexing changed anything since the last search. */
    @Synchronized
    private fun refresh() {
        val generation = store.generation()
        if (generation == loaded) return
        val db = store.reader
        fileKind.clear()
        db.query("SELECT id, kind FROM files") { row -> FileKind.of(row.text(1))?.let { fileKind[row.long(0)] = it } }
        val rows = db.long("SELECT COUNT(*) FROM segments WHERE vec IS NOT NULL").toInt()
        val bytes = IndexStore.DIMENSION * 2
        val buffer = ByteBuffer.allocateDirect(maxOf(rows, 1) * bytes).order(ByteOrder.LITTLE_ENDIAN)
        val segments = LongArray(rows)
        val files = LongArray(rows)
        val pictures = BooleanArray(rows)
        var n = 0
        db.query("SELECT id, file, vec, modality FROM segments WHERE vec IS NOT NULL ORDER BY id") { row ->
            val vec = row.blob(2)
            if (n < rows && vec != null && vec.size == bytes) {
                buffer.put(vec)
                segments[n] = row.long(0)
                files[n] = row.long(1)
                pictures[n] = row.text(3) == "image"
                n++
            }
        }
        buffer.flip()
        matrix = buffer
        rowSegment = segments.copyOf(n)
        rowFile = files.copyOf(n)
        rowPicture = pictures.copyOf(n)
        loaded = generation
    }

    /**
     * Searches for [text]. [queryVector] is `Prompts.query(text)` embedded; without it (while it's being made, or with
     * no model on the phone) the search is by words only. [prefixLast]: the last word is still being typed.
     */
    @Synchronized
    fun search(
        text: String, queryVector: FloatArray?, limit: Int = 30, prefixLast: Boolean = false,
        kinds: Set<FileKind>? = null,
    ): List<SearchHit> {
        refresh()
        val db = store.reader
        val allowed = { file: Long -> kinds == null || fileKind[file] in kinds }

        // Meaning: every vector on this query's scale; each file keeps its best piece.
        val best = HashMap<Long, Pair<Double, Int>>()   // file → (z, row)
        val matrix = matrix
        if (queryVector != null && matrix != null && rowFile.isNotEmpty()) {
            val scores = VectorMath.scores(matrix, rowFile.size, queryVector)
            val z = meaning(scores, rowPicture)
            for (row in scores.indices) {
                val file = rowFile[row]
                if (!allowed(file)) continue
                if (z[row] > (best[file]?.first ?: Double.NEGATIVE_INFINITY)) best[file] = z[row] to row
            }
        }

        // Words: FTS5 over file names, folder names, page and document text.
        val terms = QueryText.terms(text)
        val patterns = QueryText.patterns(terms, prefixLast).toMutableList()
        // A word that's in no file as typed matches as the start of longer words ("grok" finds "Grok4"); a word that
        // is in the index stays whole, so "cat" doesn't bring in "category".
        for (i in terms.indices) {
            if (terms[i].length >= 3 && !patterns[i].endsWith("*") && !isIndexed(patterns[i])) patterns[i] += "*"
        }
        data class WordMatch(val rank: Int, val segment: Long, val snippet: String?)
        val words = LinkedHashMap<Long, WordMatch>()
        val coverage = HashMap<Long, Double>()
        if (patterns.isNotEmpty()) {
            db.query(
                """
                SELECT s.file, s.id, snippet(fts, 1, '«', '»', '…', 12) FROM fts JOIN segments s ON s.id = fts.rowid
                WHERE fts MATCH ? ORDER BY bm25(fts, 3.0, 1.0) LIMIT 1000
                """.trimIndent(),
                patterns.joinToString(" OR "),
            ) { row ->
                val file = row.long(0)
                if (words.size < 100 && file !in words && allowed(file)) {
                    words[file] = WordMatch(words.size + 1, row.long(1), row.text(2))
                }
            }
            if (words.isNotEmpty()) {
                val files = words.keys.joinToString(",")
                for (pattern in patterns) {
                    db.query(
                        "SELECT DISTINCT s.file FROM fts JOIN segments s ON s.id = fts.rowid " +
                            "WHERE fts MATCH ? AND s.file IN ($files)",
                        pattern,
                    ) { row -> coverage.merge(row.long(0), 1.0 / patterns.size, Double::plus) }
                }
            }
        }

        // Score: meaning z + a bonus that grows with word coverage, + 1 for the kind the query names. One common word
        // can't beat a strong meaning match; all the words of an exact lookup usually do.
        val lookup = QueryText.isLookup(terms)
        val meaningWeight = if (lookup) 0.5 else 1.0
        val wordsWeight = if (lookup) WORD_WEIGHT * 2 else WORD_WEIGHT
        val hinted = QueryText.kindHints(text)
        val candidates = HashSet<Long>()
        best.forEach { (file, value) -> if (value.first >= Z_GATE) candidates += file }
        coverage.forEach { (file, share) -> if (share >= 0.5) candidates += file }
        val scored = candidates.associateWith { file ->
            var score = meaningWeight * max(best[file]?.first ?: 0.0, 0.0) +
                wordsWeight * (coverage[file] ?: 0.0).pow(1.5)
            if (fileKind[file] in hinted) score += 1
            score
        }

        val hits = mutableListOf<SearchHit>()
        for ((file, score) in scored.entries.sortedWith(compareBy({ -it.value }, { it.key })).take(limit)) {
            val semantic = best[file]
            val keyword = words[file]
            // Point at the best meaning piece if it stands out; else at the piece the words matched.
            val meaningRow = semantic?.takeIf { it.first >= Z_GATE || keyword == null }?.second
            val segmentId = meaningRow?.let { rowSegment[it] } ?: keyword?.segment ?: continue
            var path = ""; var kind = FileKind.DOC; var modified = 0.0; var size = 0L
            var width: Int? = null; var height: Int? = null; var taken: String? = null
            db.query(
                "SELECT path, kind, mtime, size, json_extract(info, '$.width'), json_extract(info, '$.height'), " +
                    "json_extract(info, '$.taken') FROM files WHERE id = ?",
                file,
            ) {
                path = it.text(0) ?: ""
                kind = FileKind.of(it.text(1)) ?: FileKind.DOC
                modified = it.double(2)
                size = it.long(3)
                width = if (it.isNull(4)) null else it.int(4)
                height = if (it.isNull(5)) null else it.int(5)
                taken = it.text(6)
            }
            var segment = SegmentKind.NAME; var loc: Int? = null; var stored: String? = null
            db.query("SELECT kind, loc, excerpt FROM segments WHERE id = ?", segmentId) {
                segment = SegmentKind.of(it.text(0))
                loc = if (it.isNull(1)) null else it.double(1).toInt()
                stored = it.text(2)
            }
            // Quote the matched words only when they come from the piece we point at, so a hit on page 5 never shows
            // page 2's words.
            val snippet = keyword?.takeIf { it.segment == segmentId }?.snippet?.takeIf { "«" in it }
            hits += SearchHit(path, kind, score, semantic?.first, segment, loc, snippet ?: stored, modified, size,
                width, height, taken)
        }
        return hits
    }

    private fun isIndexed(pattern: String): Boolean =
        store.reader.single("SELECT 1 FROM fts WHERE fts MATCH ? LIMIT 1", pattern) { true } ?: false

    companion object {
        /** Stand out by this many standard deviations to count as a meaning match. */
        const val Z_GATE = 2.0
        const val WORD_WEIGHT = 2.0

        /**
         * This query's meaning scores, as DigUp makes them: each cosine minus the average of its kind (text and picture
         * vectors sit at different levels for the same query: measured 0.72 for the right note, 0.56 for an unrelated
         * photo), in standard deviations of all of them together (at least 0.02). A kind with fewer than 20 vectors
         * takes the average of all. Only pieces that stand out count, which also gives an honest "nothing found".
         */
        fun meaning(scores: FloatArray, picture: BooleanArray = BooleanArray(scores.size)): DoubleArray {
            if (scores.isEmpty()) return DoubleArray(0)
            val all = scores.sumOf { it.toDouble() } / scores.size
            fun average(pictures: Boolean): Double {
                var sum = 0.0
                var count = 0
                for (i in scores.indices) if (picture[i] == pictures) { sum += scores[i]; count++ }
                return if (count >= 20) sum / count else all
            }
            val textMean = average(false)
            val pictureMean = average(true)
            val centered = DoubleArray(scores.size) { scores[it] - if (picture[it]) pictureMean else textMean }
            val mean = centered.average()
            val variance = centered.sumOf { (it - mean) * (it - mean) } / centered.size
            val deviation = max(sqrt(variance), 0.02)
            return DoubleArray(centered.size) { (centered[it] - mean) / deviation }
        }

        /** How many of [hits] (best first) score at least [ratio] × the top one; the rest are "weaker matches". */
        fun strongCount(hits: List<SearchHit>, ratio: Double = 0.5): Int {
            val top = hits.firstOrNull()?.score ?: return 0
            if (top <= 0) return hits.size
            return hits.takeWhile { it.score >= top * ratio }.size
        }
    }
}
