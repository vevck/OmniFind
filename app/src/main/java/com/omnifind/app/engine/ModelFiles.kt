package com.omnifind.app.engine

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * The EmbeddingGemma 2 files OmniFind runs, pinned exactly as DigUp pins them (`Sources/DigUpKit/ModelFiles.swift`):
 * ggml-org's Q8_0 GGUF conversion at one revision. Never F16 files: the model's activations overflow float16.
 */
object ModelFiles {
    const val REPOSITORY = "ggml-org/embeddinggemma-2-GGUF"
    const val REVISION = "bfcd298762cc34d0357ece5ebdd31791a3a374d8"

    data class File(val name: String, val bytes: Long, val sha256: String) {
        /** Hugging Face's address at the pinned revision (it redirects to a CDN that takes HTTP ranges). */
        val url: String get() = "https://huggingface.co/$REPOSITORY/resolve/$REVISION/$name"
    }

    /** The text model (310 MB): all that queries need. */
    val text = File("embeddinggemma-2-Q8_0.gguf", 309_855_456,
        "2188ac1deca4b77dffefd603c2776a9d76d9d74ec01841392982ebb840b09135")

    /** The image and audio encoders (555 MB). */
    val media = File("mmproj-embeddinggemma-2-Q8_0.gguf", 554_821_024,
        "c4a8a52691ecef40618438928bdf9e68379b854e24166f292592353db0aab64f")

    val all = listOf(text, media)

    /**
     * Where the app keeps them: /sdcard/Android/data/com.omnifind.app/files/models. Needs no permission, survives
     * app updates, and is removed with the app. During development, `adb push` puts the files there.
     */
    fun folder(context: Context): java.io.File =
        java.io.File(context.getExternalFilesDir(null) ?: context.filesDir, "models").apply { mkdirs() }

    /** The files in [folder], or null while the text model is missing (or has the wrong size). */
    fun locate(context: Context): ModelPaths? {
        val folder = folder(context)
        fun present(file: File) = java.io.File(folder, file.name).takeIf { it.length() == file.bytes }
        return present(text)?.let { ModelPaths(it, present(media)) }
    }

    /** The SHA-256 of a file as lowercase hex, read in 8 MB pieces (seconds for 555 MB on a phone). */
    fun sha256(file: java.io.File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8 shl 20)
        file.inputStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/** The model files on this phone. [media] is null while only the text part is there. */
data class ModelPaths(val text: File, val media: File?)
