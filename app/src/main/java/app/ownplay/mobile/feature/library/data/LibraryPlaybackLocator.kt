package app.ownplay.mobile.feature.library.data

import app.ownplay.mobile.data.db.LibraryDao
import app.ownplay.mobile.data.db.SourceDao
import app.ownplay.mobile.data.security.CredentialStore
import app.ownplay.mobile.data.security.SourceSecret
import app.ownplay.mobile.feature.playback.domain.LibraryPlaybackMediaResolver
import app.ownplay.mobile.feature.playback.domain.PlaybackTarget
import app.ownplay.mobile.feature.playback.domain.PreparedPlaybackMedia
import app.ownplay.mobile.sources.data.xtream.XtreamUrlBuilder
import app.ownplay.mobile.sources.domain.SourceType
import kotlinx.coroutines.CancellationException

internal interface LibraryPlaybackLocator : LibraryPlaybackMediaResolver

internal class SourceBackedLibraryPlaybackLocator(
    private val sourceDao: SourceDao,
    private val libraryDao: LibraryDao,
    private val credentialStore: CredentialStore,
) : LibraryPlaybackLocator {
    override suspend fun resolve(target: PlaybackTarget.Library): PreparedPlaybackMedia? {
        val source = sourceDao.get(target.sourceId.value) ?: return null
        if (!source.enabled) return null

        val sourceType = runCatching { SourceType.valueOf(source.type) }.getOrNull() ?: return null
        if (sourceType != SourceType.XTREAM) return null

        val credential = credentialStore.get(target.sourceId) as? SourceSecret.Xtream ?: return null

        return try {
            when (target) {
                is PlaybackTarget.Movie -> {
                    val movie = libraryDao.getAvailableMovie(
                        sourceId = target.sourceId.value,
                        movieId = target.movieId,
                    ) ?: return null
                    val extension = movie.extension?.takeIf(String::isNotBlank) ?: return null
                    LibraryPlaybackUriFactory.movie(
                        baseUrl = source.baseLocator,
                        username = credential.username,
                        password = credential.password,
                        streamId = movie.providerStreamId,
                        extension = extension,
                    )
                }

                is PlaybackTarget.Episode -> {
                    val episode = libraryDao.getAvailableEpisode(
                        sourceId = target.sourceId.value,
                        episodeId = target.episodeId,
                    ) ?: return null
                    val extension = episode.extension?.takeIf(String::isNotBlank) ?: return null
                    LibraryPlaybackUriFactory.episode(
                        baseUrl = source.baseLocator,
                        username = credential.username,
                        password = credential.password,
                        episodeId = episode.providerEpisodeId,
                        extension = extension,
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }
}

internal object LibraryPlaybackUriFactory {
    fun movie(
        baseUrl: String,
        username: String,
        password: String,
        streamId: String,
        extension: String,
    ): PreparedPlaybackMedia? = runCatching {
        PreparedPlaybackMedia(
            uri = XtreamUrlBuilder.movieStream(
                baseUrl = baseUrl,
                username = username,
                password = password,
                streamId = streamId,
                extension = extension,
            ),
            mimeType = mimeTypeForExtension(extension),
        )
    }.getOrNull()

    fun episode(
        baseUrl: String,
        username: String,
        password: String,
        episodeId: String,
        extension: String,
    ): PreparedPlaybackMedia? = runCatching {
        PreparedPlaybackMedia(
            uri = XtreamUrlBuilder.seriesStream(
                baseUrl = baseUrl,
                username = username,
                password = password,
                episodeId = episodeId,
                extension = extension,
            ),
            mimeType = mimeTypeForExtension(extension),
        )
    }.getOrNull()

    private fun mimeTypeForExtension(extension: String): String? =
        if (extension.trim().removePrefix(".").equals("m3u8", ignoreCase = true)) {
            HLS_MIME_TYPE
        } else {
            null
        }

    private const val HLS_MIME_TYPE = "application/x-mpegURL"
}
