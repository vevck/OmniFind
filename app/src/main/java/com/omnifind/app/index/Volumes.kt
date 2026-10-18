package com.omnifind.app.index

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * The storage a phone has: its own shared storage (/storage/emulated/0) and any SD card mounted as portable storage
 * (/storage/XXXX-XXXX). On Android 9, READ_EXTERNAL_STORAGE lets OmniFind read both. (A card formatted as internal
 * storage is merged into the phone's own and needs nothing here.)
 */
data class Volume(val root: File, val label: String, val removable: Boolean)

object Volumes {
    /**
     * The mounted volumes, the phone's own first. Found through the app's folder on each one (Android creates
     * `<volume>/Android/data/<package>/files` per volume), which works on every Android since 4.4.
     */
    fun list(context: Context): List<Volume> {
        val roots = context.getExternalFilesDirs(null).filterNotNull().mapNotNull { dir ->
            val path = dir.path
            val cut = path.indexOf("/Android/data/")
            if (cut <= 0) return@mapNotNull null
            File(path.substring(0, cut)).takeIf {
                Environment.getExternalStorageState(dir) == Environment.MEDIA_MOUNTED && it.isDirectory
            }
        }.distinctBy { it.path }
        var cards = 0
        val removable = roots.count { isRemovable(it) }
        return roots.map { root ->
            if (isRemovable(root)) {
                cards++
                Volume(root, if (removable > 1) "SD card $cards" else "SD card", removable = true)
            } else {
                Volume(root, "Phone storage", removable = false)
            }
        }.sortedBy { it.removable }
    }

    private fun isRemovable(root: File): Boolean =
        runCatching { Environment.isExternalStorageRemovable(root) }.getOrDefault(root.path != primaryPath)

    private val primaryPath: String get() = Environment.getExternalStorageDirectory().path

    /** A volume's root in a path: /storage/emulated/0, /sdcard, or /storage/XXXX-XXXX. */
    private val VOLUME_ROOT = Regex("^(/storage/emulated/\\d+|/sdcard|/mnt/sdcard|/storage/[^/]+)")

    /** [path] with its volume's root taken off ("Download/Invoices/a.pdf"). */
    fun relative(path: String): String = path.replaceFirst(VOLUME_ROOT, "").trimStart('/')

    /** Whether [path] is on a removable volume, judged from its path alone (no disk access). */
    fun onCard(path: String): Boolean =
        path.startsWith("/storage/") && !path.startsWith("/storage/emulated/") && !path.startsWith("/storage/self/")
}
