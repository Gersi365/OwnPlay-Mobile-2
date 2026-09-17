package app.ownplay.mobile.downloads.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.ownplay.mobile.OwnPlayApplication
import app.ownplay.mobile.downloads.domain.DownloadId

class DownloadWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val rawId = inputData.getString(KEY_DOWNLOAD_ID)?.takeIf(String::isNotBlank)
            ?: return Result.failure()
        val downloadId = runCatching { DownloadId(rawId) }.getOrNull()
            ?: return Result.failure()
        val application = applicationContext as? OwnPlayApplication
            ?: return Result.failure()

        return when (application.services.downloadExecutor.execute(downloadId)) {
            DownloadExecutionOutcome.COMPLETED,
            DownloadExecutionOutcome.SKIPPED,
            DownloadExecutionOutcome.PERSISTED_FAILURE,
            -> Result.success()
        }
    }

    companion object {
        internal const val KEY_DOWNLOAD_ID = "download_id"
    }
}
