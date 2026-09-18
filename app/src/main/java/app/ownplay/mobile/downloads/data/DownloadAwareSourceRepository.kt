package app.ownplay.mobile.downloads.data

import app.ownplay.mobile.data.db.DownloadDao
import app.ownplay.mobile.downloads.domain.DownloadId
import app.ownplay.mobile.sources.domain.SourceId
import app.ownplay.mobile.sources.domain.SourceRepository

internal data class SourceRemovalDownloadPlan(
    val downloadIds: List<DownloadId>,
)

internal interface SourceRemovalDownloadCoordinator {
    suspend fun capture(sourceId: SourceId): SourceRemovalDownloadPlan?
    suspend fun finalize(plan: SourceRemovalDownloadPlan)
}

internal class ManagedSourceRemovalDownloadCoordinator(
    private val downloadDao: DownloadDao,
    private val scheduler: DownloadWorkScheduler,
    private val storage: DownloadStorage,
    private val notifications: DownloadNotificationEvents,
) : SourceRemovalDownloadCoordinator {
    override suspend fun capture(sourceId: SourceId): SourceRemovalDownloadPlan? =
        runCatching {
            SourceRemovalDownloadPlan(
                downloadIds = downloadDao.getIdsForSource(sourceId.value).map(::DownloadId),
            )
        }.getOrNull()

    override suspend fun finalize(plan: SourceRemovalDownloadPlan) {
        plan.downloadIds.forEach { downloadId ->
            scheduler.cancel(downloadId)
            runCatching { storage.discardPending(downloadId) }
            notifications.cancelAll(downloadId)
        }
    }
}

internal class DownloadAwareSourceRepository(
    private val delegate: SourceRepository,
    private val removalCoordinator: SourceRemovalDownloadCoordinator,
) : SourceRepository by delegate {
    override suspend fun removeSource(sourceId: SourceId): Boolean {
        val plan = removalCoordinator.capture(sourceId) ?: return false
        if (!delegate.removeSource(sourceId)) return false
        removalCoordinator.finalize(plan)
        return true
    }
}
