package com.omnifind.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omnifind.app.engine.Embedder
import com.omnifind.app.engine.ModelFiles
import com.omnifind.app.engine.Prompts
import com.omnifind.app.index.FileKind
import com.omnifind.app.index.IndexStore
import com.omnifind.app.index.Indexer
import com.omnifind.app.search.Searcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Milestone 2 end to end on the phone: 30 notes go through both indexing passes (keywords, then real EmbeddingGemma 2
 * vectors) into a fresh index, and searches by words and by meaning find the right one. About a minute on the Mi A1.
 */
@RunWith(AndroidJUnit4::class)
class SearchTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private val folder = File(app.cacheDir, "search-test")
    private lateinit var store: IndexStore

    private val notes = SearchTestNotes.notes

    @Before
    fun setUp() {
        folder.deleteRecursively()
        folder.mkdirs()
        for ((name, text) in notes) File(folder, name).writeText(text)
        File(app.cacheDir, "search-test.sqlite").delete()
        store = IndexStore(File(app.cacheDir, "search-test.sqlite"))
    }

    @After
    fun tearDown() {
        store.close()
        folder.deleteRecursively()
    }

    @Test
    fun wordsThenMeaning() {
        val indexer = Indexer(store)
        assertEquals(notes.size, indexer.readFiles(listOf(folder), setOf(FileKind.DOC, FileKind.PDF), { false }) {}.read)
        val searcher = Searcher(store)

        // Before any vector: words alone find an exact code.
        val code = searcher.search("INV-2026-0417", null)
        assertEquals("invoice.txt", File(code.first().path).name)

        val models = ModelFiles.locate(app)
        assertNotNull("push the model files first", models)
        Embedder.open(models!!, textOnly = true, maxTokens = 2048).use { embedder ->
            val started = System.nanoTime()
            val embedded = indexer.embed(embedder, null, emptyMap(), app.cacheDir, stopped = { false }) {}
            Log.i("OmniFind/test", "embedded $embedded pieces in %.1f s".format((System.nanoTime() - started) / 1e9))
            assertEquals(notes.size, embedded)
            assertEquals(0, store.stats().waiting)

            fun first(query: String): String {
                val vector = embedder.embedTexts(listOf(Prompts.query(query))).single()
                val hits = searcher.search(query, vector)
                Log.i("OmniFind/test", "\"$query\" → ${hits.take(3).map { File(it.path).name to "%.2f".format(it.score) }}")
                assertTrue("nothing found for \"$query\"", hits.isNotEmpty())
                return File(hits.first().path).name
            }
            // By meaning: none of these share a content word with their note.
            assertEquals("lease.txt", first("am I allowed to have pets in the flat"))
            assertEquals("car.txt", first("vehicle maintenance"))
            assertEquals("cooking.txt", first("how to make lentil soup"))
            // Across languages: Arabic and Bengali questions find English notes.
            assertEquals("library.txt", first("متى تغلق المكتبة"))
            assertEquals("weather.txt", first("বৃষ্টির পূর্বাভাস"))
        }
    }
}
