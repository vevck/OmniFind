package com.omnifind.app.index

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Photos and screenshots for the vision encoder. Android decodes them (HEIC and WebP included, which llama.cpp's own
 * reader can't), turns them upright from their EXIF orientation, and shrinks them, so a 12-megapixel photo never
 * reaches the model at full size; the native side then sizes them exactly for the token budget (image_sizing.cpp).
 */
object Pictures {
    data class Info(val width: Int, val height: Int, val taken: String?)

    /** Size (upright) and date taken, read without decoding the pixels. Null for a file Android can't decode. */
    fun info(file: File): Info? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val exif = runCatching { ExifInterface(file.path) }.getOrNull()
        val sideways = exif?.let { it.rotationDegrees == 90 || it.rotationDegrees == 270 } ?: false
        val taken = exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let(::isoDate)
        return if (sideways) Info(bounds.outHeight, bounds.outWidth, taken) else Info(bounds.outWidth, bounds.outHeight, taken)
    }

    private fun isoDate(exif: String): String? = runCatching {
        val date = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.ROOT).parse(exif) ?: return null
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(date)
    }.getOrNull()

    /**
     * An upright copy of [file] in [cache], its long side at most [maxSide] px (enough for any budget OmniFind uses:
     * 280 soft tokens fill about 800 × 800), as a JPEG the native reader (stb_image) opens. The caller deletes it.
     */
    fun prepare(file: File, cache: File, maxSide: Int = 1344): File {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Android can't decode this picture" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: throw IllegalStateException("Android can't decode this picture")

        val degrees = runCatching { ExifInterface(file.path).rotationDegrees }.getOrDefault(0)
        val scale = minOf(1f, maxSide.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            if (degrees != 0) postRotate(degrees.toFloat())
        }
        val upright = if (scale < 1f || degrees != 0) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else decoded

        // Transparent PNGs (screenshots of stickers, logos) go on white, as a viewer shows them.
        val opaque = if (upright.hasAlpha()) {
            Bitmap.createBitmap(upright.width, upright.height, Bitmap.Config.ARGB_8888).also { canvas ->
                android.graphics.Canvas(canvas).apply {
                    drawColor(android.graphics.Color.WHITE)
                    drawBitmap(upright, 0f, 0f, null)
                }
                upright.recycle()
            }
        } else upright

        cache.mkdirs()
        val out = File.createTempFile("picture", ".jpg", cache)
        out.outputStream().use { opaque.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        opaque.recycle()
        return out
    }
}
