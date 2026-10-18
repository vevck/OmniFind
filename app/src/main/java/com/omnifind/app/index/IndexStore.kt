package com.omnifind.app.index

import android.util.Half
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** One searchable piece of a file. */
enum class SegmentKind(val id: String) {
    NAME("name"),     // file name + folder names (keyword search only)
    PAGE("page"),     // a PDF page's text, `loc` = page number from 1
    CHUNK("chunk"),   // a piece of a document's text, `loc` = its index from 0
    IMAGE("image");   // a photo or screenshot itself

    companion object {
        fun of(id: String?): SegmentKind = entries.firstOrNull { it.id == id } ?: NAME
    }
}

/**
 * The index: one SQLite file with a row per file, a row per searchable piece (with its vector once embedded, 768 ×
 * float16), and FTS5 keyword search over all of it. DigUp's schema (`IndexStore.swift`, MIT, Copyright (c) 2026 Abdur
 * Rahim) with one addition: `rank`, a piece's place in its file, so the first piece of every file is embedded before the
 * second piece of any (on the Mi A1 a piece takes ~25 s; keyword search covers everything from the start).
 */
class IndexStore(file: File) : AutoCloseable {
    /** Indexing's connection; [reader] is search's (WAL lets it read while indexing writes). */
    val writer = Database(file)
    val reader = Database(file)

    init {
        if (writer.long("PRAGMA user_version") == 0L) create()
        if (writer.long("PRAGMA user_version") == 1L) migrateToPictures()
        val stored = writer.single("SELECT value FROM meta WHERE key = 'vector_source'") { it.text(0) }
        if (stored != VECTOR_SOURCE) {
            // Vectors from another model or llama.cpp never mix with these: everything is embedded again.
            writer.transaction {
                writer.run("UPDATE segments SET vec = NULL WHERE vec IS NOT NULL")
                writer.run("INSERT OR REPLACE INTO meta(key, value) VALUES('vector_source', ?)", VECTOR_SOURCE)
                bumpGeneration()
            }
        }
    }

    /** Schema 1 → 2: pictures. Nothing already indexed or embedded changes; pictures just get their own queue. */
    private fun migrateToPictures() = writer.transaction {
        writer.execute("CREATE INDEX IF NOT EXISTS segments_waiting_media ON segments(rank, id) WHERE vec IS NULL AND modality = 'image'")
        writer.execute("PRAGMA user_version = 2")
    }

    /**
     * The picture settings the image vectors were made with ("photo70 shot140"). When they change, only the pictures'
     * vectors are made again; text keeps all of its own.
     */
    fun usePictureSettings(source: String) {
        val stored = writer.single("SELECT value FROM meta WHERE key = 'image_source'") { it.text(0) }
        if (stored == source) return
        writer.transaction {
            writer.run("UPDATE segments SET vec = NULL WHERE modality = 'image' AND vec IS NOT NULL")
            writer.run("INSERT OR REPLACE INTO meta(key, value) VALUES('image_source', ?)", source)
            bumpGeneration()
        }
    }

    private fun create() = writer.transaction {
        writer.execute("CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        writer.execute(
            """
            CREATE TABLE files(
                id INTEGER PRIMARY KEY,
                path TEXT NOT NULL UNIQUE,
                kind TEXT NOT NULL,
                size INTEGER NOT NULL,
                mtime REAL NOT NULL,
                state TEXT NOT NULL DEFAULT 'pending',   -- pending | done | skipped | failed
                detail TEXT,                              -- why skipped/failed, or what was read
                info TEXT,                                -- JSON: title, pages
                indexed_at REAL
            )
            """.trimIndent(),
        )
        writer.execute("CREATE INDEX files_state ON files(state, kind)")
        writer.execute(
            """
            CREATE TABLE segments(
                id INTEGER PRIMARY KEY,
                file INTEGER NOT NULL REFERENCES files(id) ON DELETE CASCADE,
                kind TEXT NOT NULL,       -- SegmentKind
                modality TEXT NOT NULL,   -- 'text' or 'image' (gets a vector), 'none' (keyword search only)
                loc REAL,                 -- PDF page, or chunk index
                rank INTEGER NOT NULL DEFAULT 0,
                excerpt TEXT,
                vec BLOB                  -- 768 × float16, L2-normalized; NULL until embedded
            )
            """.trimIndent(),
        )
        writer.execute("CREATE INDEX segments_file ON segments(file)")
        writer.execute("CREATE INDEX segments_waiting ON segments(rank, id) WHERE vec IS NULL AND modality = 'text'")
        writer.execute("CREATE INDEX segments_waiting_media ON segments(rank, id) WHERE vec IS NULL AND modality = 'image'")
        writer.execute("CREATE VIRTUAL TABLE fts USING fts5($FTS_COLUMNS)")
        writer.run("INSERT INTO meta(key, value) VALUES('vector_source', ?)", VECTOR_SOURCE)
        writer.run("INSERT INTO meta(key, value) VALUES('generation', '0')")
        writer.execute("PRAGMA user_version = $SCHEMA_VERSION")
    }

