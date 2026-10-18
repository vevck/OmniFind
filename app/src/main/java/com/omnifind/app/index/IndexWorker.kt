package com.omnifind.app.index

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.omnifind.app.OmniFindApp
import com.omnifind.app.R
import com.omnifind.app.engine.Embedder
import com.omnifind.app.engine.ModelFiles
import java.util.concurrent.TimeUnit

/**
 * Indexing in the background, as a foreground job with a notification (Android stops anything else that runs this
 * long). It reads new and changed files into keyword search, then embeds waiting pieces, but only while the phone
 * charges (or when asked to anyway) and stops embedding when the battery gets hot. Stopping loses nothing: the next
 * run picks up where this one left.
 */
class IndexWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val app = context.applicationContext as OmniFindApp

    override suspend fun doWork(): Result {
        val force = inputData.getBoolean(KEY_EMBED_ANYWAY, false)
        val settings = app.settings
        setForeground(foreground("Looking for new and changed files…"))
        val indexer = Indexer(app.store)
        try {
            // Changing the picture settings re-embeds pictures only; text vectors are never touched by it.
            app.store.usePictureSettings(settings.pictureSource)
            val summary = indexer.readFiles(settings.roots, settings.kinds, stopped = { isStopped }) { report(it) }
            settings.lastRun = summary.toString()
            if (isStopped) return Result.success()

            val models = ModelFiles.locate(applicationContext)
            if (models == null) {
                Log.i(TAG, "no model on the phone yet: keyword search only")
                return Result.success()
            }
            val stats = app.store.stats()
            if (!stats.anyWaiting) return Result.success()
            if (!force && !charging()) return Result.success()

            // The image encoder (0.55 GB more) only when pictures wait for it. Text uses DigUp's 2,048-token context:
            // a chunk (~450 tokens) or a page's first 2,000 characters fit.
            val pictures = stats.picturesWaiting.values.sum() > 0 && models.media != null
            setForeground(foreground("Embedding…"))
            var embedded = 0
            Embedder.open(models, textOnly = !pictures, threads = settings.threads, maxTokens = 2048).use { embedder ->
                embedded = indexer.embed(
                    texts = embedder,
                    images = if (pictures) embedder else null,
                    budgets = settings.budgets,
                    cache = applicationContext.cacheDir,
                    stopped = { isStopped || hot() || (!force && !charging()) },
                ) { report(it) }
            }
            settings.lastRun = "$summary · $embedded embedded"
            return Result.success()
        } catch (error: Throwable) {
            Log.e(TAG, "indexing stopped", error)
            settings.lastRun = "Stopped: ${error.message ?: error.javaClass.simpleName}"
            return Result.retry()
        }
    }

    private var lastReport = 0L

    /** Called from indexing's own (blocking) loop, so it posts rather than suspends. */
    private fun report(progress: Indexer.Progress) {
        setProgressAsync(workDataOf(
            KEY_PHASE to progress.phase, KEY_DONE to progress.done, KEY_TOTAL to progress.total,
            KEY_CURRENT to progress.current,
        ))
        val now = System.currentTimeMillis()
        if (now - lastReport < 2000) return   // notifications are rate-limited; the screen shows the details
        lastReport = now
        val text = if (progress.total > 0) "${progress.phase}: ${progress.done} of ${progress.total}" else progress.phase
        setForegroundAsync(foreground(text, progress.done, progress.total))
    }

    private fun foreground(text: String, done: Int = 0, total: Int = 0): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Indexing", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows while OmniFind reads your files"
                },
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("OmniFind is reading your files")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(total, done, total == 0)
            .addAction(0, "Stop", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id))
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun battery(): Intent? =
        applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    private fun charging(): Boolean = (battery()?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0

    /** Above 42 °C embedding waits for another day: the Mi A1 slows down when hot, and it's bad for the battery. */
    private fun hot(): Boolean = (battery()?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) > 420

    companion object {
        private const val TAG = "OmniFind/index"
        private const val CHANNEL = "indexing"
        private const val NOTIFICATION_ID = 7
        const val NOW = "index-now"
        const val PERIODIC = "index-periodic"
        const val KEY_EMBED_ANYWAY = "embed_anyway"
        const val KEY_PHASE = "phase"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_CURRENT = "current"

        /** Reads files now; embeds too if charging, or anyway with [embedAnyway]. */
        fun runNow(context: Context, embedAnyway: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<IndexWorker>()
                .setInputData(workDataOf(KEY_EMBED_ANYWAY to embedAnyway))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, request)
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NOW)
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
            schedule(context, (context.applicationContext as OmniFindApp).settings.embedWhileCharging)
        }

        /** Every 6 hours while charging and the battery isn't low: reads what changed, then embeds. */
        fun schedule(context: Context, enabled: Boolean) {
            val work = WorkManager.getInstance(context)
            if (!enabled) {
                work.cancelUniqueWork(PERIODIC)
                return
            }
            val request = PeriodicWorkRequestBuilder<IndexWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresCharging(true).setRequiresBatteryNotLow(true).build())
                .build()
            work.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
