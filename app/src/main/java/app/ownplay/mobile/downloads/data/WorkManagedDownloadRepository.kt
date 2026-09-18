package app.ownplay.mobile.downloads.data

import app.ownplay.mobile.downloads.domain.DownloadFailureCode
import app.ownplay.mobile.downloads.domain.DownloadId
import app.ownplay.mobile.downloads.domain.DownloadItem
import app.ownplay.mobile.downloads.domain.DownloadRepository
import app.ownplay.mobile.downloads.domain.DownloadRequest
import app.ownplay.mobile.downloads.domain.DownloadStatus
import app.ownplay.mobile.sources.domain.SourceId
import kotlinx.coroutines.flow.Flow

internal class WorkManagedDownloadRepository(
    private val delegate: DownloadRepository,
    private val scheduler: DownloadWorkScheduler,
    private val storage: DownloadStorage,
    private val notifications: DownloadNotificationEvents,
) : DownloadRepository {
    override fun observeDownloads(sourceId: SourceId): Flow<List<DownloadItem>> =
        delegate.observeDownloads(sourceId)

    override fun observeDownload(downloadId: DownloadId): Flow<DownloadItem?> =
        delegate.observeDownload(downloadId)

    override suspend fun get(downloadId: DownloadId): DownloadItem? = delegate.get(downloadId)

    override suspend fun enqueue(request: DownloadRequest): DownloadItem {
        val item = delegate.enqueue(request)
        if (item.status == DownloadStatus.QUEUED) {
            notifications.cancelResult(item.downloadId)
            scheduler.enqueue(item.downloadId, replace = false)
        }
        return item
    }

    override suspend fun markDownloading(downloadId: DownloadId): Boolean =
        delegate.markDownloading(downloadId)

    override suspend fun updateProgress(
        downloadId: DownloadId,
        bytesDownloaded: Long,
        totalBytes: Long?,
    ): Boolean = delegate.updateProgress(downloadId, bytesDownloaded, totalBytes)

    override suspend fun pause(downloadId: DownloadId): Boolean {
        val changed = delegate.pause(downloadId)
        if (changed) scheduler.cancel(downloadId)
        return changed
    }

    override suspend fun resume(downloadId: DownloadId): Boolean {
        val changed = delegate.resume(downloadId)
        if (changed) {
            notifications.cancelResult(downloadId)
            scheduler.enqueue(downloadId, replace = true)
        }
        return changed
    }

    override suspend fun cancel(downloadId: DownloadId): Boolean {
        val changed = delegate.cancel(downloadId)
        if (changed) scheduler.cancel(downloadId)
        return changed
    }

    override suspend fun complete(
        downloadId: DownloadId,
        localReference: String,
        verifiedBytes: Long,
        sha256: String?,
    ): Boolean = delegate.complete(downloadId, localReference, verifiedBytes, sha256)

    override suspend fun fail(
        downloadId: DownloadId,
        failureCode: DownloadFailureCode,
    ): Boolean = delegate.fail(downloadId, failureCode)

    override suspend fun remove(downloadId: DownloadId): Boolean {
        val existing = delegate.get(downloadId) ?: return false
        scheduler.cancel(downloadId)
        if (
            existing.status == DownloadStatus.COMPLETED &&
            existing.localReference != null &&
            !storage.removePublished(existing.localReference)
        ) {
            return false
        }
        val removed = delegate.remove(downloadId)
        if (removed) notifications.cancelAll(downloadId)
        return removed
    }
}