    // MARK: Files

    data class Known(val id: Long, val kind: FileKind?, val size: Long, val mtime: Double, val state: String)

    /** Every indexed file, by path. */
    fun knownFiles(): Map<String, Known> {
        val out = HashMap<String, Known>()
        writer.query("SELECT id, path, kind, size, mtime, state FROM files") {
            out[it.text(1)!!] = Known(it.long(0), FileKind.of(it.text(2)), it.long(3), it.double(4), it.text(5)!!)
        }
        return out
    }

    /** Reads [file] into the index again: its old pieces go, its name and new pieces come in, all in one transaction. */
    fun replace(file: File, kind: FileKind, extracted: Extracted) = writer.transaction {
        val id = upsert(file, kind)
        deleteSegments(id)
        insertName(id, file)
        extracted.pieces.forEachIndexed { rank, piece ->
            val modality = if (SearchText.hasWords(piece.text)) "text" else "none"
            writer.run(
                "INSERT INTO segments(file, kind, modality, loc, rank, excerpt) VALUES(?, ?, ?, ?, ?, ?)",
                id, piece.kind.id, modality, piece.loc, rank, SearchText.excerpt(piece.text),
            )
            insertKeywords(writer.lastInsertId, name = "", body = piece.text)
        }
        val info = buildMap {
            extracted.title?.let { put("title", JsonPrimitive(it)) }
            extracted.pages?.let { put("pages", JsonPrimitive(it)) }
        }
        val detail = if (kind == FileKind.PDF) "${extracted.pieces.size} of ${extracted.pages ?: 0} pages with text"
        else "${extracted.pieces.size} pieces"
        val state = if (extracted.pieces.isEmpty()) "skipped" else "done"
        writer.run(
            "UPDATE files SET state = ?, detail = ?, info = ?, indexed_at = ? WHERE id = ?",
            state, if (extracted.pieces.isEmpty()) "no text" else detail,
            if (info.isEmpty()) null else JsonObject(info).toString(), System.currentTimeMillis() / 1000.0, id,
        )
        bumpGeneration()
    }

    /** A file that couldn't be read: still found by its name, and not tried again until it changes. */
    fun fail(file: File, kind: FileKind, reason: String) = writer.transaction {
        val id = upsert(file, kind)
        deleteSegments(id)
        insertName(id, file)
        writer.run("UPDATE files SET state = 'failed', detail = ?, indexed_at = ? WHERE id = ?",
            reason.take(300), System.currentTimeMillis() / 1000.0, id)
        bumpGeneration()
    }

    /** A photo or screenshot: found by its name and folders at once, by what it shows once embedded. */
    fun replacePicture(file: File, kind: FileKind, info: Pictures.Info) = writer.transaction {
        val id = upsert(file, kind)
        deleteSegments(id)
        insertName(id, file)
        val small = minOf(info.width, info.height) < Rules.MIN_IMAGE_SIDE
        if (!small) writer.run("INSERT INTO segments(file, kind, modality, rank) VALUES(?, 'image', 'image', 0)", id)
        val json = buildMap {
            put("width", JsonPrimitive(info.width))
            put("height", JsonPrimitive(info.height))
            info.taken?.let { put("taken", JsonPrimitive(it)) }
        }
        writer.run(
            "UPDATE files SET state = ?, detail = ?, info = ?, indexed_at = ? WHERE id = ?",
            if (small) "skipped" else "done", if (small) "too small (${info.width}×${info.height})" else null,
            JsonObject(json).toString(), System.currentTimeMillis() / 1000.0, id,
        )
        bumpGeneration()
    }

