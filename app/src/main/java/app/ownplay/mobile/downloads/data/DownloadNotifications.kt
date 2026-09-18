package app.ownplay.mobile.downloads.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import app.ownplay.mobile.OwnPlayApplication
import app.ownplay.mobile.R
import app.ownplay.mobile.downloads.domain.DownloadId
import app.ownplay.mobile.downloads.domain.DownloadItem
import app.ownplay.mobile.downloads.domain.DownloadNotificationPolicy
import app.ownplay.mobile.downloads.domain.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal interface DownloadNotificationEvents {
    fun cancelResult(downloadId: DownloadId)
    fun cancelAll(downloadId: DownloadId)
}

internal class DownloadNotificationController(
    context: Context,
) : DownloadNotificationEvents {
    private val applicationContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(applicationContext)

    init {
        ensureChannel()
    }

    fun foregroundInfo(item: DownloadItem): ForegroundInfo {
        val notification = activeNotification(item)
        val id = DownloadNotificationPolicy.foregroundNotificationId(item.downloadId)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }

    fun showTerminal(item: DownloadItem?) {
        val current = item ?: return
        val notification = when (current.status) {
            DownloadStatus.COMPLETED -> terminalNotification(
                item = current,
                message = "Download complete",
            )
            DownloadStatus.FAILED -> terminalNotification(
                item = current,
                message = "Download failed. Retry from Library.",
            )
            else -> return
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            notificationManager.notify(
                DownloadNotificationPolicy.resultNotificationId(current.downloadId),
                notification,
            )
        } catch (_: SecurityException) {
            // Notification permission may be revoked between the explicit check and notify().
        }
    }

    override fun cancelResult(downloadId: DownloadId) {
        notificationManager.cancel(DownloadNotificationPolicy.resultNotificationId(downloadId))
    }

    override fun cancelAll(downloadId: DownloadId) {
        notificationManager.cancel(DownloadNotificationPolicy.foregroundNotificationId(downloadId))
        cancelResult(downloadId)
    }

    private fun activeNotification(item: DownloadItem): Notification {
        val progress = DownloadNotificationPolicy.progressPercent(
            bytesDownloaded = item.bytesDownloaded,
            totalBytes = item.totalBytes,
        )
        val text = when (item.status) {
            DownloadStatus.QUEUED -> "Waiting to download"
            DownloadStatus.DOWNLOADING -> progress?.let { "$it% downloaded" } ?: "Downloading"
            else -> "Download in progress"
        }
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download_notification)
            .setContentTitle(item.title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress ?: 0, progress == null)
            .addAction(
                R.drawable.ic_download_notification,
                "Cancel",
                cancelPendingIntent(item.downloadId),
            )
            .build()
    }

    private fun terminalNotification(
        item: DownloadItem,
        message: String,
    ): Notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_download_notification)
        .setContentTitle(item.title)
        .setContentText(message)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setAutoCancel(true)
        .build()

    private fun cancelPendingIntent(downloadId: DownloadId): PendingIntent {
        val intent = Intent(applicationContext, DownloadNotificationActionReceiver::class.java)
            .setAction(DownloadNotificationActionReceiver.ACTION_CANCEL)
            .putExtra(DownloadNotificationActionReceiver.EXTRA_DOWNLOAD_ID, downloadId.value)
        return PendingIntent.getBroadcast(
            applicationContext,
            DownloadNotificationPolicy.foregroundNotificationId(downloadId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "OwnPlay download progress and results"
            },
        )
    }

    private companion object {
        const val CHANNEL_ID = "ownplay_downloads"
    }
}

class DownloadNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CANCEL) return
        val rawId = intent.getStringExtra(EXTRA_DOWNLOAD_ID)?.takeIf(String::isNotBlank) ?: return
        val downloadId = runCatching { DownloadId(rawId) }.getOrNull() ?: return
        val application = context.applicationContext as? OwnPlayApplication ?: return
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val current = application.services.downloadRepository.get(downloadId)
                if (current != null && DownloadNotificationPolicy.canCancel(current.status)) {
                    application.services.downloadRepository.cancel(downloadId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        internal const val ACTION_CANCEL = "app.ownplay.mobile.action.CANCEL_DOWNLOAD"
        internal const val EXTRA_DOWNLOAD_ID = "download_id"
    }
}
