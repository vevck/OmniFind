package com.omnifind.app

import android.app.Application
import com.omnifind.app.engine.EmbeddingEngine
import com.omnifind.app.engine.ModelFiles
import com.omnifind.app.index.IndexSettings
import com.omnifind.app.index.IndexStore
import com.omnifind.app.index.IndexWorker
import com.omnifind.app.search.Searcher
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.File

/** The process-wide pieces: one index, one searcher, one query encoder. */
class OmniFindApp : Application() {
    val settings by lazy { IndexSettings(this) }
    val store by lazy { IndexStore(File(filesDir, "index.sqlite")) }
    val searcher by lazy { Searcher(store) }

    /**
     * The text model for queries, kept apart from indexing's (which runs in [IndexWorker]), so a search never waits
     * behind a 25-second batch. It's text only: about 0.3 GB while loaded.
     */
    val queryEngine by lazy { EmbeddingEngine { ModelFiles.locate(this) } }

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        IndexWorker.schedule(this, settings.embedWhileCharging)
    }
}
