package com.omnifind.app.ui

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.omnifind.app.OmniFindApp
import com.omnifind.app.engine.EmbeddingEngine
import com.omnifind.app.engine.ModelFiles
import com.omnifind.app.engine.Prompts
import com.omnifind.app.index.FileKind
import com.omnifind.app.search.SearchHit
import com.omnifind.app.search.Searcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Search as you type: words first (instant), then meaning once the query's vector is made (~0.4 s on the Mi A1).
 * The query encoder loads on the first search and is let go a minute after the last.
 */
class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as OmniFindApp

    var query by mutableStateOf("")
        private set
    var hits by mutableStateOf<List<SearchHit>>(emptyList())
        private set
    var strong by mutableStateOf(0)
        private set
    /** Words-only results are showing; meaning results are on their way. */
    var thinking by mutableStateOf(false)
        private set
    var status by mutableStateOf<String?>(null)
        private set

    /** What to show: everything, text files only, or pictures only (as a grid). */
    enum class Filter(val label: String, val kinds: Set<FileKind>?) {
        ALL("All", null),
        DOCUMENTS("Documents", setOf(FileKind.DOC, FileKind.PDF)),
        PICTURES("Pictures", setOf(FileKind.IMAGE, FileKind.SCREENSHOT)),
    }

    var filter by mutableStateOf(Filter.ALL)
        private set

    fun choose(filter: Filter) {
        this.filter = filter
        type(query)
    }

    private var job: Job? = null
    private var release: Job? = null

    fun type(text: String) {
        query = text
        job?.cancel()
        if (text.isBlank()) {
            hits = emptyList()
            strong = 0
            thinking = false
            return
        }
        job = viewModelScope.launch {
            delay(150)   // let a burst of typing settle
            val kinds = filter.kinds
            show(withContext(Dispatchers.IO) { app.searcher.search(text, null, prefixLast = true, kinds = kinds) })
            if (ModelFiles.locate(app) == null) {
                status = "No model on the phone yet: searching by words only"
                return@launch
            }
            delay(250)
            thinking = true
            try {
                val settings = EmbeddingEngine.Settings(textOnly = true, threads = app.settings.threads)
                val vector = app.queryEngine.use(settings) { it.embedTexts(listOf(Prompts.query(text))).single() }
                show(withContext(Dispatchers.IO) { app.searcher.search(text, vector, prefixLast = true, kinds = kinds) })
                status = null
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                Log.w("OmniFind/search", "meaning search failed", error)
                status = "Searching by words only (${error.message})"
            } finally {
                thinking = false
            }
            scheduleRelease()
        }
    }

    private fun show(results: List<SearchHit>) {
        hits = results
        strong = Searcher.strongCount(results)
    }

    private fun scheduleRelease() {
        release?.cancel()
        release = viewModelScope.launch {
            delay(60_000)
            app.queryEngine.release()
        }
    }
}
