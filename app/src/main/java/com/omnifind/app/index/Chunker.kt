package com.omnifind.app.index

/**
 * A document's text in overlapping pieces, each one vector. A port of DigUp's `Extractors.chunks` (MIT, Copyright (c)
 * 2026 Abdur Rahim): 1,800 characters, 200 of overlap, cut at a space when one is in the second half, the next piece
 * starting on a word boundary.
 */
object Chunker {
    const val SIZE = 1800
    const val OVERLAP = 200

    private val SPACES = Regex("[ \t ]+")
    private val BLANK_LINES = Regex("\\n\\s*\\n\\s*\\n+")

    fun chunks(text: String, size: Int = SIZE, overlap: Int = OVERLAP, limit: Int = Int.MAX_VALUE): List<String> {
        val clean = text.replace(SPACES, " ").replace(BLANK_LINES, "\n\n").trim()
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < clean.length && chunks.size < limit) {
            var end = minOf(start + size, clean.length)
            if (end < clean.length) {
                val space = lastWhitespace(clean, start, end)
                if (space >= 0 && space - start > size / 2) end = space
            }
            val chunk = clean.substring(start, end).trim()
            if (chunk.isNotEmpty()) chunks += chunk
            if (end >= clean.length) break
            var next = maxOf(end - overlap, start)
            if (next <= start) next = end
            while (next < end && !clean[next].isWhitespace()) next++
            start = if (next < end) next else end
        }
        return chunks
    }

    private fun lastWhitespace(text: String, from: Int, until: Int): Int {
        for (i in until - 1 downTo from) if (text[i].isWhitespace()) return i
        return -1
    }
}
