package app.ownplay.mobile.downloads.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.ownplay.mobile.OwnPlayApplication
import app.ownplay.mobile.downloads.domain.DownloadId
import app.ownplay.mobile.downloads.domain.DownloadStatus
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class DownloadWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = coroutineScope {
        val rawId = inputData.getString(KEY_DOWNLOAD_ID)?.takeIf(String::isNotBlank)
            ?: return@coroutineScope Result.failure()
        val downloadId = runCatching { DownloadId(rawId) }.getOrNull()
            ?: return@coroutineScope Result.failure()
        val application = applicationContext as? OwnPlayApplication
            ?: return@coroutineScope Result.failure()
        val services = application.services
        val initial = services.downloadRepository.get(downloadId)
            ?: return@coroutineScope Result.success()
        if (initial.status !in ACTIVE_STATES) {
            return@coroutineScope Result.success()
        }

        services.downloadNotifications.cancelResult(downloadId)
        setForeground(services.downloadNotifications.foregroundInfo(initial))
        val foregroundUpdates = launch {
            services.downloadRepository.observeDownload(downloadId)
                .filterNotNull()
                .collect { item ->
                    if (item.status in ACTIVE_STATES) {
                        setForeground(services.downloadNotifications.foregroundInfo(item))
                    }
                }
        }

        try {
            val outcome = services.downloadExecutor.execute(downloadId)
            services.downloadNotifications.showTerminal(
                services.downloadRepository.get(downloadId),
            )
            when (outcome) {
                DownloadExecutionOutcome.COMPLETED,
                DownloadExecutionOutcome.SKIPPED,
                DownloadExecutionOutcome.PERSISTED_FAILURE,
                -> Result.success()
            }
        } finally {
            foregroundUpdates.cancel()
        }
    }

    companion object {
        internal const val KEY_DOWNLOAD_ID = "download_id"
        private val ACTIVE_STATES = setOf(
            DownloadStatus.QUEUED,
            DownloadStatus.DOWNLOADING,
        )
    }
}
