package app.ownplay.mobile.core

import app.ownplay.mobile.downloads.domain.DownloadCleanupTarget
import app.ownplay.mobile.sources.domain.NewSource
import app.ownplay.mobile.sources.domain.RefreshStatus
import app.ownplay.mobile.sources.domain.RefreshSummary
import app.ownplay.mobile.sources.domain.Source
import app.ownplay.mobile.sources.domain.SourceRepository
import app.ownplay.mobile.sources.domain.SourceResult
import app.ownplay.mobile.sources.domain.SourceUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinatedSourceRepositoryTest {
    @Test
    fun successfulRemovalCleansCapturedDownloadsAfterSourceDelete() = runBlocking {
        val events = mutableListOf<String>()
        val delegate = FakeSourceRepository(SourceResult.Success(Unit), events)
        val repository = CoordinatedSourceRepository(
            delegate = delegate,
            captureSourceDownloads = { sourceId ->
                events += "capture:$sourceId"
                listOf(target("download-a"), target("download-b", "content://download-b"))
            },
            cleanupSourceDownloads = { downloadIds ->
                events += "cleanup:${downloadIds.joinToString(",") { it.downloadId }}"
                true
            },
        )

        val result = repository.removeSource("source-a")

        assertTrue(result is SourceResult.Success)
        assertEquals(
            listOf(
                "capture:source-a",
                "remove:source-a",
                "cleanup:download-a,download-b",
            ),
            events,
        )
    }

    @Test
    fun failedSourceDeleteDoesNotCleanCapturedDownloads() = runBlocking {
        val events = mutableListOf<String>()
        val delegate = FakeSourceRepository(
            SourceResult.Failure(app.ownplay.mobile.sources.domain.SourceError("DELETE_FAILED", "Delete failed.")),
            events,
        )
        val repository = CoordinatedSourceRepository(
            delegate = delegate,
            captureSourceDownloads = { sourceId ->
                events += "capture:$sourceId"
                listOf(target("download-a"))
            },
            cleanupSourceDownloads = { downloadIds ->
                events += "cleanup:${downloadIds.joinToString(",") { it.downloadId }}"
                true
            },
        )

        val result = repository.removeSource("source-a")

        assertTrue(result is SourceResult.Failure)
        assertEquals(listOf("capture:source-a", "remove:source-a"), events)
    }

    @Test
    fun captureFailurePreventsSourceDelete() = runBlocking {
        val events = mutableListOf<String>()
        val delegate = FakeSourceRepository(SourceResult.Success(Unit), events)
        val repository = CoordinatedSourceRepository(
            delegate = delegate,
            captureSourceDownloads = { sourceId ->
                events += "capture:$sourceId"
                error("snapshot failed")
            },
            cleanupSourceDownloads = { downloadIds ->
                events += "cleanup:${downloadIds.joinToString(",") { it.downloadId }}"
                true
            },
        )

        val result = repository.removeSource("source-a")

        assertTrue(result is SourceResult.Failure)
        val failure = result as SourceResult.Failure
        assertEquals("SOURCE_REMOVE_PREPARE_FAILED", failure.error.code)
        assertEquals(listOf("capture:source-a"), events)
    }

    @Test
    fun cleanupFailureReportsSourceRemovedWithDownloadCleanupWarning() = runBlocking {
        val events = mutableListOf<String>()
        val delegate = FakeSourceRepository(SourceResult.Success(Unit), events)
        val repository = CoordinatedSourceRepository(
            delegate = delegate,
            captureSourceDownloads = { sourceId ->
                events += "capture:$sourceId"
                listOf(target("download-a", "content://download-a"))
            },
            cleanupSourceDownloads = { downloadIds ->
                events += "cleanup:${downloadIds.joinToString(",") { it.downloadId }}"
                false
            },
        )

        val result = repository.removeSource("source-a")

        assertTrue(result is SourceResult.Failure)
        val failure = result as SourceResult.Failure
        assertEquals("SOURCE_REMOVED_CLEANUP_FAILED", failure.error.code)
        assertEquals(
            listOf("capture:source-a", "remove:source-a", "cleanup:download-a"),
            events,
        )
    }

    @Test
    fun successfulRefreshRunsDerivedDiscoveryAfterProviderCommit() = runBlocking {
        val events = mutableListOf<String>()
        val summary = refreshSummary(RefreshStatus.SUCCESS, generation = 7)
        val delegate = FakeSourceRepository(
            removalResult = SourceResult.Success(Unit),
            events = events,
            refreshResult = SourceResult.Success(summary),
        )
        val repository = CoordinatedSourceRepository(
            delegate = delegate,
            captureSourceDownloads = { emptyList() },
            cleanupSourceDownloads = { true },
            afterSuccessfulRefresh = { refreshed -> events += "discover:${refreshed.sourceId}:${refreshed.generation}" },
        )

        val result = repository.refresh("source-a")

        assertTrue(result is SourceResult.Success)
        assertEquals(listOf("refresh:source-a", "discover:source-a:7"), events)
    }

    @Test
    fun partialRefreshDoesNotReplaceDerivedDiscovery() = runBlocking {
        val events = mutableListOf<String>()
        val delegate = FakeSourceRepository(
            removalResult = SourceResult.Success(Unit),
            events = events,
            refreshResult = SourceResult.Success(refreshSummary(RefreshStatus.PARTIAL, generation = 8)),
        )
        val repository = CoordinatedSourceRepository(
            delegate = delegate,
            captureSourceDownloads = { emptyList() },
            cleanupSourceDownloads = { true },
            afterSuccessfulRefresh = { events += "discover" },
        )

        val result = repository.refresh("source-a")

        assertTrue(result is SourceResult.Success)
        assertEquals(listOf("refresh:source-a"), events)
    }

    @Test
    fun discoveryFailureDoesNotRewriteCommittedProviderRefreshResult() = runBlocking {
        val events = mutableListOf<String>()
        val delegate = FakeSourceRepository(
            removalResult = SourceResult.Success(Unit),
            events = events,
            refreshResult = SourceResult.Success(refreshSummary(RefreshStatus.SUCCESS, generation = 9)),
        )
        val repository = CoordinatedSourceRepository(
            delegate = delegate,
            captureSourceDownloads = { emptyList() },
            cleanupSourceDownloads = { true },
            afterSuccessfulRefresh = { error("local discovery failed") },
        )

        val result = repository.refresh("source-a")

        assertTrue(result is SourceResult.Success)
        assertEquals(9L, (result as SourceResult.Success).value.generation)
        assertEquals(listOf("refresh:source-a"), events)
    }

    private fun target(downloadId: String, localReference: String? = null) =
        DownloadCleanupTarget(downloadId = downloadId, localReference = localReference)

    private fun refreshSummary(status: RefreshStatus, generation: Long) = RefreshSummary(
        sourceId = "source-a",
        status = status,
        generation = generation,
        liveCategories = 4,
        liveChannels = 20,
        vodCategories = 0,
        movies = 0,
        seriesCategories = 0,
        series = 0,
        warnings = emptyList(),
    )

    private class FakeSourceRepository(
        private val removalResult: SourceResult<Unit>,
        private val events: MutableList<String>,
        private val refreshResult: SourceResult<RefreshSummary>? = null,
    ) : SourceRepository {
        override fun observeSources(): Flow<List<Source>> = flowOf(emptyList())

        override fun observeActiveSource(): Flow<Source?> = flowOf(null)

        override suspend fun addSource(input: NewSource): SourceResult<Source> = error("Not used")

        override suspend fun updateSource(input: SourceUpdate): SourceResult<Unit> = error("Not used")

        override suspend fun removeSource(sourceId: String): SourceResult<Unit> {
            events += "remove:$sourceId"
            return removalResult
        }

        override suspend fun selectSource(sourceId: String?): SourceResult<Unit> = error("Not used")

        override suspend fun refresh(sourceId: String): SourceResult<RefreshSummary> {
            events += "refresh:$sourceId"
            return requireNotNull(refreshResult) { "Refresh result not configured" }
        }
    }
}