    /** A piece that can't be embedded (a picture that won't decode): keyword search only from now on. */
    fun giveUp(segment: Long, reason: String) = writer.transaction {
        writer.run("UPDATE segments SET modality = 'none' WHERE id = ?", segment)
        writer.run("UPDATE files SET state = 'failed', detail = ? WHERE id = (SELECT file FROM segments WHERE id = ?)",
            reason.take(300), segment)
    }

    fun remove(id: Long) = writer.transaction {
        writer.run("DELETE FROM fts WHERE rowid IN (SELECT id FROM segments WHERE file = ?)", id)
        writer.run("DELETE FROM files WHERE id = ?", id)   // its segments go with it (ON DELETE CASCADE)
        bumpGeneration()
    }

    private fun upsert(file: File, kind: FileKind): Long {
        writer.run(
            """
            INSERT INTO files(path, kind, size, mtime) VALUES(?, ?, ?, ?)
            ON CONFLICT(path) DO UPDATE SET kind = excluded.kind, size = excluded.size, mtime = excluded.mtime
            """.trimIndent(),
            file.path, kind.id, file.length(), file.lastModified() / 1000.0,
        )
        return writer.long("SELECT id FROM files WHERE path = ?", file.path)
    }

    private fun deleteSegments(file: Long) {
        writer.run("DELETE FROM fts WHERE rowid IN (SELECT id FROM segments WHERE file = ?)", file)
        writer.run("DELETE FROM segments WHERE file = ?", file)
    }

    private fun insertName(file: Long, path: File) {
        writer.run("INSERT INTO segments(file, kind, modality) VALUES(?, 'name', 'none')", file)
        insertKeywords(writer.lastInsertId, name = path.name, body = folderText(path))
    }

    private fun insertKeywords(segment: Long, name: String, body: String) {
        val normalized = SearchText.normalized(body)
        writer.run(
            "INSERT INTO fts(rowid, name, body, original) VALUES(?, ?, ?, ?)",
            segment, SearchText.normalized(name), normalized, if (normalized == body) null else body,
        )
    }

    // MARK: Embedding

    /** A piece waiting for its vector: text with what goes into its prompt, or a picture by its file. */
    data class Waiting(val segment: Long, val modality: String, val kind: FileKind?, val path: String,
                       val title: String, val text: String)

    /**
     * The next pieces to embed: every file's first piece before anyone's second, oldest first within a rank. Pictures
     * only when [pictures] (they need the image encoder loaded).
     */
    fun waiting(limit: Int, pictures: Boolean): List<Waiting> {
        val out = mutableListOf<Waiting>()
        val modalities = if (pictures) "('text', 'image')" else "('text')"
        writer.query(
            """
            SELECT s.id, s.modality, f.kind, f.path, json_extract(f.info, '$.title'), fts.original, fts.body
            FROM segments s JOIN files f ON f.id = s.file LEFT JOIN fts ON fts.rowid = s.id
            WHERE s.vec IS NULL AND s.modality IN $modalities
            ORDER BY s.rank, s.id LIMIT ?
            """.trimIndent(),
            limit,
        ) {
            val path = it.text(3)!!
            val title = it.text(4) ?: File(path).nameWithoutExtension
            out += Waiting(it.long(0), it.text(1)!!, FileKind.of(it.text(2)), path, title, it.text(5) ?: it.text(6) ?: "")
        }
        return out
    }

    /** Stores vectors (L2-normalized, 768 floats) as float16. A piece deleted meanwhile is just not there. */
    fun saveVectors(vectors: List<Pair<Long, FloatArray>>) = writer.transaction {
        for ((segment, vector) in vectors) writer.run("UPDATE segments SET vec = ? WHERE id = ?", half(vector), segment)
        bumpGeneration()
    }

