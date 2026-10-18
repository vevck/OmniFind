package com.omnifind.app

import com.omnifind.app.index.Chunker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkerTest {
    @Test
    fun shortTextIsOneChunk() {
        assertEquals(listOf("Hello there."), Chunker.chunks("  Hello   there.  "))
    }

    @Test
    fun longTextOverlapsAndCutsAtSpaces() {
        val words = (1..1000).joinToString(" ") { "word$it" }
        val chunks = Chunker.chunks(words)
        assertTrue(chunks.size > 1)
        for (chunk in chunks) {
            assertTrue(chunk.length <= Chunker.SIZE)
            assertTrue("cut inside a word: ${chunk.takeLast(12)}", chunk.endsWith(chunk.split(' ').last()))
            assertTrue(chunk.startsWith("word"))
        }
        // Each chunk starts inside the one before (the 200-character overlap).
        for (i in 1 until chunks.size) {
            val first = chunks[i].substringBefore(' ')
            assertTrue("chunk $i doesn't overlap", chunks[i - 1].contains(first))
        }
    }

    @Test
    fun blankLinesCollapse() {
        assertEquals(listOf("a\n\nb"), Chunker.chunks("a\n\n\n\n\nb"))
    }

    @Test
    fun limitStops() {
        assertEquals(2, Chunker.chunks("x ".repeat(10_000), limit = 2).size)
    }
}
