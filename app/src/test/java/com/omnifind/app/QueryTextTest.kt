package com.omnifind.app

import com.omnifind.app.index.FileKind
import com.omnifind.app.index.SearchText
import com.omnifind.app.search.QueryText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryTextTest {
    @Test
    fun termsDropStopwordsAndKindWords() {
        assertEquals(listOf("clause", "pets", "lease"), QueryText.terms("the clause about pets in the lease PDF"))
    }

    @Test
    fun termsKeepBengaliVowelSigns() {
        assertEquals(listOf("ইলিশ", "মাছ", "রান্না"), QueryText.terms("ইলিশ মাছ রান্না"))
    }

    @Test
    fun arabicIsNormalized() {
        // Alef with hamza becomes a bare alef; harakat go.
        assertEquals("اكل", SearchText.normalized("أَكَل"))
    }

    @Test
    fun patternsQuoteAndPrefix() {
        assertEquals(listOf("\"invoice\"", "\"inv\"*"), QueryText.patterns(listOf("invoice", "inv"), prefixLast = true))
        assertEquals(listOf("\"مدرس\"*"), QueryText.patterns(listOf("مدرس"), prefixLast = false))
    }

    @Test
    fun lookups() {
        assertTrue(QueryText.isLookup(QueryText.terms("K7Q2LM")))
        assertTrue(QueryText.isLookup(QueryText.terms("4815162342")))
        // "INV-2026-0417" splits into inv, 2026, 0417, and "inv" has no digit: by DigUp's rule it isn't a lookup,
        // and its words still find it (SearchTest checks that).
        assertFalse(QueryText.isLookup(QueryText.terms("INV-2026-0417")))
        assertFalse(QueryText.isLookup(QueryText.terms("invoice from march")))
    }

    @Test
    fun kindHints() {
        assertEquals(setOf(FileKind.PDF), QueryText.kindHints("the lease pdf"))
        assertEquals(emptySet<FileKind>(), QueryText.kindHints("the lease"))
    }

    @Test
    fun hasWords() {
        assertTrue(SearchText.hasWords("The tenant may keep one cat."))
        assertFalse(SearchText.hasWords("1,2,3,4,5,6,7,8,9,10,11,12,13,14,15"))
    }
}
