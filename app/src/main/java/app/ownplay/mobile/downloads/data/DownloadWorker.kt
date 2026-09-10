package app.ownplay.mobile.downloads.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import app.ownplay.mobile.OwnPlayApplication
import app.ownplay.mobile.downloads.domain.DownloadWorkResult

internal class DownloadWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return Result.failure()
        val application = applicationContext as? OwnPlayApplication
            ?: return Result.failure()

        setForeground(createForegroundInfo(downloadId))
        return when (application.services.downloadRepository.executeWork(downloadId)) {
            DownloadWorkResult.SUCCESS,
            DownloadWorkResult.NO_OP,
            -> Result.success()

            DownloadWorkResult.RETRY -> Result.retry()
            DownloadWorkResult.FAILURE -> Result.failure()
        }
    }

    private fun createForegroundInfo(downloadId: String): ForegroundInfo {
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "OwnPlay downloads",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("OwnPlay download")
            .setContentText("Saving media for offline playback")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
        val notificationId = downloadId.hashCode().and(Int.MAX_VALUE).coerceAtLeast(1)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        private const val CHANNEL_ID = "ownplay_downloads"
    }
}
