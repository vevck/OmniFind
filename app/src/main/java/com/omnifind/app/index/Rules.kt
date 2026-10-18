package com.omnifind.app.index

import java.io.File

/** What a file is, for indexing, for choosing what to index, and for grouping results. */
enum class FileKind(val id: String, val label: String) {
    DOC("doc", "Documents"),
    PDF("pdf", "PDFs"),
    IMAGE("image", "Photos"),
    SCREENSHOT("screenshot", "Screenshots");

    val isPicture: Boolean get() = this == IMAGE || this == SCREENSHOT

    companion object {
        fun of(id: String?): FileKind? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Which files OmniFind reads, after DigUp's `Rules.swift` (MIT, Copyright (c) 2026 Abdur Rahim), plus a phone's own
 * clutter. It only reads; it never changes, moves or uploads a file.
 */
object Rules {
    val docExtensions = setOf("txt", "text", "md", "markdown", "html", "htm", "docx", "odt")

    /** What Android 9 decodes (HEIC/HEIF since Android 9). GIFs are mostly animations and stickers: left out. */
    val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp")

    /** Folders never worth walking into (hidden ones are skipped too). */
    private val skippedFolders = setOf(
        "node_modules", "bower_components", "venv", "site-packages", "__pycache__", "Pods",
        // Android's and apps' own storage, caches, thumbnails and stickers
        "Android", "LOST.DIR", "cache", "Cache", ".thumbnails", "thumbnails", "Statuses", "Stickers",
        "WhatsApp Stickers", "WhatsApp Animated Gifs",
    )

    /** Keys, certificates and password stores: never read, whatever they're called. */
    private val secretExtensions = setOf("pem", "key", "p12", "pfx", "keystore", "jks", "kdbx", "gpg", "asc", "ovpn")

    /** A document bigger than this is machine-made (a log, a dump): left out. */
    const val MAX_DOC_BYTES = 20L shl 20
    const val MAX_PDF_BYTES = 200L shl 20
    const val MAX_IMAGE_BYTES = 60L shl 20

    /** Pictures smaller than this on their short side are icons, emoji and thumbnails, not photos (DigUp: 256). */
    const val MIN_IMAGE_SIDE = 256

    private val screenshotPrefixes = listOf("screenshot", "screen shot", "screen_", "scr-", "scr_")

    fun skipsFolder(folder: File): Boolean = folder.name.startsWith(".") || folder.name in skippedFolders

    /** A screenshot by where Android puts them or what it calls them (Pictures/Screenshots/Screenshot_2026…png). */
    fun isScreenshot(path: String): Boolean {
        val name = path.substringAfterLast('/').lowercase()
        val folder = path.substringBeforeLast('/', "").substringAfterLast('/').lowercase()
        return folder == "screenshots" || screenshotPrefixes.any { name.startsWith(it) }
    }

    fun classify(file: File): FileKind? {
        if (file.name.startsWith(".")) return null
        val extension = file.extension.lowercase()
        if (extension in secretExtensions) return null
        return when {
            extension == "pdf" && file.length() <= MAX_PDF_BYTES -> FileKind.PDF
            extension in docExtensions && file.length() <= MAX_DOC_BYTES -> FileKind.DOC
            extension in imageExtensions && file.length() <= MAX_IMAGE_BYTES ->
                if (isScreenshot(file.path)) FileKind.SCREENSHOT else FileKind.IMAGE
            else -> null
        }
    }
}
