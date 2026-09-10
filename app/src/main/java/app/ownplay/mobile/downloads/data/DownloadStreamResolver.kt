package app.ownplay.mobile.downloads.data

import app.ownplay.mobile.data.db.DownloadEntity
import app.ownplay.mobile.data.db.LibraryDao
import app.ownplay.mobile.data.db.SourceDao
import app.ownplay.mobile.data.security.CredentialStore
import app.ownplay.mobile.feature.library.data.LibraryPlaybackLocator
import app.ownplay.mobile.feature.library.domain.LibraryMediaKind
import app.ownplay.mobile.playback.domain.PlaybackStreamFormat
import app.ownplay.mobile.sources.domain.SourceCredential
import app.ownplay.mobile.sources.domain.SourceType
import java.util.Locale

internal class ResolvedDownloadSource(
    val uri: String,
    val streamFormat: PlaybackStreamFormat,
) {
    override fun toString(): String = "ResolvedDownloadSource(uri=<redacted>, streamFormat=$streamFormat)"
}

internal sealed interface DownloadSourceResolution {
    data class Success(val value: ResolvedDownloadSource) : DownloadSourceResolution

    data class Failure(
        val code: String,
        val safeMessage: String,
        val retryable: Boolean = false,
    ) : DownloadSourceResolution
}

internal class DownloadStreamResolver(
    private val sourceDao: SourceDao,
    private val libraryDao: LibraryDao,
    private val credentialStore: CredentialStore,
) {
    suspend fun resolve(download: DownloadEntity): DownloadSourceResolution {
        val mediaKind = runCatching {
            LibraryMediaKind.valueOf(download.mediaKind.uppercase(Locale.US))
        }.getOrNull() ?: return failure("INVALID_MEDIA_KIND", "This item cannot be downloaded.")

        val source = sourceDao.get(download.sourceId)
            ?: return failure("SOURCE_NOT_FOUND", "The source for this download no longer exists.")
        if (!source.enabled || source.type != SourceType.XTREAM.name) {
            return failure("SOURCE_UNAVAILABLE", "The source cannot currently provide this download.")
        }
        val credential = credentialStore.get(download.sourceId) as? SourceCredential.Xtream
            ?: return failure("CREDENTIAL_MISSING", "Source credentials are unavailable.")

        val uri = when (mediaKind) {
            LibraryMediaKind.MOVIE -> {
                val movie = libraryDao.getMovie(download.contentId)
                    ?: return failure("MOVIE_NOT_FOUND", "The movie metadata is no longer available.")
                if (movie.sourceId != download.sourceId) {
                    return failure("SOURCE_MISMATCH", "The movie source does not match this download.")
                }
                LibraryPlaybackLocator.movieUri(
                    baseUrl = source.baseLocator,
                    credential = credential,
                    providerStreamId = movie.providerStreamId,
                    extension = movie.extension,
                )
            }

            LibraryMediaKind.EPISODE -> {
                val episode = libraryDao.getEpisode(download.contentId)
                    ?: return failure("EPISODE_NOT_FOUND", "The episode metadata is no longer available.")
                if (episode.sourceId != download.sourceId) {
                    return failure("SOURCE_MISMATCH", "The episode source does not match this download.")
                }
                LibraryPlaybackLocator.episodeUri(
                    baseUrl = source.baseLocator,
                    credential = credential,
                    providerEpisodeId = episode.providerEpisodeId,
                    extension = episode.extension,
                )
            }
        }

        val format = LibraryPlaybackLocator.streamFormatFor(uri)
        if (format == PlaybackStreamFormat.HLS) {
            return failure(
                code = "HLS_OFFLINE_UNSUPPORTED",
                message = "Segmented HLS media cannot be saved for offline playback yet.",
            )
        }
        return DownloadSourceResolution.Success(
            ResolvedDownloadSource(uri = uri, streamFormat = format),
        )
    }

    private fun failure(
        code: String,
        message: String,
        retryable: Boolean = false,
    ): DownloadSourceResolution.Failure = DownloadSourceResolution.Failure(
        code = code,
        safeMessage = message,
        retryable = retryable,
    )
}
