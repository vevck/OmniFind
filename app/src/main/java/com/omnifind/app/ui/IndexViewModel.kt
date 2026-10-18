package com.omnifind.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.omnifind.app.OmniFindApp
import com.omnifind.app.index.FileKind
import com.omnifind.app.index.IndexStore
import com.omnifind.app.index.IndexWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** A type someone can choose to index; Pictures stands for photos and screenshots together. */
enum class IndexType(val label: String, val subtitle: String, val kinds: Set<FileKind>) {
    DOCUMENTS("Documents", "txt, md, html, docx, odt", setOf(FileKind.DOC)),
    PDFS("PDFs", "PDFs with text (scanned ones come later)", setOf(FileKind.PDF)),
    PICTURES("Pictures", "Photos and screenshots", setOf(FileKind.IMAGE, FileKind.SCREENSHOT)),
}

/** The index screen: what's chosen, how far reading and embedding have got, and the buttons to run or stop it. */
class IndexViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as OmniFindApp
    private val settings = app.settings

    var stats by mutableStateOf<IndexStore.Stats?>(null)
        private set
    var running by mutableStateOf<String?>(null)
        private set
    var folders by mutableStateOf(settings.folders)
        private set
    var choices by mutableStateOf(emptyMap<com.omnifind.app.index.Volume, List<com.omnifind.app.index.IndexSettings.Choice>>())
        private set
    var missing by mutableStateOf(emptyList<String>())
        private set
    var allowed by mutableStateOf(hasPermission())
        private set
    var threads by mutableStateOf(settings.threads)
        private set
    var embedWhileCharging by mutableStateOf(settings.embedWhileCharging)
        private set
    var kinds by mutableStateOf(settings.kinds)
        private set
    var photoBudget by mutableStateOf(settings.photoBudget)
        private set
    var screenshotBudget by mutableStateOf(settings.screenshotBudget)
        private set
    var lastRun by mutableStateOf(settings.lastRun)
        private set
    var lastRunAt by mutableStateOf(settings.lastRunAt)
        private set
    /** Shown before indexing when chosen types are already indexed: what's there, and that it will be skipped. */
    var caution by mutableStateOf<String?>(null)
        private set

    init {
        val work = WorkManager.getInstance(app)
        viewModelScope.launch {
            combine(
                work.getWorkInfosForUniqueWorkFlow(IndexWorker.NOW),
                work.getWorkInfosForUniqueWorkFlow(IndexWorker.PERIODIC),
            ) { now, periodic -> (now + periodic).firstOrNull { it.state == WorkInfo.State.RUNNING } }
                .collect { info -> running = info?.let(::describe) }
        }
        viewModelScope.launch {
            while (true) {
                stats = withContext(Dispatchers.IO) { app.store.stats() }
                lastRun = settings.lastRun
                lastRunAt = settings.lastRunAt
                delay(2000)
            }
        }
        refresh()
    }

    private fun describe(info: WorkInfo): String {
        val phase = info.progress.getString(IndexWorker.KEY_PHASE) ?: "Starting"
        val done = info.progress.getInt(IndexWorker.KEY_DONE, 0)
        val total = info.progress.getInt(IndexWorker.KEY_TOTAL, 0)
        return if (total > 0) "$phase: $done of $total" else "$phase…"
    }

    fun refresh() {
        allowed = hasPermission()
        choices = if (allowed) settings.choices() else emptyMap()
        missing = folders.filter { !File(it).isDirectory }.sorted()
    }

    private fun hasPermission() = com.omnifind.app.index.FileAccess.granted(app)

    fun isChosen(type: IndexType) = type.kinds.all { it in kinds }

    fun toggle(type: IndexType) {
        kinds = if (isChosen(type)) kinds - type.kinds else kinds + type.kinds
        settings.kinds = kinds
    }

    fun toggleFolder(path: String) {
        folders = if (path in folders) folders - path else folders + path
        settings.folders = folders
    }

    fun chooseThreads(count: Int) {
        threads = count
        settings.threads = count
    }

    fun changeEmbedWhileCharging(enabled: Boolean) {
        embedWhileCharging = enabled
        settings.embedWhileCharging = enabled
        IndexWorker.schedule(app, enabled)
    }

    /** Changing a budget makes those pictures embed again on the next run (text is never affected). */
    fun choosePhotoBudget(budget: Int) {
        photoBudget = budget
        settings.photoBudget = budget
    }

    fun chooseScreenshotBudget(budget: Int) {
        screenshotBudget = budget
        settings.screenshotBudget = budget
    }

    /** The chosen types that already have files in the index, with how many. */
    fun alreadyIndexed(): List<Pair<IndexType, Int>> = IndexType.entries.filter(::isChosen).mapNotNull { type ->
        type.kinds.sumOf { stats?.byKind?.get(it) ?: 0 }.takeIf { it > 0 }?.let { type to it }
    }

    /** Runs the reading pass, after a word of caution when chosen types are already indexed. */
    fun indexNow(embedAnyway: Boolean = false) {
        val indexed = alreadyIndexed()
        if (indexed.isNotEmpty() && settings.cautionBeforeIndexing) {
            pendingEmbedAnyway = embedAnyway
            caution = indexed.joinToString(", ") { (type, count) -> "$count ${type.label.lowercase()}" }
            return
        }
        IndexWorker.runNow(app, embedAnyway)
    }
    private var pendingEmbedAnyway = false

    fun confirmIndexing(dontAskAgain: Boolean) {
        if (dontAskAgain) settings.cautionBeforeIndexing = false
        caution = null
        IndexWorker.runNow(app, pendingEmbedAnyway)
    }

    fun cancelIndexing() {
        caution = null
    }

    fun embedNow() = indexNow(embedAnyway = true)
    fun stop() = IndexWorker.stop(app)
}
