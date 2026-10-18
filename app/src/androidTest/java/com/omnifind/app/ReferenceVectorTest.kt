package com.omnifind.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omnifind.app.engine.Embedder
import com.omnifind.app.engine.ModelFiles
import com.omnifind.app.engine.Prompts
import com.omnifind.app.engine.cosine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Checks that EmbeddingGemma 2 on this phone makes the vectors DigUp's reference test expects
 * (`Tests/LlamaRuntimeTests/ReferenceVectorTests.swift`): the same inputs (assets/reference/inputs.json), compared
 * with the vectors llama.cpp b11461 made on a Mac (assets/reference/vectors.json). Anything below DigUp's bar
 * (cosine 0.999) means a framing, tokenizing or resizing bug, or a llama.cpp that makes other vectors.
 *
 * Needs the model files on the phone first (README: `adb push models/. …`). It also logs how long each input took:
 *
 *     adb logcat -s OmniFind/test
 */
@RunWith(AndroidJUnit4::class)
class ReferenceVectorTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext
    private val assets = instrumentation.context.assets   // the test APK's assets

    @Test
    fun vectorsMatchDigUpReference() {
        val models = ModelFiles.locate(app)
        assertNotNull("no ${ModelFiles.text.name} in ${ModelFiles.folder(app)}: push the model files first", models)
        assertNotNull("no ${ModelFiles.media.name} in ${ModelFiles.folder(app)}", models!!.media)

        val inputs = json("reference/inputs.json")
        val expected = json("reference/vectors.json")["vectors"]!!.jsonObject
        val agreement = inputs["agreement"]!!.jsonPrimitive.float
        val made = linkedMapOf<String, FloatArray>()
        val seconds = linkedMapOf<String, Double>()

        Embedder.open(models, textOnly = false).use { embedder ->
            log("loaded in %.1f s; %s".format(embedder.loadSeconds, Embedder.systemInfo()))
            assertEquals(768, embedder.dimension)
            for (input in inputs["texts"]!!.jsonArray.map { it.jsonObject }) {
                val name = input.string("name")
                timed(name, seconds) { made[name] = embedder.embedTexts(listOf(input.string("text"))).single() }
            }
            for (input in inputs["images"]!!.jsonArray.map { it.jsonObject }) {
                val name = input.string("name")
                val file = copy("reference/${input.string("file")}")
                timed(name, seconds) { made[name] = embedder.embedImage(file, input["budget"]!!.jsonPrimitive.int) }
            }
            for (input in inputs["audio"]!!.jsonArray.map { it.jsonObject }) {
                val name = input.string("name")
                val file = copy("reference/${input.string("file")}")
                timed(name, seconds) { made[name] = embedder.embedAudio(file) }
            }
        }

        assertEquals(expected.keys, made.keys)
        val low = mutableListOf<String>()
        for ((name, vector) in made) {
            val reference = expected[name]!!.jsonArray.map { it.jsonPrimitive.float }.toFloatArray()
            val score = cosine(vector, reference)
            log("%-14s cosine %.5f  %6.2f s".format(name, score, seconds[name]))
            if (score < agreement) low += "$name: %.5f".format(score)
        }
        assertTrue("below cosine $agreement: $low", low.isEmpty())
    }

    @Test
    fun aQueryFindsItsDocument() {
        val models = ModelFiles.locate(app)
        assertNotNull("push the model files first", models)
        Embedder.open(models!!, textOnly = true).use { embedder ->
            val documents = listOf(
                "Your card payment was declined. Please try another payment method.",
                "Preheat the oven to 200 °C and roast the vegetables for 25 minutes.",
                "The library closes at 8 pm on weekdays.",
            ).map { Prompts.document(null, it) }
            val vectors = embedder.embedTexts(documents)
            val query = embedder.embedTexts(listOf(Prompts.query("متى تغلق المكتبة"))).single()
            val best = vectors.indices.maxBy { cosine(query, vectors[it]) }
            assertEquals("an Arabic question about the library's hours should find the library sentence", 2, best)
        }
    }

    private fun json(path: String): JsonObject =
        Json.parseToJsonElement(assets.open(path).bufferedReader().use { it.readText() }).jsonObject

    /** mtmd reads files by path: assets go to the app's cache first. */
    private fun copy(asset: String): File {
        val file = File(app.cacheDir, asset.substringAfterLast('/'))
        assets.open(asset).use { input -> file.outputStream().use { input.copyTo(it) } }
        return file
    }

    private fun JsonObject.string(key: String) = this[key]!!.jsonPrimitive.content

    private inline fun timed(name: String, into: MutableMap<String, Double>, block: () -> Unit) {
        log("embedding $name…")
        val started = System.nanoTime()
        block()
        into[name] = (System.nanoTime() - started) / 1e9
        log("  $name done in %.1f s".format(into[name]))
    }

    private fun log(line: String) {
        Log.i("OmniFind/test", line)
        println(line)
    }
}
