package app.ownplay.mobile.core

import app.ownplay.mobile.sources.domain.NewSource
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
                listOf("download-a", "download-b")
            },
            cleanupSourceDownloads = { downloadIds ->
                events += "cleanup:${downloadIds.joinToString(",")}"
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
                listOf("download-a")
            },
            cleanupSourceDownloads = { downloadIds ->
                events += "cleanup:${downloadIds.joinToString(",")}"
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
                events += "cleanup:${downloadIds.joinToString(",")}"
            },
        )

        val result = repository.removeSource("source-a")

        assertTrue(result is SourceResult.Failure)
        val failure = result as SourceResult.Failure
        assertEquals("SOURCE_REMOVE_PREPARE_FAILED", failure.error.code)
        assertEquals(listOf("capture:source-a"), events)
    }

    private class FakeSourceRepository(
        private val removalResult: SourceResult<Unit>,
        private val events: MutableList<String>,
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

        override suspend fun refresh(sourceId: String): SourceResult<RefreshSummary> = error("Not used")
    }
}
