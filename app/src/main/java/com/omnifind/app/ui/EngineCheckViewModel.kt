package com.omnifind.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.omnifind.app.engine.Embedder
import com.omnifind.app.engine.EmbeddingEngine
import com.omnifind.app.engine.ModelDownloadWorker
import com.omnifind.app.engine.ModelFiles
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.omnifind.app.engine.ModelPaths
import com.omnifind.app.engine.Prompts
import com.omnifind.app.engine.cosine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Milestone 1's screen: is the model on the phone, does a search rank the right sentence first, and how fast is it
 * here. The numbers it prints are what later milestones size their work by.
 */
class EngineCheckViewModel(application: Application) : AndroidViewModel(application) {
    private val context get() = getApplication<Application>()
    private val engine = EmbeddingEngine { ModelFiles.locate(context) }

    var models by mutableStateOf<ModelPaths?>(null)
        private set
    var busy by mutableStateOf<String?>(null)
        private set
    var log by mutableStateOf("")
        private set
    var results by mutableStateOf<List<Pair<String, Float>>>(emptyList())
        private set
    var threads by mutableStateOf(Embedder.DEFAULT_THREADS)
    /** The model download, while it runs or waits for Wi-Fi: what it's doing, and how far it is (0..1, or null). */
    var download by mutableStateOf<String?>(null)
        private set
    var downloadFraction by mutableStateOf<Float?>(null)
        private set

    val folder: File get() = ModelFiles.folder(context)

    /** Sentences to rank: a search should put the matching one first, across languages too. */
    val samples = listOf(
        "Flight to Tokyo Haneda on November 3, then the train to Kamakura to see the Great Buddha.",
        "Your card payment was declined. Please try another payment method.",
        "The library closes at 8 pm on weekdays and 5 pm on weekends.",
        "Preheat the oven to 200 °C and roast the vegetables for 25 minutes.",
        "The tenant may keep one cat or one small dog with the landlord's written consent.",
        "Meeting moved to room 4 because the projector in the main hall failed again.",
        "ইলিশ মাছ সরষে দিয়ে রান্না করুন, দশ মিনিট ভাপে রাখুন।",
        "تفتح المكتبة في التاسعة صباحاً وتغلق في الثامنة مساءً.",
        "Invoice INV-2026-0417 is due on October 31.",
        "Our dog loves running along the beach at sunset.",
    )

    init {
        refresh()
        log = "llama.cpp: ${Embedder.systemInfo()}\n"
        viewModelScope.launch {
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.WORK).collect { infos ->
                val info = infos.lastOrNull()
                when (info?.state) {
                    WorkInfo.State.RUNNING -> {
                        val done = info.progress.getLong(ModelDownloadWorker.KEY_DONE, 0)
                        val total = info.progress.getLong(ModelDownloadWorker.KEY_TOTAL, 0)
                        val phase = info.progress.getString(ModelDownloadWorker.KEY_PHASE) ?: "Starting"
                        downloadFraction = if (total > 0) done.toFloat() / total else null
                        download = if (total > 0 && phase == "Downloading") "Downloading: ${done shr 20} of ${total shr 20} MB" else "$phase…"
                    }
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                        download = info.progress.getString(ModelDownloadWorker.KEY_ERROR)?.let { "Paused: $it. Retrying soon" }
                            ?: "Waiting for Wi-Fi"
                        downloadFraction = null
                    }
                    WorkInfo.State.FAILED -> {
                        download = null
                        say("download failed: ${info.outputData.getString(ModelDownloadWorker.KEY_ERROR)}")
                    }
                    else -> download = null
                }
                refresh()
            }
        }
    }

    fun startDownload() = ModelDownloadWorker.start(context)
    fun stopDownload() = ModelDownloadWorker.stop(context)

    fun refresh() {
        models = ModelFiles.locate(context)
    }

    fun verifyChecksums() = work("Checking the model files…") {
        for (file in ModelFiles.all) {
            val path = File(folder, file.name)
            if (!path.exists()) {
                say("${file.name}: missing")
                continue
            }
            val started = SystemClock.elapsedRealtime()
            val sum = withContext(Dispatchers.IO) { ModelFiles.sha256(path) }
            val verdict = if (sum == file.sha256) "ok" else "WRONG checksum (download it again)"
            say("${file.name}: $verdict (${seconds(started)})")
        }
    }

    fun search(query: String) = work("Searching…") {
        if (query.isBlank()) return@work
        val settings = EmbeddingEngine.Settings(textOnly = true, threads = threads)
        val documents = engine.use(settings) { it.embedTexts(samples.map { s -> Prompts.document(null, s) }) }
        val started = SystemClock.elapsedRealtime()
        val vector = engine.use(settings) { it.embedTexts(listOf(Prompts.query(query))).single() }
        say("query \"$query\" embedded in ${seconds(started)}")
        results = samples.zip(documents.map { cosine(vector, it) }).sortedByDescending { it.second }
    }

    /** Times what indexing will spend its hours on: passages, and pictures at two budgets. */
    fun benchmark() = work("Benchmarking with $threads threads…") {
        val picture = withContext(Dispatchers.IO) { testPicture() }
        val settings = EmbeddingEngine.Settings(textOnly = false, threads = threads)
        val loadStarted = SystemClock.elapsedRealtime()
        val load = engine.use(settings) { it.loadSeconds }
        say("threads $threads: full model loaded in ${"%.1f s".format(Locale.ROOT, load)} (wall ${seconds(loadStarted)})")
        val passage = "The review moved to room 4 because the projector in the main hall failed again, " +
            "and the team agreed to send the slides the night before. "
        val passages = List(16) { Prompts.document("Notes $it", passage.repeat(8)) }   // ~1,400 characters each
        var started = SystemClock.elapsedRealtime()
        engine.use(settings) { it.embedTexts(passages) }
        say("  16 passages of ~1,400 characters: ${seconds(started)} (${perItem(started, 16)} each)")
        for (budget in listOf(70, 140)) {
            started = SystemClock.elapsedRealtime()
            engine.use(settings) { it.embedImage(picture, budget) }
            say("  1 picture at budget $budget: ${seconds(started)}")
        }
        engine.release()
    }

    private fun testPicture(): File {
        val file = File(context.cacheDir, "benchmark.jpg")
        if (file.exists()) return file
        val bitmap = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, 1600f, 1200f, Color.rgb(30, 90, 160), Color.rgb(240, 200, 90),
                Shader.TileMode.CLAMP)
        }
        Canvas(bitmap).drawRect(0f, 0f, 1600f, 1200f, paint)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        return file
    }

    private fun work(label: String, block: suspend () -> Unit) {
        if (busy != null) return
        busy = label
        viewModelScope.launch {
            try {
                block()
            } catch (error: Throwable) {
                say("error: ${error.message}")
            } finally {
                busy = null
            }
        }
    }

    private fun say(line: String) {
        log += line + "\n"
    }

    private fun seconds(since: Long) =
        "%.2f s".format(Locale.ROOT, (SystemClock.elapsedRealtime() - since) / 1000.0)

    private fun perItem(since: Long, count: Int) =
        "%.2f s".format(Locale.ROOT, (SystemClock.elapsedRealtime() - since) / 1000.0 / count)

    override fun onCleared() {
        engine.close()
    }
}
