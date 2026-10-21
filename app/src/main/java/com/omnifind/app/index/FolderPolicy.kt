package com.omnifind.app.index

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * Which folders OmniFind may read, applied everywhere a folder is chosen, saved or walked (the picker, the settings,
 * the crawl), following Android's storage security guidance:
 *
 * - **Least access, read only.** OmniFind reads only the folders someone ticks, never writes, moves or deletes.
 * - **Other apps' private storage is off limits.** `Android/data`, `Android/obb` and `Android/media` belong to other
 *   apps (Android hides them from apps since 11; on 9 and 10 OmniFind keeps out by rule), and so do system and
 *   recovery folders (`LOST.DIR`, trash, `.android_secure`, a PC's `System Volume Information`).
 * - **No escaping a chosen folder.** Symbolic links are never followed, and a path counts only if its real (canonical)
 *   location is inside an allowed volume and outside every protected folder: `Download/../Android/data` or a link to
 *   another app's files is refused like the folder itself.
 * - **Nothing hidden.** Folders starting with "." (apps' caches, `.thumbnails`, `.Trash`) stay out.
 */
object FolderPolicy {
    /**
     * Top-level folders of a volume that are the system's or other apps' (compared ignoring case: an SD card
     * formatted on a PC uses its own spellings).
     */
    val protectedTopLevel: Set<String> = setOf(
        "android", "lost.dir", ".android_secure", "system volume information", "\$recycle.bin", ".trashes",
        ".trash", ".spotlight-v100", ".fseventsd",
    )

    /** Shown to people in the folder picker: what's protected, in plain words. */
    const val EXPLANATION = "System folders (Android, LOST.DIR, hidden folders) are protected: OmniFind never reads them " +
        "or other apps' private files."

    /** The volume roots folders must live under: /storage/emulated/<user>, /storage/XXXX-XXXX, or /sdcard. */
    private val VOLUME_ROOT = Regex("^/storage/(emulated/\\d+|[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4})(/|$)|^/sdcard(/|$)|^/mnt/sdcard(/|$)")

    /** Whether [folder]'s own name marks it as the system's or hidden (for one level of a listing). */
    fun isProtectedName(folder: File): Boolean {
        val name = folder.name
        return name.startsWith(".") || name.lowercase() in protectedTopLevel
    }

    fun isSymlink(file: File): Boolean =
        runCatching { Files.isSymbolicLink(file.toPath()) }.getOrDefault(true)   // can't tell: treat as a link

    /**
     * Whether OmniFind may read [path] (a folder someone chose, or one found while walking): on a storage volume,
     * really there (not through a link), and with no protected folder anywhere on its real path.
     */
    fun allows(path: String): Boolean {
        if (!VOLUME_ROOT.containsMatchIn(path)) return false
        val file = File(path)
        val real = try {
            file.canonicalPath
        } catch (_: IOException) {
            return false
        }
        // The real location must itself be on a volume (a link from Download to /data is refused).
        if (!VOLUME_ROOT.containsMatchIn(real)) return false
        val inside = Volumes.relative(real).split('/').filter { it.isNotEmpty() }
        val hidden = inside.any { it.startsWith(".") }                              // hidden at any depth
        val system = inside.firstOrNull()?.lowercase() in protectedTopLevel         // Android/, LOST.DIR, …
        return !hidden && !system
    }

    /** The chosen folders that may be read; anything else (a saved path that's now protected, or tampered) is dropped. */
    fun sanitize(paths: Collection<String>): List<File> = paths.filter(::allows).map(::File)

    /** Whether the walk may go into [dir] under [root]: not protected, not a link, really inside [root]. */
    fun mayEnter(dir: File, root: File): Boolean {
        if (dir == root) return true
        if (isProtectedName(dir) || Rules.skipsFolder(dir) || isSymlink(dir)) return false
        return try {
            dir.canonicalPath.startsWith(root.canonicalPath.trimEnd('/') + "/")
        } catch (_: IOException) {
            false
        }
    }

    /** Whether a file found while walking may be read: a regular file, not a link. */
    fun mayRead(file: File): Boolean =
        runCatching { Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) }.getOrDefault(false)
}
