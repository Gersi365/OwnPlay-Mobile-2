package app.ownplay.mobile.feature.library.data

import app.ownplay.mobile.data.db.LibraryDao
import app.ownplay.mobile.feature.library.domain.LibraryMediaKind
import app.ownplay.mobile.feature.library.domain.LibraryMediaMetadata

/**
 * Builds a durable download-metadata snapshot from the catalog already stored in Room.
 *
 * This deliberately reads source catalog rows even when the provider item is no longer
 * currently available so an existing download can retain its Library identity and artwork.
 */
class LibraryDownloadMetadataResolver(
    private val libraryDao: LibraryDao,
) {
    suspend fun resolve(
        sourceId: String,
        mediaKind: LibraryMediaKind,
        contentId: String,
    ): LibraryMediaMetadata? {
        if (sourceId.isBlank() || contentId.isBlank()) return null
        return when (mediaKind) {
            LibraryMediaKind.MOVIE -> resolveMovie(sourceId, contentId)
            LibraryMediaKind.EPISODE -> resolveEpisode(sourceId, contentId)
        }
    }

    private suspend fun resolveMovie(sourceId: String, movieId: String): LibraryMediaMetadata? {
        val movie = libraryDao.getMovie(movieId)?.takeIf { it.sourceId == sourceId } ?: return null
        val progressDuration = libraryDao.getProgress(sourceId, LibraryMediaKind.MOVIE.name, movieId)
            ?.durationMs
            ?.takeIf { it > 0L }
        return LibraryMediaMetadata(
            title = movie.name,
            posterUrl = movie.posterUrl,
            backdropUrl = movie.backdropUrl,
            durationMs = progressDuration,
            rating = movie.rating,
        )
    }

    private suspend fun resolveEpisode(sourceId: String, episodeId: String): LibraryMediaMetadata? {
        val episode = libraryDao.getEpisode(episodeId)?.takeIf { it.sourceId == sourceId } ?: return null
        val series = libraryDao.getSeries(episode.seriesId)?.takeIf { it.sourceId == sourceId }
        val duration = episode.progressDurationMs
            ?.takeIf { it > 0L }
            ?: episode.durationMs?.takeIf { it > 0L }
        return LibraryMediaMetadata(
            title = episode.title.ifBlank { "Episode ${episode.episodeNumber}" },
            posterUrl = series?.posterUrl,
            backdropUrl = series?.backdropUrl,
            plot = series?.description,
            durationMs = duration,
            rating = series?.rating,
        )
    }
}
