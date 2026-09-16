package app.ownplay.mobile.downloads.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.ownplay.mobile.OwnPlayApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal class DownloadNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID)
            ?.takeIf(String::isNotBlank)
            ?: return
        val application = context.applicationContext as? OwnPlayApplication ?: return
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                when (intent.action) {
                    ACTION_PAUSE -> application.services.downloadRepository.pause(downloadId)
                    ACTION_RESUME -> application.services.downloadRepository.resume(downloadId)
                }
            } catch (_: Exception) {
                // Repository state remains authoritative; a failed receiver action must not crash the process.
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "app.ownplay.mobile.action.PAUSE_DOWNLOAD"
        const val ACTION_RESUME = "app.ownplay.mobile.action.RESUME_DOWNLOAD"
        const val EXTRA_DOWNLOAD_ID = "download_id"

        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
