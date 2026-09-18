package app.ownplay.mobile.feature.settings.data

import app.ownplay.mobile.feature.settings.domain.SourceRefreshSchedule
import app.ownplay.mobile.feature.settings.domain.SourceRefreshScheduleRepository
import app.ownplay.mobile.sources.domain.SourceId
import app.ownplay.mobile.sources.domain.SourceRepository
import kotlinx.coroutines.flow.Flow

internal interface SourceRefreshScheduleCleanup {
    suspend fun clearSource(sourceId: SourceId)
}

internal class ManagedSourceRefreshScheduleRepository(
    private val store: SourceRefreshScheduleStore,
    private val scheduler: SourceRefreshScheduler,
    private val sourceExists: suspend (SourceId) -> Boolean,
) : SourceRefreshScheduleRepository, SourceRefreshScheduleCleanup {
    override fun observeSchedule(sourceId: SourceId): Flow<SourceRefreshSchedule> = store.observe(sourceId)

    override suspend fun setSchedule(
        sourceId: SourceId,
        schedule: SourceRefreshSchedule,
    ): Boolean {
        if (!runCatching { sourceExists(sourceId) }.getOrDefault(false)) return false
        val previous = runCatching { store.current(sourceId) }.getOrNull() ?: return false
        return try {
            store.set(sourceId, schedule)
            scheduler.apply(sourceId, schedule)
            true
        } catch (_: Exception) {
            runCatching { store.set(sourceId, previous) }
            runCatching { scheduler.apply(sourceId, previous) }
            false
        }
    }

    override suspend fun clearSource(sourceId: SourceId) {
        runCatching { scheduler.cancel(sourceId) }
        runCatching { store.clear(sourceId) }
    }
}

internal class RefreshScheduleAwareSourceRepository(
    private val delegate: SourceRepository,
    private val scheduleCleanup: SourceRefreshScheduleCleanup,
) : SourceRepository by delegate {
    override suspend fun removeSource(sourceId: SourceId): Boolean {
        if (!delegate.removeSource(sourceId)) return false
        runCatching { scheduleCleanup.clearSource(sourceId) }
        return true
    }
}
