package com.omnifind.app.index

import java.text.Normalizer

/**
 * Text the way keyword search keeps and matches it, so a word is found however it was typed or extracted. A port of
 * DigUp's `SearchText.swift` (MIT, Copyright (c) 2026 Abdur Rahim).
 */
object SearchText {
    /**
     * NFKC (Arabic presentation forms and ligatures become plain letters, full-width becomes ASCII, Bengali's two
     * spellings of "ো" become one), invisible formatting characters dropped, and Arabic the way people type it: no
     * tatweel, no short-vowel marks, one alef.
     */
    fun normalized(text: String): String {
        val compatible = Normalizer.normalize(text, Normalizer.Form.NFKC)
        val out = StringBuilder(compatible.length)
        var i = 0
        while (i < compatible.length) {
            val code = compatible.codePointAt(i)
            i += Character.charCount(code)
            when (code) {
                0x200B -> out.append(' ')   // zero-width space: a word break (Thai, Khmer)
                0x00AD, 0x061C, in 0x200C..0x200F, in 0x202A..0x202E, in 0x2060..0x2064, in 0x2066..0x2069, 0xFEFF -> {}
                0x0640, in 0x064B..0x065F, 0x0670 -> {}   // tatweel; harakat and the dagger alef
                0x0622, 0x0623, 0x0625, 0x0671 -> out.append('ا')   // alef with hamza or madda, wasla → alef
                else -> out.appendCodePoint(code)
            }
        }
        return out.toString()
    }

    /**
     * True for words of scripts that attach endings (Bengali, Hindi, Arabic): keyword search matches them as prefixes,
     * so "কুমির" finds "কুমিরও" and "مدرس" finds "مدرسة".
     */
    fun inflects(word: String): Boolean = word.codePoints().anyMatch { it in 0x0900..0x0DFF || it in 0x0600..0x06FF }

    /**
     * Whether text says something in words: at least a fifth of what isn't space is letters (marks count, such as
     * Bengali vowel signs). A data dump or a table of numbers isn't, and gets keyword search only.
     */
    fun hasWords(text: String): Boolean {
        var letters = 0
        var others = 0
        text.codePoints().forEach {
            if (Character.isWhitespace(it)) return@forEach
            val type = Character.getType(it)
            val letter = Character.isLetter(it) || type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt()
            if (letter) letters++ else others++
        }
        return letters > 0 && letters * 4 >= others
    }

    /** The start of [text] on one line, at most [limit] characters: what a result shows under its name. */
    fun excerpt(text: String, limit: Int = 240): String {
        val start = text.take(limit * 8)
        val line = start.replace(WHITESPACE, " ").trim()
        return if (line.length > limit || start.length < text.length) line.take(limit) + "…" else line
    }

    private val WHITESPACE = Regex("\\s+")
}
