package com.omnifind.app

import com.omnifind.app.index.Volumes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumesTest {
    @Test
    fun relativeTakesOffTheVolume() {
        assertEquals("Download/a.pdf", Volumes.relative("/storage/emulated/0/Download/a.pdf"))
        assertEquals("Music/notes.txt", Volumes.relative("/storage/1A2B-3C4D/Music/notes.txt"))
        assertEquals("Documents", Volumes.relative("/sdcard/Documents"))
        assertEquals("", Volumes.relative("/storage/1A2B-3C4D"))
    }

    @Test
    fun cardsAreTheOtherVolumes() {
        assertTrue(Volumes.onCard("/storage/1A2B-3C4D/Documents/lease.pdf"))
        assertFalse(Volumes.onCard("/storage/emulated/0/Documents/lease.pdf"))
        assertFalse(Volumes.onCard("/sdcard/Documents/lease.pdf"))
    }
}
