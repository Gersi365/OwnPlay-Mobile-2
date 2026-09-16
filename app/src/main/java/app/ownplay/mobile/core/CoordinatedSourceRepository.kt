package app.ownplay.mobile.core

import app.ownplay.mobile.downloads.domain.DownloadCleanupTarget
import app.ownplay.mobile.sources.domain.NewSource
import app.ownplay.mobile.sources.domain.RefreshStatus
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
    private val captureSourceDownloads: suspend (String) -> List<DownloadCleanupTarget>,
    private val cleanupSourceDownloads: suspend (List<DownloadCleanupTarget>) -> Boolean,
    private val afterSuccessfulRefresh: suspend (RefreshSummary) -> Unit = {},
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
            val cleanupComplete = withContext(NonCancellable) {
                cleanupSourceDownloads(downloadIds)
            }
            if (!cleanupComplete) {
                return SourceResult.Failure(
                    SourceError(
                        code = "SOURCE_REMOVED_CLEANUP_FAILED",
                        safeMessage = "The source was removed, but some downloaded files could not be deleted from device storage.",
                    ),
                )
            }
        }
        return result
    }

    override suspend fun selectSource(sourceId: String?): SourceResult<Unit> = delegate.selectSource(sourceId)

    override suspend fun refresh(sourceId: String): SourceResult<RefreshSummary> {
        val result = delegate.refresh(sourceId)
        if (result is SourceResult.Success && result.value.status == RefreshStatus.SUCCESS) {
            try {
                afterSuccessfulRefresh(result.value)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Provider refresh is already committed. Keep the prior derived OwnPlay view on local discovery failure.
            }
        }
        return result
    }
}
