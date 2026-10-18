package com.omnifind.app.engine

import com.omnifind.app.index.ImageEmbedder
import com.omnifind.app.index.TextEmbedder
import java.io.File

/** A model that didn't load, or an input that got no vector (the JNI bridge throws it). */
class EmbedderException(message: String) : RuntimeException(message)

/**
 * EmbeddingGemma 2 on llama.cpp (CPU), in this process: `app/src/main/cpp/embedder.cpp`, a port of DigUp's
 * `LlamaEmbedder.swift`. Vectors are 768 floats, L2-normalized, so a dot product is the cosine.
 *
 * Not thread-safe: use it from one thread at a time ([EmbeddingEngine] does that). [close] frees it: about 1 GB with
 * the image and audio encoders, about 0.3 GB text-only.
 */
class Embedder private constructor(private var handle: Long) : AutoCloseable, TextEmbedder, ImageEmbedder {

    val dimension: Int get() = nativeDimension(checkedHandle())

    /** Seconds the load took, warm-up included. */
    val loadSeconds: Double get() = nativeLoadSeconds(checkedHandle())

    /** One vector per text, in order. Texts carry their task prompt already ([Prompts]). */
    override fun embedTexts(texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()
        val flat = nativeEmbedTexts(checkedHandle(), Array(texts.size) { texts[it].toByteArray(Charsets.UTF_8) })
        val size = flat.size / texts.size
        return List(texts.size) { flat.copyOfRange(it * size, (it + 1) * size) }
    }

    /** An image file (JPEG, PNG, BMP, GIF) at [budget] vision soft tokens: 70, 140, 280, 560 or 1120. */
    override fun embedImage(file: File, budget: Int): FloatArray = nativeEmbedImage(checkedHandle(), file.path, budget)

    /** The first 30 s of an audio file (WAV, MP3, FLAC). */
    fun embedAudio(file: File): FloatArray = nativeEmbedAudio(checkedHandle(), file.path)

    override fun close() {
        if (handle != 0L) nativeFree(handle)
        handle = 0L
    }

    private fun checkedHandle(): Long = handle.also { check(it != 0L) { "the embedder is closed" } }

    companion object {
        init {
            System.loadLibrary("omnifind")
        }

        /** The Mi A1 has 8 Cortex-A53 cores; 4 keeps the phone responsive. Measure 4, 6 and 8 with the engine check. */
        const val DEFAULT_THREADS = 4

        /**
         * [textOnly]: just the text model, for queries. [maxTokens] 0 picks 512 text-only and 2048 otherwise
         * (DigUp's sizes; 2048 is the reference's cut, so long texts match DigUp's vectors).
         */
        fun open(models: ModelPaths, textOnly: Boolean, threads: Int = DEFAULT_THREADS, maxTokens: Int = 0): Embedder {
            val media = if (textOnly) null else (models.media ?: throw EmbedderException(
                "${ModelFiles.media.name} (images and audio) isn't on the phone yet"))
            return Embedder(nativeLoad(models.text.path, media?.path, textOnly, threads, maxTokens))
        }

        /** What llama.cpp was built with for this CPU (NEON, ARM_FMA, …), for logs. */
        fun systemInfo(): String = nativeSystemInfo()

        @JvmStatic private external fun nativeLoad(
            textModel: String, mediaModel: String?, textOnly: Boolean, threads: Int, maxTokens: Int,
        ): Long
        @JvmStatic private external fun nativeFree(handle: Long)
        @JvmStatic private external fun nativeLoadSeconds(handle: Long): Double
        @JvmStatic private external fun nativeDimension(handle: Long): Int
        @JvmStatic private external fun nativeEmbedTexts(handle: Long, texts: Array<ByteArray>): FloatArray
        @JvmStatic private external fun nativeEmbedImage(handle: Long, path: String, budget: Int): FloatArray
        @JvmStatic private external fun nativeEmbedAudio(handle: Long, path: String): FloatArray
        @JvmStatic private external fun nativeSystemInfo(): String
    }
}

/** The dot product of two L2-normalized vectors: their cosine. */
fun cosine(a: FloatArray, b: FloatArray): Float {
    require(a.size == b.size) { "vectors of ${a.size} and ${b.size}" }
    var sum = 0.0
    for (i in a.indices) sum += a[i].toDouble() * b[i]
    return sum.toFloat()
}
