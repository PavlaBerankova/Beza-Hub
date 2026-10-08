package com.berpod.app.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.berpod.app.BerpodApplication
import com.berpod.app.R
import com.berpod.app.data.local.DownloadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Works through the waiting downloads one after another, oldest first. One worker for all of them, so
 * a long queue never fetches in parallel, and the user cancelling one just removes its row.
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as BerpodApplication).container
        val dao = container.downloadDao
        val downloader = container.episodeDownloader
        // Can be refused when the app is in the background; the download is resumable, so carry on without it.
        runCatching { setForeground(getForegroundInfo()) }

        while (true) {
            val next = dao.nextToDownload() ?: return Result.success()
            dao.setStatus(next.episodeId, DownloadStatus.DOWNLOADING)
            val outcome = try {
                downloader.download(next.episodeId)
            } catch (e: CancellationException) {
                // Stopped by the system (e.g. Wi-Fi gone); it goes back to waiting and WorkManager restarts us.
                withContext(NonCancellable) { dao.setStatus(next.episodeId, DownloadStatus.QUEUED) }
                throw e
            }
            when (outcome) {
                DownloadOutcome.Done, DownloadOutcome.Removed -> Unit
                DownloadOutcome.Failed -> dao.setStatus(next.episodeId, DownloadStatus.FAILED)
                DownloadOutcome.Retry -> {
                    if (runAttemptCount >= MAX_ATTEMPTS) {
                        dao.setStatus(next.episodeId, DownloadStatus.FAILED)
                    } else {
                        dao.setStatus(next.episodeId, DownloadStatus.QUEUED)
                        return Result.retry()
                    }
                }
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        ensureChannel(applicationContext)
        val notification: Notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(applicationContext.getString(R.string.download_notification_title))
            .setOngoing(true)
            .setProgress(0, 0, true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.download_channel_name), NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 4711
        private const val MAX_ATTEMPTS = 5
        const val UNIQUE_NAME = "episode-downloads"
    }
}
