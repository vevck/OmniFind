package com.omnifind.app

import com.omnifind.app.index.FileKind
import com.omnifind.app.index.Rules
import com.omnifind.app.search.QueryText
import com.omnifind.app.search.Searcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PicturesRulesTest {
    @Test
    fun screenshotsByFolderOrName() {
        assertTrue(Rules.isScreenshot("/storage/emulated/0/Pictures/Screenshots/Screenshot_20261018-101500.png"))
        assertTrue(Rules.isScreenshot("/storage/emulated/0/DCIM/Screenshots/IMG_0001.png"))
        assertTrue(Rules.isScreenshot("/storage/1A2B-3C4D/Backup/Screen Shot 2026-10-18.png"))
        assertFalse(Rules.isScreenshot("/storage/emulated/0/DCIM/Camera/IMG_20261018_101500.jpg"))
    }

    @Test
    fun kindWordsBoostPictures() {
        assertEquals(setOf(FileKind.SCREENSHOT), QueryText.kindHints("screenshot of a payment error"))
        assertEquals(setOf(FileKind.IMAGE), QueryText.kindHints("photo of the beach"))
        // Kind words describe what to look for, not what's in it: they're not searched as words.
        assertEquals(listOf("payment", "error"), QueryText.terms("screenshot of a payment error"))
    }

    @Test
    fun picturesAreScoredAgainstTheirOwnAverage() {
        // 25 text vectors around 0.60 and 25 picture vectors around 0.45 (pictures score lower for any query): the best
        // picture (0.62) stands out among pictures as much as the best note (0.77) does among notes.
        val text = FloatArray(25) { 0.60f + (it % 5 - 2) * 0.01f }.also { it[0] = 0.77f }
        val pictures = FloatArray(25) { 0.45f + (it % 5 - 2) * 0.01f }.also { it[0] = 0.62f }
        val z = Searcher.meaning(text + pictures, BooleanArray(50) { it >= 25 })
        assertTrue("best note z=${z[0]}", z[0] > 2)
        assertTrue("best picture z=${z[25]}", z[25] > 2)
        assertEquals(z[0], z[25], 0.3)
    }

    @Test
    fun fewPicturesUseTheOverallAverage() {
        // Under 20 picture vectors, their own average isn't trusted: everything is measured from the overall one.
        val scores = FloatArray(30) { 0.6f } + floatArrayOf(0.5f, 0.5f)
        val z = Searcher.meaning(scores, BooleanArray(32) { it >= 30 })
        assertTrue(z[31] < 0)
    }
}
