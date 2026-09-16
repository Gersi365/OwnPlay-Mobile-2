package app.ownplay.mobile.downloads.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import app.ownplay.mobile.data.db.DownloadEntity
import app.ownplay.mobile.downloads.domain.DownloadAction
import app.ownplay.mobile.downloads.domain.DownloadNotificationPolicy
import app.ownplay.mobile.downloads.domain.DownloadNotificationSnapshot
import app.ownplay.mobile.downloads.domain.DownloadState
import java.util.Locale

internal class DownloadNotificationController(context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)

    fun foregroundInfo(snapshot: DownloadNotificationSnapshot): ForegroundInfo {
        ensureChannel()
        val notification = buildNotification(snapshot)
        val notificationId = foregroundNotificationId(snapshot.downloadId)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    fun show(snapshot: DownloadNotificationSnapshot) {
        val presentation = DownloadNotificationPolicy.present(snapshot)
        if (!presentation.visible) {
            cancel(snapshot.downloadId)
            return
        }
        ensureChannel()
        runCatching {
            notificationManager.notify(controlNotificationId(snapshot.downloadId), buildNotification(snapshot))
        }
    }

    fun cancelControl(downloadId: String) {
        if (downloadId.isBlank()) return
        notificationManager.cancel(controlNotificationId(downloadId))
    }

    fun cancel(downloadId: String) {
        if (downloadId.isBlank()) return
        notificationManager.cancel(controlNotificationId(downloadId))
        notificationManager.cancel(foregroundNotificationId(downloadId))
    }

    private fun buildNotification(snapshot: DownloadNotificationSnapshot): Notification {
        val presentation = DownloadNotificationPolicy.present(snapshot)
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(snapshot.title)
            .setContentText(presentation.statusText)
            .setOngoing(presentation.visible)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        when {
            presentation.progressPercent != null ->
                builder.setProgress(100, presentation.progressPercent, false)
            presentation.progressIndeterminate ->
                builder.setProgress(0, 0, true)
        }

        when (presentation.action) {
            DownloadAction.PAUSE -> builder.addAction(
                android.R.drawable.ic_media_pause,
                "Pause",
                actionPendingIntent(snapshot.downloadId, DownloadNotificationActionReceiver.ACTION_PAUSE),
            )
            DownloadAction.RESUME -> builder.addAction(
                android.R.drawable.ic_media_play,
                "Resume",
                actionPendingIntent(snapshot.downloadId, DownloadNotificationActionReceiver.ACTION_RESUME),
            )
            else -> Unit
        }
        return builder.build()
    }

    private fun actionPendingIntent(downloadId: String, action: String): PendingIntent {
        val intent = Intent(appContext, DownloadNotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(DownloadNotificationActionReceiver.EXTRA_DOWNLOAD_ID, downloadId)
        }
        val requestCode = (31 * downloadId.hashCode() + action.hashCode()).and(Int.MAX_VALUE)
        return PendingIntent.getBroadcast(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "OwnPlay downloads",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "ownplay_downloads"

        fun foregroundNotificationId(downloadId: String): Int =
            downloadId.hashCode().and(Int.MAX_VALUE).coerceAtLeast(1)

        fun controlNotificationId(downloadId: String): Int =
            (31 * downloadId.hashCode() + CONTROL_ID_SALT).and(Int.MAX_VALUE).coerceAtLeast(1)

        private const val CONTROL_ID_SALT = 0x4F57504C
    }
}

internal fun DownloadEntity.toNotificationSnapshotOrNull(): DownloadNotificationSnapshot? {
    val state = runCatching { DownloadState.valueOf(state.uppercase(Locale.US)) }.getOrNull() ?: return null
    return DownloadNotificationSnapshot(
        downloadId = downloadId,
        title = title,
        state = state,
        bytesDownloaded = bytesDownloaded.coerceAtLeast(0L),
        totalBytes = totalBytes?.takeIf { it > 0L },
    )
}