    /** Changes whenever what search sees changes, so it knows to load the vectors again. */
    fun generation(): Long = reader.long("SELECT CAST(value AS INTEGER) FROM meta WHERE key = 'generation'")

    private fun bumpGeneration() =
        writer.run("UPDATE meta SET value = CAST(value AS INTEGER) + 1 WHERE key = 'generation'")

    // MARK: Stats

    data class Stats(
        val files: Int, val done: Int, val failed: Int, val skipped: Int,
        /** Text pieces: all, with a vector, still waiting. */
        val pieces: Int, val embedded: Int, val waiting: Int,
        /** Pictures waiting for a vector, by kind, and pictures with one. */
        val picturesWaiting: Map<FileKind, Int>, val picturesEmbedded: Int,
        /** Files in the index, by kind (whatever their state). */
        val byKind: Map<FileKind, Int>,
    ) {
        val pictures: Int get() = picturesEmbedded + picturesWaiting.values.sum()
        val anyWaiting: Boolean get() = waiting > 0 || picturesWaiting.values.any { it > 0 }
    }

    fun stats(): Stats {
        var files = 0; var done = 0; var failed = 0; var skipped = 0
        reader.query("SELECT state, COUNT(*) FROM files GROUP BY state") {
            val n = it.int(1)
            files += n
            when (it.text(0)) { "done" -> done = n; "failed" -> failed = n; "skipped" -> skipped = n }
        }
        val byKind = HashMap<FileKind, Int>()
        reader.query("SELECT kind, COUNT(*) FROM files GROUP BY kind") { row ->
            FileKind.of(row.text(0))?.let { byKind[it] = row.int(1) }
        }
        var pieces = 0; var embedded = 0
        reader.query("SELECT COUNT(*), COUNT(vec) FROM segments WHERE modality = 'text'") {
            pieces = it.int(0); embedded = it.int(1)
        }
        val picturesWaiting = HashMap<FileKind, Int>()
        var picturesEmbedded = 0
        reader.query(
            "SELECT f.kind, COUNT(*) - COUNT(s.vec), COUNT(s.vec) FROM segments s JOIN files f ON f.id = s.file " +
                "WHERE s.modality = 'image' GROUP BY f.kind",
        ) { row ->
            FileKind.of(row.text(0))?.let { picturesWaiting[it] = row.int(1) }
            picturesEmbedded += row.int(2)
        }
        return Stats(files, done, failed, skipped, pieces, embedded, pieces - embedded, picturesWaiting,
            picturesEmbedded, byKind)
    }

    override fun close() {
        reader.close()
        writer.close()
    }

    companion object {
        const val SCHEMA_VERSION = 2
        const val DIMENSION = 768

        /** What made the vectors: index fingerprints change with any of it (see ReferenceVectorTest). */
        const val VECTOR_SOURCE = "google/embeddinggemma-2@bfcd298 q8_0 llama.cpp-b11461 vectors1 page2000 chunk1800"

        /**
         * Keyword search's tokenizer, as DigUp's: Porter stemming over unicode61, marks (M*) counted as letters so
         * Bengali vowel signs and Arabic harakat stay inside words. `original` keeps text that normalizing changed.
         */
        const val FTS_COLUMNS =
            "name, body, original UNINDEXED, tokenize = \"porter unicode61 remove_diacritics 2 categories 'L* N* Co M*'\""

        /**
         * The folders a file sits in on its volume ("Download Invoices"), as low-weight keywords. Files on an SD card
         * get "sdcard" too, so "on the SD card" in a search counts.
         */
        fun folderText(file: File): String {
            val folder = file.parent ?: return ""
            val folders = Volumes.relative(folder).split('/').filter { it.isNotEmpty() }
            return (if (Volumes.onCard(folder)) listOf("sdcard") + folders else folders).joinToString(" ")
        }

        fun half(vector: FloatArray): ByteArray {
            val buffer = ByteBuffer.allocate(vector.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (value in vector) buffer.putShort(Half.toHalf(value))
            return buffer.array()
        }
    }
}
