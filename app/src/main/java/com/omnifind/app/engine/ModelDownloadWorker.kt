package com.omnifind.app.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.omnifind.app.R
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the model files (865 MB) from Hugging Face at the pinned revision, as DigUp's ModelDownload does: over
 * Wi-Fi, resuming a broken download where it stopped (HTTP ranges), and checking each file's SHA-256 before giving it
 * its final name, so a half-written or wrong file is never loaded. The text model comes first: search works by meaning
 * as soon as it's in. Nothing is sent but the requests for the files.
 */
class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val folder = ModelFiles.folder(applicationContext)
        val missing = ModelFiles.all.filter { File(folder, it.name).length() != it.bytes }
        if (missing.isEmpty()) return Result.success()
        val total = missing.sumOf { it.bytes }
        var before = ModelFiles.all.filter { it !in missing }.sumOf { it.bytes }
        val grand = ModelFiles.all.sumOf { it.bytes }
        setForeground(foreground(0, 0))
        try {
            for (file in missing) {
                fetch(file, folder) { done -> report(before + done, grand) }
                before += file.bytes
            }
            return Result.success()
        } catch (error: Throwable) {
            Log.w(TAG, "download stopped", error)
            setProgress(workDataOf(KEY_ERROR to (error.message ?: error.javaClass.simpleName)))
            return if (runAttemptCount < 5) Result.retry() else Result.failure(workDataOf(KEY_ERROR to error.message))
        } finally {
            Log.i(TAG, "of ${total shr 20} MB missing, done ${ModelFiles.all.count { File(folder, it.name).length() == it.bytes }} files")
        }
    }

    private fun fetch(file: ModelFiles.File, folder: File, progress: (Long) -> Unit) {
        val part = File(folder, file.name + ".part")
        var have = part.length().takeIf { it <= file.bytes } ?: run { part.delete(); 0L }
        if (have < file.bytes) {
            val connection = (URL(file.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                if (have > 0) setRequestProperty("Range", "bytes=$have-")
            }
            try {
                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_OK) have = 0   // the server ignored the range: start over
                else if (code != HttpURLConnection.HTTP_PARTIAL) throw IllegalStateException("HTTP $code for ${file.name}")
                RandomAccessFile(part, "rw").use { out ->
                    out.setLength(have)
                    out.seek(have)
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(1 shl 16)
                        var lastReport = 0L
                        while (!isStopped) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            have += read
                            if (have - lastReport > (4 shl 20)) {
                                lastReport = have
                                progress(have)
                            }
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
        if (isStopped) return
        check(part.length() == file.bytes) { "${file.name}: got ${part.length()} of ${file.bytes} bytes" }
        setProgressAsync(workDataOf(KEY_PHASE to "Checking ${file.name}"))
        val sum = ModelFiles.sha256(part)
        if (sum != file.sha256) {
            part.delete()
            throw IllegalStateException("${file.name} didn't match its checksum; it will be downloaded again")
        }
        check(part.renameTo(File(folder, file.name))) { "can't move ${file.name} into place" }
    }

    private fun report(done: Long, total: Long) {
        setProgressAsync(workDataOf(KEY_DONE to done, KEY_TOTAL to total, KEY_PHASE to "Downloading"))
        setForegroundAsync(foreground(done, total))
    }

    private fun foreground(done: Long, total: Long): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Model download", NotificationManager.IMPORTANCE_LOW))
        }
        val percent = if (total > 0) (done * 100 / total).toInt() else 0
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Downloading OmniFind's model")
            .setContentText(if (total > 0) "${done shr 20} of ${total shr 20} MB" else "Starting…")
            .setProgress(100, percent, total == 0L)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Stop", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id))
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "OmniFind/download"
        private const val CHANNEL = "model_download"
        private const val NOTIFICATION_ID = 8
        const val WORK = "model-download"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_PHASE = "phase"
        const val KEY_ERROR = "error"

        /** Starts (or keeps) the download, on Wi-Fi only unless [anyNetwork]. */
        fun start(context: Context, anyNetwork: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(if (anyNetwork) NetworkType.CONNECTED else NetworkType.UNMETERED)
                    .setRequiresStorageNotLow(true)
                    .build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP, request)
        }

        fun stop(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }
}
