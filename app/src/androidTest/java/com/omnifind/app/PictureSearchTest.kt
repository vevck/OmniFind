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
 * Milestone 3 on the phone: a photo and a screenshot (DigUp's reference pictures) among 30 notes. Checks that unchanged
 * files are skipped, that types left out stay in the index, and that searches by meaning find the pictures while text
 * searches still find the notes. About two minutes on the Mi A1.
 */
@RunWith(AndroidJUnit4::class)
class PictureSearchTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext
    private val folder = File(app.cacheDir, "picture-test")
    private lateinit var store: IndexStore

    @Before
    fun setUp() {
        folder.deleteRecursively()
        File(folder, "Notes").mkdirs()
        for ((name, text) in SearchTestNotes.notes) File(folder, "Notes/$name").writeText(text)
        copy("reference/photo.jpg", File(folder, "Pictures/hills.jpg"))
        copy("reference/screen.png", File(folder, "Pictures/Screenshots/Screenshot_20261018-101500.png"))
        File(app.cacheDir, "picture-test.sqlite").delete()
        store = IndexStore(File(app.cacheDir, "picture-test.sqlite"))
    }

    @After
    fun tearDown() {
        store.close()
        folder.deleteRecursively()
    }

    private fun copy(asset: String, to: File) {
        to.parentFile!!.mkdirs()
        instrumentation.context.assets.open(asset).use { input -> to.outputStream().use { input.copyTo(it) } }
    }

    @Test
    fun picturesAmongNotes() {
        val indexer = Indexer(store)
        val roots = listOf(folder)

        // Documents first, as milestone 2 did.
        val first = indexer.readFiles(roots, setOf(FileKind.DOC), { false }) {}
        assertEquals(30, first.read)

        // Now pictures only: the notes aren't looked at, and stay in the index.
        val second = indexer.readFiles(roots, setOf(FileKind.IMAGE, FileKind.SCREENSHOT), { false }) {}
        assertEquals(2, second.read)
        assertEquals(0, second.removed)
        assertEquals(30, store.stats().byKind[FileKind.DOC])
        assertEquals(1, store.stats().byKind[FileKind.SCREENSHOT])

        // Everything chosen: all 32 already indexed and unchanged, so nothing is read again.
        val third = indexer.readFiles(roots, FileKind.entries.toSet(), { false }) {}
        assertEquals(0, third.read)
        assertEquals(32, third.skipped)

        val models = ModelFiles.locate(app)
        assertNotNull("push the model files first", models)
        assertNotNull("the image encoder isn't on the phone", models!!.media)
        val budgets = mapOf(FileKind.IMAGE to 70, FileKind.SCREENSHOT to 140)
        Embedder.open(models, textOnly = false, maxTokens = 2048).use { embedder ->
            val started = System.nanoTime()
            val embedded = indexer.embed(embedder, embedder, budgets, app.cacheDir, stopped = { false }) {}
            Log.i("OmniFind/test", "embedded $embedded pieces in %.1f s".format((System.nanoTime() - started) / 1e9))
            assertEquals(32, embedded)
            assertTrue(!store.stats().anyWaiting)

            // Embedding again finds nothing to do: no vector is made twice.
            assertEquals(0, indexer.embed(embedder, embedder, budgets, app.cacheDir, stopped = { false }) {})

            val searcher = Searcher(store)
            fun first(query: String): String {
                val vector = embedder.embedTexts(listOf(Prompts.query(query))).single()
                val hits = searcher.search(query, vector)
                Log.i("OmniFind/test", "\"$query\" → ${hits.take(3).map { File(it.path).name to "%.2f".format(it.score) }}")
                assertTrue("nothing found for \"$query\"", hits.isNotEmpty())
                return File(hits.first().path).name
            }
            assertEquals("hills.jpg", first("green hills under a sunny sky"))
            assertEquals("hills.jpg", first("a landscape with a tree"))
            assertEquals("Screenshot_20261018-101500.png", first("screenshot of an error message"))
            // Text still wins its own searches with pictures in the index.
            assertEquals("lease.txt", first("am I allowed to have pets in the flat"))
            assertEquals("car.txt", first("vehicle maintenance"))

            // The Pictures filter shows pictures only.
            val vector = embedder.embedTexts(listOf(Prompts.query("hills"))).single()
            val pictures = searcher.search("hills", vector, kinds = setOf(FileKind.IMAGE, FileKind.SCREENSHOT))
            assertTrue(pictures.all { it.kind.isPicture })
        }
    }
}
