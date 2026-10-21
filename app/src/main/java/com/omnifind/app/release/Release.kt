package com.omnifind.app.release

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * This build's release manifest (assets/release/release.json): its version and build number (Gradle takes
 * versionName and versionCode from the same file), the numbered tour of features, the fixes and known issues. The
 * in-app tour, the Release Notes and the walk-through in the product and design documents are all made from it
 * (tools/release/make_docs.py), so they always agree.
 */
@Serializable
data class Release(
    val product: String,
    val version: String,
    val build: Int,
    val date: String,
    val channel: String,
    val minAndroid: String,
    val targetAndroid: String,
    val summary: String,
    val testCases: List<TestCase>,
    val tour: List<TourStep>,
    val fixes: List<Fix>,
    val known: List<String>,
    /** Where the walk-through's results and screenshots come from (device, data, how the model got there). */
    val validationRun: Map<String, String> = emptyMap(),
) {
    /** "0.3.0-beta.3 · Build 5" */
    val label: String get() = "$version · Build $build"

    @Serializable
    data class TestCase(val id: String, val name: String, val test: String, val where: String, val result: String = "")

    @Serializable
    data class TourStep(
        val number: Int, val title: String, val overview: String, val screenshot: String, val status: String,
        val tests: List<String>,
        /** What the validation run actually showed for this feature ("“INV-2026-0417” → Invoice….pdf"). */
        val highlights: List<String> = emptyList(),
        /** Where the screenshot was taken: a device run, or rendered by a test. */
        val source: String = "",
    )

    @Serializable
    data class Fix(val title: String, val build: String)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): Release = json.decodeFromString(serializer(), text)

        fun load(context: Context): Release =
            context.assets.open("release/release.json").bufferedReader().use { parse(it.readText()) }

        /** A tour step's screenshot (assets/release/tour/<name>.webp), captured by the build's screenshot tests. */
        fun screenshot(context: Context, name: String): Bitmap? = runCatching {
            context.assets.open("release/tour/$name.webp").use(BitmapFactory::decodeStream)
        }.getOrNull()
    }
}

/**
 * When to show the tour: after a fresh install, and once after each update to a new build (not again on that build).
 * Kept in SharedPreferences, so clearing the app's data shows it again, as a fresh install would.
 */
class TourGate(context: Context) {
    private val prefs = context.getSharedPreferences("omnifind_tour", Context.MODE_PRIVATE)

    fun shouldShow(build: Int): Boolean = Companion.shouldShow(prefs.getInt(KEY, 0), build)

    fun markSeen(build: Int) = prefs.edit().putInt(KEY, build).apply()

    companion object {
        private const val KEY = "seen_build"

        /** [seenBuild] 0 is a fresh install; a lower number, an update. */
        fun shouldShow(seenBuild: Int, build: Int): Boolean = seenBuild < build
    }
}
