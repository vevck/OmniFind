package com.omnifind.app.search

import com.omnifind.app.index.FileKind
import com.omnifind.app.index.SearchText

/** A query's words, as DigUp's `Searcher` reads them (MIT, Copyright (c) 2026 Abdur Rahim). */
object QueryText {
    /** Words that describe what to look for, not what's in it. */
    val stopwords = setOf(
        "a", "an", "the", "of", "in", "on", "at", "to", "for", "with", "and", "or", "my", "me", "that", "this",
        "these", "those", "from", "about", "where", "what", "which", "who", "when", "is", "was", "are", "were", "be",
        "it", "its", "some", "any", "find", "show", "file", "files", "photo", "photos", "picture", "image", "images",
        "screenshot", "screenshots", "video", "videos", "pdf", "document", "documents", "recording",
        // Bengali and Arabic (normalized, as `terms` sees them)
        "এবং", "ও", "এই", "সেই", "একটি", "আমার", "থেকে", "জন্য", "সাথে", "মধ্যে", "কোথায়", "কোন", "কী", "কি", "যে",
        "ছবি", "স্ক্রিনশট", "ভিডিও", "ফাইল", "পিডিএফ",
        "في", "من", "على", "الى", "عن", "مع", "هذا", "هذه", "ذلك", "التي", "الذي", "اين", "ما",
        "صورة", "صور", "لقطة", "شاشة", "فيديو", "ملف", "مستند",
    )

    /** Letters, digits and marks (Bengali vowel signs) belong to words; anything else separates them. */
    private fun isWordCodePoint(code: Int): Boolean {
        if (Character.isLetterOrDigit(code)) return true
        val type = Character.getType(code)
        return type == Character.NON_SPACING_MARK.toInt() || type == Character.COMBINING_SPACING_MARK.toInt()
    }

    private fun words(text: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        SearchText.normalized(text).lowercase().codePoints().forEach { code ->
            if (isWordCodePoint(code)) current.appendCodePoint(code)
            else if (current.isNotEmpty()) { out += current.toString(); current.clear() }
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }

    /** The content words of a query, normalized as keyword search keeps its text, stopwords dropped, each once. */
    fun terms(text: String): List<String> {
        val seen = HashSet<String>()
        return words(text).filter { it.codePointCount(0, it.length) >= 2 && it !in stopwords && seen.add(it) }
    }

    /**
     * FTS5 patterns: each word quoted, as a prefix for the last word while it's being typed and for words of languages
     * that attach endings.
     */
    fun patterns(terms: List<String>, prefixLast: Boolean): List<String> = terms.mapIndexed { index, term ->
        val prefix = (prefixLast && index == terms.lastIndex) || SearchText.inflects(term)
        "\"${term.replace("\"", "\"\"")}\"" + if (prefix) "*" else ""
    }

    /** Codes and IDs ("K7Q2LM") and long numbers mean nothing to an embedding but everything to keyword search. */
    fun isLookup(terms: List<String>): Boolean = terms.isNotEmpty() && terms.all { term ->
        term.any { it.isDigit() } && (term.any { it.isLetter() } || term.length >= 4)
    }

    private val kindWords = mapOf(
        "pdf" to FileKind.PDF, "pdfs" to FileKind.PDF, "paper" to FileKind.PDF, "papers" to FileKind.PDF,
        "document" to FileKind.DOC, "documents" to FileKind.DOC, "note" to FileKind.DOC, "notes" to FileKind.DOC,
        "screenshot" to FileKind.SCREENSHOT, "screenshots" to FileKind.SCREENSHOT,
        "photo" to FileKind.IMAGE, "photos" to FileKind.IMAGE, "picture" to FileKind.IMAGE,
        "pictures" to FileKind.IMAGE, "image" to FileKind.IMAGE, "images" to FileKind.IMAGE,
        "pic" to FileKind.IMAGE, "pics" to FileKind.IMAGE, "selfie" to FileKind.IMAGE,
        "পিডিএফ" to FileKind.PDF, "নোট" to FileKind.DOC, "স্ক্রিনশট" to FileKind.SCREENSHOT, "ছবি" to FileKind.IMAGE,
        "مستند" to FileKind.DOC, "ملاحظات" to FileKind.DOC, "لقطة" to FileKind.SCREENSHOT, "صورة" to FileKind.IMAGE,
        "صور" to FileKind.IMAGE,
    )

    /** Kinds the query asks for ("…in a pdf"): a soft boost, not a filter. */
    fun kindHints(text: String): Set<FileKind> = words(text).mapNotNull { word ->
        kindWords[word] ?: kindWords.entries.firstOrNull { (key, _) ->
            SearchText.inflects(key) && key.length >= 3 && word.startsWith(key)
        }?.value
    }.toSet()
}
