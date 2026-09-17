package app.ownplay.mobile.downloads.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.ownplay.mobile.OwnPlayApplication
import app.ownplay.mobile.downloads.domain.DownloadNotificationPolicy
import app.ownplay.mobile.downloads.domain.DownloadState
import app.ownplay.mobile.downloads.domain.DownloadWorkResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        val downloadDao = application.services.database.downloadDao()
        val notificationController = DownloadNotificationController(applicationContext)
        val initial = downloadDao.get(downloadId)?.toNotificationSnapshotOrNull()
            ?: return Result.failure().also { notificationController.cancel(downloadId) }

        if (initial.state == DownloadState.QUEUED || initial.state == DownloadState.DOWNLOADING) {
            notificationController.cancelControl(downloadId)
            setForeground(notificationController.foregroundInfo(initial))
        }

        return coroutineScope {
            val notificationJob = launch {
                downloadDao.observe(downloadId).collect { row ->
                    val snapshot = row?.toNotificationSnapshotOrNull() ?: return@collect
                    if (snapshot.state == DownloadState.QUEUED || snapshot.state == DownloadState.DOWNLOADING) {
                        try {
                            setForeground(notificationController.foregroundInfo(snapshot))
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // A progress-notification refresh must not corrupt the transfer state machine.
                        }
                    }
                }
            }

            val workResult = try {
                application.services.downloadRepository.executeWork(downloadId)
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) {
                    if (downloadDao.queueIfDownloading(downloadId, System.currentTimeMillis()) > 0) {
                        downloadDao.get(downloadId)?.toNotificationSnapshotOrNull()?.let(notificationController::show)
                    }
                }
                throw cancelled
            } finally {
                withContext(NonCancellable) { notificationJob.cancelAndJoin() }
            }

            val finalSnapshot = downloadDao.get(downloadId)?.toNotificationSnapshotOrNull()
            if (finalSnapshot == null) {
                notificationController.cancelControl(downloadId)
            } else if (DownloadNotificationPolicy.present(finalSnapshot).visible) {
                notificationController.show(finalSnapshot)
            } else {
                notificationController.cancelControl(downloadId)
            }

            when (workResult) {
                DownloadWorkResult.SUCCESS,
                DownloadWorkResult.NO_OP,
                -> Result.success()
                DownloadWorkResult.RETRY -> Result.retry()
                DownloadWorkResult.FAILURE -> Result.failure()
            }
        }
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
    }
}
