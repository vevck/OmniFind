package com.omnifind.app.index

import android.util.Log
import com.omnifind.app.engine.Prompts
import java.io.File

/** Something that turns prompt-framed texts into vectors ([com.omnifind.app.engine.Embedder], or a fake in tests). */
fun interface TextEmbedder {
    fun embedTexts(texts: List<String>): List<FloatArray>
}

/** Something that turns a picture into a vector at a vision budget (the full model, with its image encoder). */
fun interface ImageEmbedder {
    fun embedImage(file: File, budget: Int): FloatArray
}

/**
 * Reads the chosen folders into the index in two passes:
 *
 * 1. [readFiles]: every new or changed file goes into keyword search (by its words, or a picture by its name and
 *    folders), and its pieces wait for vectors. A file already in the index and unchanged (same size and date) is
 *    skipped: nothing is read or embedded twice. After this, every word in every file can be found.
 * 2. [embed]: pieces get their meaning vectors, first pieces first. On the Mi A1 a text piece takes ~25 s and a photo
 *    ~18 s, so this runs while charging and stops whenever asked; nothing is lost when it does.
 */
class Indexer(private val store: IndexStore) {

    data class Progress(val phase: String, val done: Int, val total: Int, val current: String? = null)

    /** What a reading pass did. */
    data class Summary(val read: Int, val skipped: Int, val removed: Int) {
        override fun toString(): String = buildList {
            add("$read new or changed")
            add("$skipped skipped")
            if (removed > 0) add("$removed removed")
        }.joinToString(" · ")
    }

    /** Walks [roots] (skipping what [Rules] says to) for the files of [kinds]. */
    fun crawl(roots: List<File>, kinds: Set<FileKind>): List<Pair<File, FileKind>> {
        val out = mutableListOf<Pair<File, FileKind>>()
        for (root in roots.filter { it.isDirectory }) {
            root.walkTopDown()
                .onEnter { it == root || !Rules.skipsFolder(it) }
                .filter { it.isFile }
                .forEach { file -> Rules.classify(file)?.takeIf { it in kinds }?.let { out += file to it } }
        }
        return out.distinctBy { it.first.path }
    }

    /**
     * Pass 1, for the files of [kinds] in [roots]. Files of kinds not chosen this time stay in the index as they are
     * (still searchable, not read again). A file leaves the index only when it's gone from the phone, or its folder
     * is no longer chosen. A chosen folder that isn't there (an SD card taken out) keeps its files.
     */
    fun readFiles(
        roots: List<File>, kinds: Set<FileKind>, stopped: () -> Boolean, progress: (Progress) -> Unit,
    ): Summary {
        progress(Progress("Looking for files", 0, 0))
        val found = crawl(roots, kinds)
        val known = store.knownFiles()

        val present = found.mapTo(HashSet()) { it.first.path }
        val chosen = roots.map { it.path.trimEnd('/') + "/" }
        val away = roots.filter { !it.isDirectory }.map { it.path.trimEnd('/') + "/" }
        var removed = 0
        for ((path, file) in known) {
            if (path in present || away.any { path.startsWith(it) }) continue
            val inChosenFolder = chosen.any { path.startsWith(it) }
            val gone = when {
                !inChosenFolder -> true                    // its folder was unticked
                file.kind in kinds -> true                 // looked for, and not found: deleted or now excluded
                else -> !File(path).exists()               // a kind not looked for this time: only if deleted
            }
            if (gone) {
                store.remove(file.id)
                removed++
            }
        }

        val changed = found.filter { (file, _) ->
            val old = known[file.path]
            old == null || old.size != file.length() || old.mtime != file.lastModified() / 1000.0 || old.state == "pending"
        }
        val skipped = found.size - changed.size
        if (skipped > 0) Log.i(TAG, "$skipped files already indexed and unchanged: skipped")
        changed.forEachIndexed { index, (file, kind) ->
            if (stopped()) return Summary(index, skipped, removed)
            progress(Progress("Reading files", index, changed.size, file.name))
            try {
                if (kind.isPicture) {
                    val info = Pictures.info(file) ?: throw IllegalStateException("Android can't decode this picture")
                    store.replacePicture(file, kind, info)
                } else {
                    store.replace(file, kind, Extractors.extract(file, kind))
                }
            } catch (error: Throwable) {
                if (error is OutOfMemoryError) System.gc()
                Log.w(TAG, "can't read ${file.path}", error)
                store.fail(file, kind, error.message ?: error.javaClass.simpleName)
            }
        }
        return Summary(changed.size, skipped, removed)
    }

    /**
     * Pass 2: embeds waiting pieces until none wait or [stopped] says so (checked between batches). Text goes [batch]
     * at a time; pictures one at a time, and only with an [images] embedder (the full model). Pieces that already have
     * a vector are never embedded again. Returns how many were embedded.
     */
    fun embed(
        texts: TextEmbedder, images: ImageEmbedder?, budgets: Map<FileKind, Int>, cache: File,
        batch: Int = 2, stopped: () -> Boolean, progress: (Progress) -> Unit,
    ): Int {
        var embedded = 0
        val stats = store.stats()
        val total = stats.waiting + if (images != null) stats.picturesWaiting.values.sum() else 0
        while (!stopped()) {
            val waiting = store.waiting(batch, pictures = images != null)
            if (waiting.isEmpty()) break
            // One kind at a time: the texts at the front of the queue together, or the one picture there.
            val first = waiting.first()
            if (first.modality == "image" && images != null) {
                progress(Progress("Embedding pictures", embedded, total, File(first.path).name))
                val budget = budgets[first.kind] ?: DEFAULT_PHOTO_BUDGET
                try {
                    val upright = Pictures.prepare(File(first.path), cache)
                    try {
                        store.saveVectors(listOf(first.segment to images.embedImage(upright, budget)))
                    } finally {
                        upright.delete()
                    }
                } catch (error: Throwable) {
                    if (error is OutOfMemoryError) System.gc()
                    Log.w(TAG, "can't embed ${first.path}", error)
                    store.giveUp(first.segment, error.message ?: error.javaClass.simpleName)
                }
                embedded++
            } else {
                val run = waiting.takeWhile { it.modality == "text" }
                progress(Progress("Embedding text", embedded, total, first.title))
                val vectors = texts.embedTexts(run.map { Prompts.document(it.title, it.text.take(EMBED_CHARACTERS)) })
                store.saveVectors(run.map { it.segment }.zip(vectors))
                embedded += run.size
            }
        }
        return embedded
    }

    companion object {
        private const val TAG = "OmniFind/index"

        /**
         * A piece's text that goes into its vector. Chunks are 1,800 characters already; a PDF page can be far
         * longer, and on the Mi A1 4,000 characters (DigUp's cut) would be about a minute a page. Keyword search
         * still has the whole page.
         */
        const val EMBED_CHARACTERS = 2000

        /** Vision soft tokens: photos at 70 (~18 s each on the Mi A1), screenshots at 140 (~40 s; text needs detail). */
        const val DEFAULT_PHOTO_BUDGET = 70
        const val DEFAULT_SCREENSHOT_BUDGET = 140
    }
}
