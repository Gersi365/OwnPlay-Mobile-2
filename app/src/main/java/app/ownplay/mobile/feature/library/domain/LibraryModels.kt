package app.ownplay.mobile.feature.library.domain

import app.ownplay.mobile.playback.domain.PlaybackStart
import app.ownplay.mobile.playback.domain.PlaybackStreamFormat
import kotlinx.coroutines.flow.Flow

enum class LibraryMediaKind {
    MOVIE,
    EPISODE,
}

enum class LibraryStartMode {
    RESUME,
    BEGINNING,
}

data class LibraryMovie(
    val movieId: String,
    val sourceId: String,
    val name: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val rating: String?,
    val providerOrder: Int,
    val resumePositionMs: Long?,
    val durationMs: Long?,
)

data class LibrarySeries(
    val seriesId: String,
    val sourceId: String,
    val name: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val description: String?,
    val rating: String?,
    val providerOrder: Int,
)

data class LibraryEpisode(
    val episodeId: String,
    val seriesId: String,
    val sourceId: String,
    val seriesName: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val durationMs: Long?,
    val resumePositionMs: Long?,
)

data class ContinueWatchingItem(
    val sourceId: String,
    val contentId: String,
    val mediaKind: LibraryMediaKind,
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

data class LibraryDownloadedMedia(
    val downloadId: String,
    val sourceId: String,
    val mediaKind: LibraryMediaKind,
    val contentId: String,
    val title: String,
    val createdAt: Long,
)

data class LibraryCatalog(
    val activeSourceId: String? = null,
    val activeSourceName: String? = null,
    val continueWatching: List<ContinueWatchingItem> = emptyList(),
    val movies: List<LibraryMovie> = emptyList(),
    val series: List<LibrarySeries> = emptyList(),
    val downloadedMedia: List<LibraryDownloadedMedia> = emptyList(),
)

data class LibrarySeriesDetail(
    val series: LibrarySeries,
    val episodes: List<LibraryEpisode>,
)

sealed interface LibrarySeriesDetailResult {
    data class Success(
        val detail: LibrarySeriesDetail,
        val refreshWarning: String? = null,
    ) : LibrarySeriesDetailResult

    data class Failure(
        val code: String,
        val safeMessage: String,
    ) : LibrarySeriesDetailResult
}

class ResolvedLibraryPlayback(
    val sourceId: String,
    val contentId: String,
    val mediaKind: LibraryMediaKind,
    val title: String,
    val subtitle: String?,
    val uri: String,
    val streamFormat: PlaybackStreamFormat,
    val start: PlaybackStart,
    val knownDurationMs: Long?,
) {
    override fun toString(): String =
        "ResolvedLibraryPlayback(sourceId=$sourceId, contentId=$contentId, mediaKind=$mediaKind, uri=<redacted>, streamFormat=$streamFormat, start=$start)"
}

sealed interface LibraryPlaybackResolution {
    data class Success(val value: ResolvedLibraryPlayback) : LibraryPlaybackResolution

    data class Failure(
        val code: String,
        val safeMessage: String,
    ) : LibraryPlaybackResolution
}

data class PlaybackProgressUpdate(
    val sourceId: String,
    val mediaKind: LibraryMediaKind,
    val contentId: String,
    val positionMs: Long,
    val durationMs: Long,
    val ended: Boolean,
)

interface LibraryRepository {
    fun observeCatalog(): Flow<LibraryCatalog>

    suspend fun loadSeriesDetail(seriesId: String): LibrarySeriesDetailResult

    suspend fun resolveMoviePlayback(
        movieId: String,
        startMode: LibraryStartMode,
    ): LibraryPlaybackResolution

    suspend fun resolveEpisodePlayback(
        episodeId: String,
        startMode: LibraryStartMode,
    ): LibraryPlaybackResolution

    suspend fun saveProgress(update: PlaybackProgressUpdate)
}
