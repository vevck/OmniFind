package com.omnifind.app.engine

import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * Runs every [Embedder] call on one background thread, the only way the embedder may be used, and keeps at most one
 * loaded. Ask for text-only when searching and full when reading pictures or sound.
 */
class EmbeddingEngine(private val models: () -> ModelPaths?) : AutoCloseable {
    private val thread: ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor { Thread(it, "omnifind-embedder") }.asCoroutineDispatcher()
    private var embedder: Embedder? = null
    private var loaded: Settings? = null

    data class Settings(val textOnly: Boolean, val threads: Int = Embedder.DEFAULT_THREADS)

    /** Runs [work] with an embedder loaded for [settings] (loading or swapping it first). */
    suspend fun <T> use(settings: Settings, work: (Embedder) -> T): T = withContext(thread) {
        val current = embedder
        val ready = if (current != null && loaded == settings) current else {
            current?.close()
            embedder = null
            val paths = models() ?: throw EmbedderException("the model isn't on the phone yet")
            Embedder.open(paths, settings.textOnly, settings.threads).also {
                embedder = it
                loaded = settings
            }
        }
        work(ready)
    }

    /** Frees the model's memory (the next [use] loads it again). */
    suspend fun release() = withContext(thread) {
        embedder?.close()
        embedder = null
        loaded = null
    }

    override fun close() {
        thread.executor.execute { embedder?.close(); embedder = null }
        thread.close()
    }
}
