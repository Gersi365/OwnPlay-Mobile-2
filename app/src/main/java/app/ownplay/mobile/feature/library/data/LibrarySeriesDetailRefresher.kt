package app.ownplay.mobile.feature.library.data

import app.ownplay.mobile.data.db.EpisodeEntity
import app.ownplay.mobile.data.db.LibraryDao
import app.ownplay.mobile.data.db.SourceDao
import app.ownplay.mobile.data.security.CredentialStore
import app.ownplay.mobile.data.security.SourceSecret
import app.ownplay.mobile.feature.library.domain.LibraryDetailRefreshResult
import app.ownplay.mobile.sources.data.StableIdentity
import app.ownplay.mobile.sources.data.xtream.XtreamClient
import app.ownplay.mobile.sources.data.xtream.XtreamConnection
import app.ownplay.mobile.sources.data.xtream.XtreamSeriesEpisode
import app.ownplay.mobile.sources.domain.SourceId
import app.ownplay.mobile.sources.domain.SourceType
import kotlinx.coroutines.CancellationException

internal interface LibrarySeriesDetailRefresher {
    suspend fun refresh(
        sourceId: SourceId,
        seriesId: String,
    ): LibraryDetailRefreshResult
}

internal class SourceBackedLibrarySeriesDetailRefresher(
    private val sourceDao: SourceDao,
    private val libraryDao: LibraryDao,
    private val credentialStore: CredentialStore,
    private val xtreamClient: XtreamClient,
) : LibrarySeriesDetailRefresher {
    override suspend fun refresh(
        sourceId: SourceId,
        seriesId: String,
    ): LibraryDetailRefreshResult {
        if (seriesId.isBlank()) return LibraryDetailRefreshResult.UNAVAILABLE
        val source = sourceDao.get(sourceId.value) ?: return LibraryDetailRefreshResult.UNAVAILABLE
        if (!source.enabled) return LibraryDetailRefreshResult.UNAVAILABLE
        val series = libraryDao.getAvailableSeries(sourceId.value, seriesId)
            ?: return LibraryDetailRefreshResult.UNAVAILABLE

        val sourceType = runCatching { SourceType.valueOf(source.type) }.getOrNull()
            ?: return LibraryDetailRefreshResult.FAILED
        if (sourceType != SourceType.XTREAM) {
            return LibraryDetailRefreshResult.UNSUPPORTED_SOURCE
        }

        val secret = credentialStore.get(sourceId) as? SourceSecret.Xtream
            ?: return LibraryDetailRefreshResult.FAILED

        return try {
            val detail = xtreamClient.seriesInfo(
                connection = XtreamConnection(
                    baseUrl = source.baseLocator,
                    username = secret.username,
                    password = secret.password,
                ),
                seriesId = series.providerSeriesId,
            )
            val rows = LibrarySeriesEpisodeCacheMapper.rows(
                sourceId = sourceId,
                seriesId = series.seriesId,
                generation = series.lastSeenGeneration,
                episodes = detail.episodes,
            )
            libraryDao.reconcileSeriesEpisodes(series.seriesId, rows)
            LibraryDetailRefreshResult.REFRESHED
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            LibraryDetailRefreshResult.FAILED
        }
    }
}

internal object LibrarySeriesEpisodeCacheMapper {
    fun rows(
        sourceId: SourceId,
        seriesId: String,
        generation: Long,
        episodes: List<XtreamSeriesEpisode>,
    ): List<EpisodeEntity> = episodes.map { episode ->
        EpisodeEntity(
            episodeId = StableIdentity.xtreamEpisode(sourceId, episode.providerEpisodeId),
            seriesId = seriesId,
            seasonNumber = episode.seasonNumber,
            episodeNumber = episode.episodeNumber,
            providerEpisodeId = episode.providerEpisodeId,
            title = episode.title,
            durationMs = episode.durationMs,
            streamLocator = "xtream://episode/${episode.providerEpisodeId.trim()}",
            extension = episode.containerExtension,
            available = true,
            lastSeenGeneration = generation,
        )
    }
}
