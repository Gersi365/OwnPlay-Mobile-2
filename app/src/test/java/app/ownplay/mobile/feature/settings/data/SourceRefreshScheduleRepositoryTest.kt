package app.ownplay.mobile.feature.settings.data

import app.ownplay.mobile.feature.settings.domain.SourceRefreshSchedule
import app.ownplay.mobile.sources.domain.SourceId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceRefreshScheduleRepositoryTest {
    private val sourceId = SourceId("source-a")

    @Test
    fun setSchedulePersistsAndAppliesUniqueScheduleIntent() = runBlocking {
        val store = FakeStore()
        val scheduler = FakeScheduler()
        val repository = ManagedSourceRefreshScheduleRepository(store, scheduler) { true }

        assertTrue(repository.setSchedule(sourceId, SourceRefreshSchedule.EVERY_6_HOURS))

        assertEquals(SourceRefreshSchedule.EVERY_6_HOURS, store.current(sourceId))
        assertEquals(listOf(sourceId to SourceRefreshSchedule.EVERY_6_HOURS), scheduler.applied)
    }

    @Test
    fun missingSourceRejectsScheduleWithoutPersistingOrScheduling() = runBlocking {
        val store = FakeStore()
        val scheduler = FakeScheduler()
        val repository = ManagedSourceRefreshScheduleRepository(store, scheduler) { false }

        assertFalse(repository.setSchedule(sourceId, SourceRefreshSchedule.DAILY))

        assertEquals(SourceRefreshSchedule.MANUAL, store.current(sourceId))
        assertTrue(scheduler.applied.isEmpty())
    }

    @Test
    fun schedulerFailureRollsBackPersistedSchedule() = runBlocking {
        val store = FakeStore(SourceRefreshSchedule.EVERY_12_HOURS)
        val scheduler = FakeScheduler(failFirstApply = true)
        val repository = ManagedSourceRefreshScheduleRepository(store, scheduler) { true }

        assertFalse(repository.setSchedule(sourceId, SourceRefreshSchedule.DAILY))

        assertEquals(SourceRefreshSchedule.EVERY_12_HOURS, store.current(sourceId))
        assertEquals(SourceRefreshSchedule.EVERY_12_HOURS, scheduler.applied.last().second)
    }

    @Test
    fun clearSourceCancelsWorkAndClearsPreference() = runBlocking {
        val store = FakeStore(SourceRefreshSchedule.DAILY)
        val scheduler = FakeScheduler()
        val repository = ManagedSourceRefreshScheduleRepository(store, scheduler) { true }

        repository.clearSource(sourceId)

        assertEquals(listOf(sourceId), scheduler.cancelled)
        assertEquals(SourceRefreshSchedule.MANUAL, store.current(sourceId))
    }
}

private class FakeStore(
    initial: SourceRefreshSchedule = SourceRefreshSchedule.MANUAL,
) : SourceRefreshScheduleStore {
    private val flows = mutableMapOf<String, MutableStateFlow<SourceRefreshSchedule>>()
    private val initialSchedule = initial

    private fun state(sourceId: SourceId): MutableStateFlow<SourceRefreshSchedule> =
        flows.getOrPut(sourceId.value) { MutableStateFlow(initialSchedule) }

    override fun observe(sourceId: SourceId): Flow<SourceRefreshSchedule> = state(sourceId)
    override suspend fun current(sourceId: SourceId): SourceRefreshSchedule = state(sourceId).value
    override suspend fun set(sourceId: SourceId, schedule: SourceRefreshSchedule) { state(sourceId).value = schedule }
    override suspend fun clear(sourceId: SourceId) { state(sourceId).value = SourceRefreshSchedule.MANUAL }
}

private class FakeScheduler(
    private var failFirstApply: Boolean = false,
) : SourceRefreshScheduler {
    val applied = mutableListOf<Pair<SourceId, SourceRefreshSchedule>>()
    val cancelled = mutableListOf<SourceId>()

    override fun apply(sourceId: SourceId, schedule: SourceRefreshSchedule) {
        if (failFirstApply) {
            failFirstApply = false
            error("scheduler failed")
        }
        applied += sourceId to schedule
    }

    override fun cancel(sourceId: SourceId) {
        cancelled += sourceId
    }
}
