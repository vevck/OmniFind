package com.omnifind.app

import com.omnifind.app.index.FolderPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

/** TC-06: the folders OmniFind may read, and the ones it must never (system, other apps', hidden, through links). */
class FolderPolicyTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun userFoldersOnEveryVolumeAreAllowed() {
        assertTrue(FolderPolicy.allows("/storage/emulated/0/Download"))
        assertTrue(FolderPolicy.allows("/storage/emulated/0/DCIM/Camera"))
        assertTrue(FolderPolicy.allows("/storage/1A2B-3C4D/Notes"))
        assertTrue(FolderPolicy.allows("/storage/emulated/0"))          // "Everything on the phone storage"
    }

    @Test
    fun systemAndOtherAppsFoldersAreRefused() {
        assertFalse(FolderPolicy.allows("/storage/emulated/0/Android"))
        assertFalse(FolderPolicy.allows("/storage/emulated/0/Android/data/com.example.bank"))
        assertFalse(FolderPolicy.allows("/storage/emulated/0/android/obb"))           // any case
        assertFalse(FolderPolicy.allows("/storage/1A2B-3C4D/LOST.DIR"))
        assertFalse(FolderPolicy.allows("/storage/1A2B-3C4D/System Volume Information"))
        assertFalse(FolderPolicy.allows("/storage/emulated/0/.thumbnails"))
        assertFalse(FolderPolicy.allows("/storage/emulated/0/DCIM/.trashed"))           // hidden at any depth
    }

    @Test
    fun pathsOutsideStorageAndTraversalAreRefused() {
        assertFalse(FolderPolicy.allows("/data/data/com.omnifind.app"))
        assertFalse(FolderPolicy.allows("/system/etc"))
        assertFalse(FolderPolicy.allows("/storage/emulated/0/Download/../Android/data"))
        assertFalse(FolderPolicy.allows("/storage/emulated/0/../../data"))
        assertFalse(FolderPolicy.allows("relative/Download"))
    }

    @Test
    fun sanitizeDropsAnythingNotAllowed() {
        val kept = FolderPolicy.sanitize(listOf("/storage/emulated/0/Download", "/storage/emulated/0/Android/data", "/data"))
        assertEquals(listOf("/storage/emulated/0/Download"), kept.map { it.path })
    }

    @Test
    fun theWalkNeverFollowsLinksOrLeavesTheFolder() {
        val root = temp.newFolder("Download")
        val inside = java.io.File(root, "Invoices").apply { mkdirs() }
        val elsewhere = temp.newFolder("private")
        val link = java.io.File(root, "shortcut")
        Files.createSymbolicLink(link.toPath(), elsewhere.toPath())
        assertTrue(FolderPolicy.mayEnter(root, root))
        assertTrue(FolderPolicy.mayEnter(inside, root))
        assertFalse(FolderPolicy.mayEnter(link, root))                                   // a link: never followed
        assertFalse(FolderPolicy.mayEnter(java.io.File(root, ".cache").apply { mkdirs() }, root))
        assertFalse(FolderPolicy.mayEnter(elsewhere, root))                               // outside the chosen folder

        val file = java.io.File(inside, "a.txt").apply { writeText("x") }
        val fileLink = java.io.File(root, "b.txt")
        Files.createSymbolicLink(fileLink.toPath(), file.toPath())
        assertTrue(FolderPolicy.mayRead(file))
        assertFalse(FolderPolicy.mayRead(fileLink))
    }
}
