package app.ownplay.mobile.downloads.domain

import app.ownplay.mobile.feature.library.domain.LibraryMediaKind
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
) {
    val progressFraction: Float?
        get() = totalBytes
            ?.takeIf { total -> total > 0L }
            ?.let { total -> (bytesDownloaded.toDouble() / total.toDouble()).coerceIn(0.0, 1.0).toFloat() }
}

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

    suspend fun executeWork(downloadId: String): DownloadWorkResult
}
