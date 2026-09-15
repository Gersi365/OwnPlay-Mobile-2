package app.ownplay.mobile.downloads.domain

import app.ownplay.mobile.feature.library.domain.LibraryMediaKind
import app.ownplay.mobile.feature.library.domain.LibraryMediaMetadata
import app.ownplay.mobile.feature.library.domain.LibraryPlaybackResolution
import app.ownplay.mobile.feature.library.domain.LibraryStartMode
import kotlinx.coroutines.flow.Flow

enum class DownloadState {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    FAILED,
    COMPLETED,
}

enum class DownloadAction {
    DOWNLOAD,
    PAUSE,
    RESUME,
    RETRY,
    REMOVE,
    PLAY_OFFLINE,
    RESUME_OFFLINE,
}

enum class DownloadTransition {
    CREATE_QUEUED,
    MARK_PAUSED,
    MARK_QUEUED,
    REMOVE,
    PLAY_OFFLINE,
}

enum class OfflineAvailability {
    AVAILABLE,
    MISSING,
    INCOMPLETE,
}

data class DownloadItem(
    val downloadId: String,
    val sourceId: String,
    val mediaKind: LibraryMediaKind,
    val contentId: String,
    val title: String,
    val state: DownloadState,
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val resumePositionMs: Long?,
    val metadata: LibraryMediaMetadata? = null,
) {
    val progressFraction: Float?
        get() = totalBytes
            ?.takeIf { total -> total > 0L }
            ?.let { total -> (bytesDownloaded.toDouble() / total.toDouble()).coerceIn(0.0, 1.0).toFloat() }
}

internal data class DownloadCleanupTarget(
    val downloadId: String,
    val localReference: String?,
)

sealed interface DownloadOperationResult {
    data class Success(val item: DownloadItem? = null) : DownloadOperationResult

    data class Failure(
        val code: String,
        val safeMessage: String,
    ) : DownloadOperationResult
}

enum class DownloadWorkResult {
    SUCCESS,
    RETRY,
    FAILURE,
    NO_OP,
}

interface DownloadRepository {
    fun observeDownloads(): Flow<List<DownloadItem>>

    suspend fun requestDownload(
        sourceId: String,
        mediaKind: LibraryMediaKind,
        contentId: String,
        title: String,
    ): DownloadOperationResult

    suspend fun pause(downloadId: String): DownloadOperationResult

    suspend fun resume(downloadId: String): DownloadOperationResult

    suspend fun retry(downloadId: String): DownloadOperationResult

    suspend fun remove(downloadId: String): DownloadOperationResult

    suspend fun resolveOfflinePlayback(
        downloadId: String,
        startMode: LibraryStartMode,
    ): LibraryPlaybackResolution

    suspend fun saveMetadata(downloadId: String, metadata: LibraryMediaMetadata) = Unit

    suspend fun offlineAvailability(downloadId: String): OfflineAvailability = OfflineAvailability.INCOMPLETE

    suspend fun redownload(downloadId: String): DownloadOperationResult =
        DownloadOperationResult.Failure("REDOWNLOAD_UNSUPPORTED", "This download cannot be restarted.")

    suspend fun executeWork(downloadId: String): DownloadWorkResult
}
