package app.ownplay.mobile.downloads.data

import app.ownplay.mobile.downloads.domain.DownloadId
import app.ownplay.mobile.sources.domain.SourceId
import app.ownplay.mobile.sources.domain.SourceInput
import app.ownplay.mobile.sources.domain.SourceMutationResult
import app.ownplay.mobile.sources.domain.SourceRefreshResult
import app.ownplay.mobile.sources.domain.SourceRepository
import app.ownplay.mobile.sources.domain.SourceSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadAwareSourceRepositoryTest {
    @Test
    fun successfulRemovalFinalizesCapturedDownloads() = runBlocking {
        val delegate = FakeSourceRepository(removeResult = true)
        val coordinator = FakeRemovalCoordinator(
            plan = SourceRemovalDownloadPlan(listOf(DownloadId("download:a"))),
        )
        val repository = DownloadAwareSourceRepository(delegate, coordinator)

        assertTrue(repository.removeSource(SourceId("source-a")))
        assertEquals(listOf(SourceId("source-a")), coordinator.capturedSources)
        assertEquals(listOf(SourceRemovalDownloadPlan(listOf(DownloadId("download:a")))), coordinator.finalizedPlans)
        assertEquals(1, delegate.removeCalls)
    }

    @Test
    fun captureFailureBlocksSourceRemoval() = runBlocking {
        val delegate = FakeSourceRepository(removeResult = true)
        val coordinator = FakeRemovalCoordinator(plan = null)
        val repository = DownloadAwareSourceRepository(delegate, coordinator)

        assertFalse(repository.removeSource(SourceId("source-a")))
        assertEquals(0, delegate.removeCalls)
        assertTrue(coordinator.finalizedPlans.isEmpty())
    }

    @Test
    fun failedSourceRemovalDoesNotFinalizeDownloadCleanup() = runBlocking {
        val delegate = FakeSourceRepository(removeResult = false)
        val coordinator = FakeRemovalCoordinator(SourceRemovalDownloadPlan(emptyList()))
        val repository = DownloadAwareSourceRepository(delegate, coordinator)

        assertFalse(repository.removeSource(SourceId("source-a")))
        assertEquals(1, delegate.removeCalls)
        assertTrue(coordinator.finalizedPlans.isEmpty())
    }
}

private class FakeRemovalCoordinator(
    private val plan: SourceRemovalDownloadPlan?,
) : SourceRemovalDownloadCoordinator {
    val capturedSources = mutableListOf<SourceId>()
    val finalizedPlans = mutableListOf<SourceRemovalDownloadPlan>()

    override suspend fun capture(sourceId: SourceId): SourceRemovalDownloadPlan? {
        capturedSources += sourceId
        return plan
    }

    override suspend fun finalize(plan: SourceRemovalDownloadPlan) {
        finalizedPlans += plan
    }
}

private class FakeSourceRepository(
    private val removeResult: Boolean,
) : SourceRepository {
    var removeCalls = 0

    override fun observeSources(): Flow<List<SourceSummary>> = flowOf(emptyList())
    override fun observeActiveSource(): Flow<SourceSummary?> = flowOf(null)
    override suspend fun addSource(input: SourceInput): SourceMutationResult = error("unused")
    override suspend fun setActiveSource(sourceId: SourceId): Boolean = error("unused")
    override suspend fun refreshSource(sourceId: SourceId): SourceRefreshResult = error("unused")
    override suspend fun removeSource(sourceId: SourceId): Boolean {
        removeCalls += 1
        return removeResult
    }
}
