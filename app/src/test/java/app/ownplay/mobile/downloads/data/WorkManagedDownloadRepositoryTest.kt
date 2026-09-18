package app.ownplay.mobile.downloads.data

import app.ownplay.mobile.downloads.domain.DownloadId
import app.ownplay.mobile.downloads.domain.DownloadStatus
import app.ownplay.mobile.downloads.domain.DownloadFailureCode
import java.io.OutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkManagedDownloadRepositoryTest {
    @Test
    fun missingCompletedOutputBecomesRetryableIntegrityFailure() = runBlocking {
        val store = DownloadTestStore()
        val completed = store.completed()
        val scheduler = FakeScheduler()
        val repository = managed(store, scheduler) { false }

        val reconciled = requireNotNull(repository.get(completed.downloadId))
        assertEquals(DownloadStatus.FAILED, reconciled.status)
        assertEquals(DownloadFailureCode.INTEGRITY, reconciled.failureCode)

        val retried = repository.enqueue(store.request)
        assertEquals(DownloadStatus.QUEUED, retried.status)
        assertEquals(listOf(retried.downloadId), scheduler.enqueued)
    }

    @Test
    fun completedOutputIsReprobedSoExternalDeletionIsDetected() = runBlocking {
        val store = DownloadTestStore()
        val completed = store.completed()
        var probes = 0
        val repository = managed(store, FakeScheduler()) {
            probes += 1
            probes == 1
        }

        assertEquals(DownloadStatus.COMPLETED, repository.get(completed.downloadId)?.status)
        val missing = requireNotNull(repository.get(completed.downloadId))
        assertEquals(DownloadStatus.FAILED, missing.status)
        assertEquals(DownloadFailureCode.INTEGRITY, missing.failureCode)
        assertEquals(2, probes)
    }

    private fun managed(
        store: DownloadTestStore,
        scheduler: FakeScheduler,
        available: suspend () -> Boolean,
    ) = WorkManagedDownloadRepository(
        delegate = store.repository,
        scheduler = scheduler,
        storage = FakeStorage,
        notifications = FakeNotifications,
        availabilityProbe = DownloadedMediaAvailabilityProbe { available() },
    )

    private class FakeScheduler : DownloadWorkScheduler {
        val enqueued = mutableListOf<DownloadId>()
        override suspend fun enqueue(downloadId: DownloadId, replace: Boolean) {
            enqueued += downloadId
        }
        override fun cancel(downloadId: DownloadId) = Unit
    }

    private object FakeNotifications : DownloadNotificationEvents {
        override fun cancelResult(downloadId: DownloadId) = Unit
        override fun cancelAll(downloadId: DownloadId) = Unit
    }

    private object FakeStorage : DownloadStorage {
        override suspend fun openPending(downloadId: DownloadId, media: ResolvedDownloadMedia): PendingDownloadOutput? = null
        override suspend fun verifiedSize(pending: PendingDownloadOutput): Long? = null
        override suspend fun publish(pending: PendingDownloadOutput): String? = null
        override suspend fun discard(pending: PendingDownloadOutput) = Unit
        override suspend fun discardPending(downloadId: DownloadId): Boolean = true
        override suspend fun removePublished(localReference: String): Boolean = true
    }
}
