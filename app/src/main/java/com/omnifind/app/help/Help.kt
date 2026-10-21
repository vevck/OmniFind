package com.omnifind.app.help

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The in-app User Guide (assets/help/guide.json): articles in sections, each a list of blocks (paragraphs with
 * **bold**, headings that are the article's "On this page" anchors, and figures cropped from real screenshots).
 */
@Serializable
data class Guide(val updated: String, val start: String, val sections: List<Section>, val articles: List<Article>) {
    @Serializable
    data class Section(val title: String, val articles: List<String>)

    @Serializable
    data class Article(val id: String, val section: String, val title: String, val minutes: Int, val blocks: List<Block>) {
        val anchors: List<Block> get() = blocks.filter { it.type == "h" }
        /** Everything a search looks at: title and all the text. */
        val searchable: String get() = (listOf(title) + blocks.mapNotNull { it.text ?: it.caption }).joinToString(" ")
    }

    @Serializable
    data class Block(
        val type: String,                 // "p", "h" or "figure"
        val text: String? = null,
        val anchor: String? = null,       // a heading's chip label under "On this page"
        val image: String? = null,        // assets/help/figures/<image>.webp
        val caption: String? = null,
    )

    fun article(id: String): Article? = articles.firstOrNull { it.id == id }

    companion object {
        fun parse(text: String): Guide = HelpJson.decodeFromString(serializer(), text)
        fun load(context: Context): Guide =
            context.assets.open("help/guide.json").bufferedReader().use { parse(it.readText()) }

        fun figure(context: Context, name: String): Bitmap? = runCatching {
            context.assets.open("help/figures/$name.webp").use(BitmapFactory::decodeStream)
        }.getOrNull()
    }
}

/**
 * The release notes of every version (assets/help/release_notes.json), newest first, made by
 * tools/release/make_docs.py from the release manifest and tools/release/history.json.
 */
@Serializable
data class ReleaseNotes(val versions: List<Version>) {
    @Serializable
    data class Version(
        val version: String, val build: Int, val date: String, val summary: String,
        val new: List<String> = emptyList(), val improved: List<String> = emptyList(),
        val fixed: List<String> = emptyList(), val thanks: String? = null,
    ) {
        val searchable: String get() = (listOf(version, summary) + new + improved + fixed + listOfNotNull(thanks)).joinToString(" ")
    }

    val latest: Version? get() = versions.firstOrNull()
    fun version(name: String): Version? = versions.firstOrNull { it.version == name }

    companion object {
        fun parse(text: String): ReleaseNotes = HelpJson.decodeFromString(serializer(), text)
        fun load(context: Context): ReleaseNotes =
            context.assets.open("help/release_notes.json").bufferedReader().use { parse(it.readText()) }
    }
}

private val HelpJson = Json { ignoreUnknownKeys = true }

/** Finding things in Help: every word of the query must appear (any order, ignoring case). */
object HelpSearch {
    private fun words(query: String) = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }

    fun matches(text: String, query: String): Boolean {
        val haystack = text.lowercase()
        return words(query).all { it in haystack }
    }

    fun articles(guide: Guide, query: String): List<Guide.Article> =
        if (query.isBlank()) guide.articles else guide.articles.filter { matches(it.searchable, query) }

    fun versions(notes: ReleaseNotes, query: String): List<ReleaseNotes.Version> =
        if (query.isBlank()) notes.versions else notes.versions.filter { matches(it.searchable, query) }
}
