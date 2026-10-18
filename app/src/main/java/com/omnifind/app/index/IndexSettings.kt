package com.omnifind.app.index

import android.content.Context
import android.os.Environment
import com.omnifind.app.engine.Embedder
import java.io.File

/** What to index and how, kept in SharedPreferences. */
class IndexSettings(private val context: Context) {
    private val prefs = context.getSharedPreferences("omnifind", Context.MODE_PRIVATE)

    /** A folder someone can choose: a volume's top-level folder, or a whole volume ([isWholeVolume]). */
    data class Choice(val volume: Volume, val folder: File) {
        val isWholeVolume: Boolean get() = folder == volume.root
        val path: String get() = folder.path
    }

    /** The choices on each mounted volume: the whole volume, then its top-level folders (hidden and Android/ out). */
    fun choices(): Map<Volume, List<Choice>> = Volumes.list(context).associateWith { volume ->
        val folders = volume.root.listFiles { file -> file.isDirectory && !Rules.skipsFolder(file) }
            ?.sortedBy { it.name.lowercase() } ?: emptyList()
        listOf(Choice(volume, volume.root)) + folders.map { Choice(volume, it) }
    }

    /**
     * Chosen folders, as full paths (a folder on an SD card is /storage/XXXX-XXXX/…). Until someone chooses: the phone's
     * Download and Documents. Folders chosen by name before SD cards were supported are read as the phone's own.
     */
    var folders: Set<String>
        get() = prefs.getStringSet(KEY_PATHS, null)
            ?: (prefs.getStringSet(KEY_OLD_NAMES, null) ?: setOf("Download", "Documents"))
                .map { File(Environment.getExternalStorageDirectory(), it).path }.toSet()
        set(value) = prefs.edit().putStringSet(KEY_PATHS, value).remove(KEY_OLD_NAMES).apply()

    val roots: List<File> get() = folders.map(::File)

    /** CPU threads for embedding (and queries). The engine check's benchmark says which is fastest here. */
    var threads: Int
        get() = prefs.getInt("threads", Embedder.DEFAULT_THREADS)
        set(value) = prefs.edit().putInt("threads", value).apply()

    /**
     * The kinds of file the next reading pass looks for. Until someone chooses: documents and PDFs (what milestone 2
     * indexed). Kinds left out stay in the index as they are; they're just not looked for.
     */
    var kinds: Set<FileKind>
        get() = prefs.getStringSet("kinds", null)?.mapNotNull(FileKind::of)?.toSet() ?: setOf(FileKind.DOC, FileKind.PDF)
        set(value) = prefs.edit().putStringSet("kinds", value.map { it.id }.toSet()).apply()

    /** Vision soft tokens for photos (70 or 140) and screenshots (140 or 280). */
    var photoBudget: Int
        get() = prefs.getInt("photo_budget", Indexer.DEFAULT_PHOTO_BUDGET)
        set(value) = prefs.edit().putInt("photo_budget", value).apply()
    var screenshotBudget: Int
        get() = prefs.getInt("screenshot_budget", Indexer.DEFAULT_SCREENSHOT_BUDGET)
        set(value) = prefs.edit().putInt("screenshot_budget", value).apply()

    val budgets: Map<FileKind, Int> get() = mapOf(FileKind.IMAGE to photoBudget, FileKind.SCREENSHOT to screenshotBudget)

    /** What made the picture vectors; when it changes, only pictures are embedded again. */
    val pictureSource: String get() = "photo$photoBudget shot$screenshotBudget max1344"

    /** Ask before "Index now" when chosen kinds are already indexed (until someone says not to). */
    var cautionBeforeIndexing: Boolean
        get() = prefs.getBoolean("caution_before_indexing", true)
        set(value) = prefs.edit().putBoolean("caution_before_indexing", value).apply()

    /** The last run's summary ("0 new or changed · 57 skipped · 2 embedded") and when it ended, for the index screen. */
    var lastRun: String?
        get() = prefs.getString("last_run", null)
        set(value) = prefs.edit().putString("last_run", value).putLong("last_run_at", System.currentTimeMillis()).apply()
    val lastRunAt: Long? get() = prefs.getLong("last_run_at", 0L).takeIf { it > 0 }

    /** Embed in the background whenever the phone charges. */
    var embedWhileCharging: Boolean
        get() = prefs.getBoolean("embed_while_charging", true)
        set(value) = prefs.edit().putBoolean("embed_while_charging", value).apply()

    private companion object {
        const val KEY_PATHS = "folder_paths"
        const val KEY_OLD_NAMES = "folders"
    }
}
