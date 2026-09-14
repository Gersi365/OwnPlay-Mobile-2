package app.ownplay.mobile.core

import app.ownplay.mobile.sources.domain.NewSource
import app.ownplay.mobile.sources.domain.RefreshSummary
import app.ownplay.mobile.sources.domain.Source
import app.ownplay.mobile.sources.domain.SourceError
import app.ownplay.mobile.sources.domain.SourceRepository
import app.ownplay.mobile.sources.domain.SourceResult
import app.ownplay.mobile.sources.domain.SourceUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

internal class CoordinatedSourceRepository(
    private val delegate: SourceRepository,
    private val captureSourceDownloads: suspend (String) -> List<String>,
    private val cleanupSourceDownloads: suspend (List<String>) -> Unit,
) : SourceRepository {
    override fun observeSources(): Flow<List<Source>> = delegate.observeSources()

    override fun observeActiveSource(): Flow<Source?> = delegate.observeActiveSource()

    override suspend fun addSource(input: NewSource): SourceResult<Source> = delegate.addSource(input)

    override suspend fun updateSource(input: SourceUpdate): SourceResult<Unit> = delegate.updateSource(input)

    override suspend fun removeSource(sourceId: String): SourceResult<Unit> {
        val downloadIds = try {
            captureSourceDownloads(sourceId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return SourceResult.Failure(
                SourceError(
                    code = "SOURCE_REMOVE_PREPARE_FAILED",
                    safeMessage = "Source removal could not prepare managed downloads.",
                ),
            )
        }

        val result = delegate.removeSource(sourceId)
        if (result is SourceResult.Success) {
            withContext(NonCancellable) {
                cleanupSourceDownloads(downloadIds)
            }
        }
        return result
    }

    override suspend fun selectSource(sourceId: String?): SourceResult<Unit> = delegate.selectSource(sourceId)

    override suspend fun refresh(sourceId: String): SourceResult<RefreshSummary> = delegate.refresh(sourceId)
}
