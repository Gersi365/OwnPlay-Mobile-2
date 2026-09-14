package app.ownplay.mobile.feature.library.data

import androidx.room.withTransaction
import app.ownplay.mobile.data.db.CatalogDao
import app.ownplay.mobile.data.db.DownloadEntity
import app.ownplay.mobile.data.db.EpisodeEntity
import app.ownplay.mobile.data.db.EpisodeLibraryView
import app.ownplay.mobile.data.db.LibraryDao
import app.ownplay.mobile.data.db.MediaFavoriteEntity
import app.ownplay.mobile.data.db.MovieEntity
import app.ownplay.mobile.data.db.OwnPlayDatabase
import app.ownplay.mobile.data.db.PlaybackProgressEntity
import app.ownplay.mobile.data.db.ProviderCategoryEntity
import app.ownplay.mobile.data.db.SeriesEntity
import app.ownplay.mobile.data.db.SourceDao
import app.ownplay.mobile.data.db.SourceEntity
import app.ownplay.mobile.data.security.CredentialStore
import app.ownplay.mobile.feature.library.domain.ContinueWatchingItem
import app.ownplay.mobile.feature.library.domain.LibraryCatalog
import app.ownplay.mobile.feature.library.domain.LibraryCategory
import app.ownplay.mobile.feature.library.domain.LibraryCompletionPolicy
import app.ownplay.mobile.feature.library.domain.LibraryDownloadedMedia
import app.ownplay.mobile.feature.library.domain.LibraryEpisode
import app.ownplay.mobile.feature.library.domain.LibraryMediaKind
import app.ownplay.mobile.feature.library.domain.LibraryMediaMetadata
import app.ownplay.mobile.feature.library.domain.LibraryMovie
import app.ownplay.mobile.feature.library.domain.LibraryMovieDetail
import app.ownplay.mobile.feature.library.domain.LibraryMovieDetailResult
import app.ownplay.mobile.feature.library.domain.LibraryOrderingPolicy
import app.ownplay.mobile.feature.library.domain.LibraryPlaybackResolution
import app.ownplay.mobile.feature.library.domain.LibraryRepository
import app.ownplay.mobile.feature.library.domain.LibrarySeries
import app.ownplay.mobile.feature.library.domain.LibrarySeriesDetail
import app.ownplay.mobile.feature.library.domain.LibrarySeriesDetailResult
import app.ownplay.mobile.feature.library.domain.LibraryStartMode
import app.ownplay.mobile.feature.library.domain.LibraryStartPolicy
import app.ownplay.mobile.feature.library.domain.PlaybackProgressUpdate
import app.ownplay.mobile.feature.library.domain.ResolvedLibraryPlayback
import app.ownplay.mobile.sources.data.StableIdentity
import app.ownplay.mobile.sources.data.xtream.XtreamClient
import app.ownplay.mobile.sources.data.xtream.XtreamMediaInfo
import app.ownplay.mobile.sources.data.xtream.XtreamResult
import app.ownplay.mobile.sources.domain.Source
import app.ownplay.mobile.sources.domain.SourceCredential
import app.ownplay.mobile.sources.domain.SourceRepository
import app.ownplay.mobile.sources.domain.SourceType
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class LibraryRepositoryImpl(
    private val database: OwnPlayDatabase,
    private val sourceRepository: SourceRepository,
    private val sourceDao: SourceDao,
    private val catalogDao: CatalogDao,
    private val libraryDao: LibraryDao,
    private val credentialStore: CredentialStore,
    private val xtreamClient: XtreamClient,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : LibraryRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCatalog(): Flow<LibraryCatalog> =
        sourceRepository.observeActiveSource().flatMapLatest { source ->
            if (source == null) flowOf(LibraryCatalog()) else observeRows(source.sourceId).map { it.toCatalog(source) }
        }

    override suspend fun loadMovieDetail(movieId: String): LibraryMovieDetailResult {
        if (movieId.isBlank()) return movieFailure("INVALID_MOVIE", "This movie cannot be opened.")
        return try {
            val movie = libraryDao.getMovie(movieId)
                ?: return movieFailure("MOVIE_NOT_FOUND", "This movie is no longer available.")
            if (!movie.available) return movieFailure("MOVIE_UNAVAILABLE", "This movie is currently unavailable.")
            val progress = libraryDao.getProgress(movie.sourceId, LibraryMediaKind.MOVIE.name, movie.movieId)
            val favorite = libraryDao.getMediaFavorite(movie.sourceId, FAVORITE_KIND_MOVIE, movie.movieId) != null
            val movieModel = movie.toDomain(progress = progress, favorite = favorite)
            val cached = LibraryMovieDetail(
                movie = movieModel,
                metadata = movie.baseMetadata(),
            )
            val source = sourceDao.get(movie.sourceId)
                ?: return LibraryMovieDetailResult.Success(cached, "Provider metadata is unavailable.")
            val credential = playableXtreamCredential(source)
                ?: return LibraryMovieDetailResult.Success(cached, "Provider metadata is unavailable.")
            when (
                val result = xtreamClient.vodInfo(
                    baseUrl = source.baseLocator,
                    credential = credential,
                    streamId = movie.providerStreamId,
                )
            ) {
                is XtreamResult.Failure -> LibraryMovieDetailResult.Success(
                    detail = cached,
                    refreshWarning = "Provider metadata could not be refreshed.",
                )
                is XtreamResult.Success -> LibraryMovieDetailResult.Success(
                    detail = cached.copy(
                        metadata = result.value.metadata.toLibraryMetadata(
                            fallbackTitle = movie.name,
                            fallbackPoster = movie.posterUrl,
                            fallbackBackdrop = movie.backdropUrl,
                            fallbackRating = movie.rating,
                        ),
                    ),
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            movieFailure("MOVIE_DETAIL_FAILED", "Movie details could not be prepared.")
        }
    }

    override suspend fun loadSeriesDetail(seriesId: String): LibrarySeriesDetailResult {
        if (seriesId.isBlank()) return seriesFailure("INVALID_SERIES", "This series cannot be opened.")
        return try {
            val series = libraryDao.getSeries(seriesId)
                ?: return seriesFailure("SERIES_NOT_FOUND", "This series is no longer available.")
            if (!series.available) return seriesFailure("SERIES_UNAVAILABLE", "This series is currently unavailable.")

            val cached = cachedSeriesDetail(series)
            val source = sourceDao.get(series.sourceId)
                ?: return cached.orFailure("SOURCE_NOT_FOUND", "The series source is no longer available.")
            if (!source.enabled) return cached.orFailure("SOURCE_DISABLED", "The series source is disabled.")
            if (source.type != SourceType.XTREAM.name) {
                return cached.orFailure("SERIES_SOURCE_UNSUPPORTED", "Episode refresh is not supported for this source type.")
            }
            val credential = credentialStore.get(source.sourceId) as? SourceCredential.Xtream
                ?: return cached.orFailure("CREDENTIAL_MISSING", "Source credentials are unavailable.")

            when (
                val result = xtreamClient.seriesInfo(
                    baseUrl = source.baseLocator,
                    credential = credential,
                    seriesId = series.providerSeriesId,
                )
            ) {
                is XtreamResult.Failure -> cached.orFailure(result.code, "Episodes could not be refreshed.")
                is XtreamResult.Success -> {
                    val rows = result.value.episodes.map { episode ->
                        EpisodeEntity(
                            episodeId = StableIdentity.xtreamContentId(series.sourceId, "episode", episode.episodeId),
                            seriesId = series.seriesId,
                            seasonNumber = episode.seasonNumber,
                            episodeNumber = episode.episodeNumber,
                            providerEpisodeId = episode.episodeId,
                            title = episode.title,
                            durationMs = episode.durationSeconds
                                ?.takeIf { seconds -> seconds >= 0L && seconds <= Long.MAX_VALUE / 1_000L }
                                ?.times(1_000L),
                            streamLocator = "xtream://series/${episode.episodeId}",
                            extension = episode.extension,
                            available = true,
                            lastSeenGeneration = series.lastSeenGeneration,
                        )
                    }
                    database.withTransaction {
                        if (result.warningCode == null) libraryDao.markEpisodesUnavailable(series.seriesId)
                        catalogDao.upsertEpisodes(rows)
                    }
                    LibrarySeriesDetailResult.Success(
                        refreshWarning = result.warningCode?.let { "Some episodes could not be refreshed; saved episodes were kept." },
                        detail = LibrarySeriesDetail(
                            series = series.toDomain(),
                            episodes = libraryDao.getEpisodesForSeries(series.seriesId).map { it.toDomain() },
                            metadata = result.value.metadata?.toLibraryMetadata(
                                fallbackTitle = series.name,
                                fallbackPoster = series.posterUrl,
                                fallbackBackdrop = series.backdropUrl,
                                fallbackRating = series.rating,
                                fallbackPlot = series.description,
                            ) ?: series.baseMetadata(),
                        ),
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            seriesFailure("SERIES_DETAIL_FAILED", "Series details could not be prepared.")
        }
    }

    override suspend fun resolveMoviePlayback(movieId: String, startMode: LibraryStartMode): LibraryPlaybackResolution {
        if (movieId.isBlank()) return playbackFailure("INVALID_MOVIE", "This movie cannot be opened.")
        return try {
            val movie = libraryDao.getMovie(movieId)
                ?: return playbackFailure("MOVIE_NOT_FOUND", "This movie is no longer available.")
            if (!movie.available) return playbackFailure("MOVIE_UNAVAILABLE", "This movie is currently unavailable.")
            val source = sourceDao.get(movie.sourceId)
                ?: return playbackFailure("SOURCE_NOT_FOUND", "The movie source is no longer available.")
            val credential = playableXtreamCredential(source)
                ?: return playbackFailure("SOURCE_UNAVAILABLE", "The movie source cannot be used for playback.")
            val progress = libraryDao.getProgress(movie.sourceId, LibraryMediaKind.MOVIE.name, movie.movieId)
                ?.takeIf { !it.completed && it.positionMs > 0L && it.durationMs > 0L }
            val uri = LibraryPlaybackLocator.movieUri(source.baseLocator, credential, movie.providerStreamId, movie.extension)
            LibraryPlaybackResolution.Success(
                ResolvedLibraryPlayback(
                    sourceId = movie.sourceId,
                    contentId = movie.movieId,
                    mediaKind = LibraryMediaKind.MOVIE,
                    title = movie.name,
                    subtitle = "Movie",
                    uri = uri,
                    streamFormat = LibraryPlaybackLocator.streamFormatFor(uri),
                    start = LibraryStartPolicy.resolve(startMode, progress?.positionMs),
                    knownDurationMs = progress?.durationMs,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            playbackFailure("MOVIE_PLAYBACK_FAILED", "The movie stream could not be prepared.")
        }
    }

    override suspend fun resolveEpisodePlayback(episodeId: String, startMode: LibraryStartMode): LibraryPlaybackResolution {
        if (episodeId.isBlank()) return playbackFailure("INVALID_EPISODE", "This episode cannot be opened.")
        return try {
            val episode = libraryDao.getEpisode(episodeId)
                ?: return playbackFailure("EPISODE_NOT_FOUND", "This episode is no longer available.")
            if (!episode.available) return playbackFailure("EPISODE_UNAVAILABLE", "This episode is currently unavailable.")
            val source = sourceDao.get(episode.sourceId)
                ?: return playbackFailure("SOURCE_NOT_FOUND", "The episode source is no longer available.")
            val credential = playableXtreamCredential(source)
                ?: return playbackFailure("SOURCE_UNAVAILABLE", "The episode source cannot be used for playback.")
            val resumePosition = episode.progressPositionMs?.takeIf { episode.progressCompleted != true && it > 0L }
            val knownDuration = episode.progressDurationMs?.takeIf { it > 0L } ?: episode.durationMs?.takeIf { it > 0L }
            val uri = LibraryPlaybackLocator.episodeUri(source.baseLocator, credential, episode.providerEpisodeId, episode.extension)
            LibraryPlaybackResolution.Success(
                ResolvedLibraryPlayback(
                    sourceId = episode.sourceId,
                    contentId = episode.episodeId,
                    mediaKind = LibraryMediaKind.EPISODE,
                    title = episode.title,
                    subtitle = "${episode.seriesName} • S${episode.seasonNumber} E${episode.episodeNumber}",
                    uri = uri,
                    streamFormat = LibraryPlaybackLocator.streamFormatFor(uri),
                    start = LibraryStartPolicy.resolve(startMode, resumePosition),
                    knownDurationMs = knownDuration,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            playbackFailure("EPISODE_PLAYBACK_FAILED", "The episode stream could not be prepared.")
        }
    }

    override suspend fun saveProgress(update: PlaybackProgressUpdate) {
        if (update.sourceId.isBlank() || update.contentId.isBlank() || update.durationMs <= 0L) return
        val boundedDuration = update.durationMs.coerceAtLeast(1L)
        val boundedPosition = update.positionMs.coerceIn(0L, boundedDuration)
        val completed = LibraryCompletionPolicy.isComplete(boundedPosition, boundedDuration, update.ended)
        try {
            libraryDao.upsertProgress(
                PlaybackProgressEntity(
                    sourceId = update.sourceId,
                    mediaKind = update.mediaKind.name,
                    contentId = update.contentId,
                    positionMs = if (completed) boundedDuration else boundedPosition,
                    durationMs = boundedDuration,
                    completed = completed,
                    updatedAt = nowMillis(),
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Progress persistence failure is non-fatal to playback and contains no user secret.
        }
    }

    override suspend fun clearProgress(sourceId: String, mediaKind: LibraryMediaKind, contentId: String) {
        if (sourceId.isBlank() || contentId.isBlank()) return
        val existing = libraryDao.getProgress(sourceId, mediaKind.name, contentId) ?: return
        libraryDao.upsertProgress(
            existing.copy(positionMs = 0L, completed = false, updatedAt = nowMillis()),
        )
    }

    override suspend fun markWatched(
        sourceId: String,
        mediaKind: LibraryMediaKind,
        contentId: String,
        durationMs: Long,
    ) {
        if (sourceId.isBlank() || contentId.isBlank() || durationMs <= 0L) return
        libraryDao.upsertProgress(
            PlaybackProgressEntity(
                sourceId = sourceId,
                mediaKind = mediaKind.name,
                contentId = contentId,
                positionMs = durationMs,
                durationMs = durationMs,
                completed = true,
                updatedAt = nowMillis(),
            ),
        )
    }

    override suspend fun setMovieFavorite(movieId: String, favorite: Boolean) {
        if (movieId.isBlank()) return
        val movie = libraryDao.getMovie(movieId) ?: return
        setMediaFavorite(movie.sourceId, FAVORITE_KIND_MOVIE, movie.movieId, favorite)
    }

    override suspend fun setSeriesFavorite(seriesId: String, favorite: Boolean) {
        if (seriesId.isBlank()) return
        val series = libraryDao.getSeries(seriesId) ?: return
        setMediaFavorite(series.sourceId, FAVORITE_KIND_SERIES, series.seriesId, favorite)
    }

    private suspend fun setMediaFavorite(sourceId: String, mediaKind: String, contentId: String, favorite: Boolean) {
        if (favorite) {
            libraryDao.upsertMediaFavorite(MediaFavoriteEntity(sourceId, mediaKind, contentId, nowMillis()))
        } else {
            libraryDao.deleteMediaFavorite(sourceId, mediaKind, contentId)
        }
    }

    private fun observeRows(sourceId: String): Flow<LibraryRows> {
        val coreRows = combine(
            libraryDao.observeAvailableMovies(sourceId),
            libraryDao.observeAvailableSeries(sourceId),
        ) { movies, series -> CoreRows(movies, series) }
        val categoryRows = combine(
            catalogDao.observeAvailableCategories(sourceId, "MOVIE"),
            catalogDao.observeAvailableCategories(sourceId, "SERIES"),
        ) { movieCategories, seriesCategories -> CategoryRows(movieCategories, seriesCategories) }
        return combine(
            coreRows,
            categoryRows,
            libraryDao.observeIncompleteProgress(sourceId),
            libraryDao.observeCompletedDownloads(sourceId),
            libraryDao.observeMediaFavorites(sourceId),
        ) { core, categories, progress, downloads, favorites ->
            val progressEpisodeIds = progress.asSequence()
                .filter { row ->
                    row.mediaKind.equals(LibraryMediaKind.EPISODE.name, ignoreCase = true) &&
                        !row.completed && row.positionMs > 0L && row.durationMs > 0L
                }
                .map { it.contentId }
                .distinct()
                .toList()
            val progressEpisodes = if (progressEpisodeIds.isEmpty()) {
                emptyList()
            } else {
                libraryDao.getEpisodesForProgress(sourceId, progressEpisodeIds)
            }
            LibraryRows(
                movies = core.movies,
                series = core.series,
                episodes = progressEpisodes,
                movieCategories = categories.movieCategories,
                seriesCategories = categories.seriesCategories,
                progress = progress,
                downloads = downloads,
                favorites = favorites,
            )
        }
    }

    private fun LibraryRows.toCatalog(source: Source): LibraryCatalog {
        val progressByKey = progress.associateBy { ProgressKey(it.mediaKind.uppercase(Locale.US), it.contentId) }
        val favoriteKeys = favorites.mapTo(mutableSetOf()) { FavoriteKey(it.mediaKind.uppercase(Locale.US), it.contentId) }
        val movieModels = movies.map { movie ->
            movie.toDomain(
                progress = progressByKey[ProgressKey(LibraryMediaKind.MOVIE.name, movie.movieId)],
                favorite = FavoriteKey(FAVORITE_KIND_MOVIE, movie.movieId) in favoriteKeys,
            )
        }
        val seriesModels = series.map { it.toDomain(FavoriteKey(FAVORITE_KIND_SERIES, it.seriesId) in favoriteKeys) }
        val episodeModels = episodes.map { it.toDomain() }
        val movieById = movieModels.associateBy { it.movieId }
        val episodeById = episodeModels.associateBy { it.episodeId }
        val seriesById = seriesModels.associateBy { it.seriesId }

        val continueItems = progress.mapNotNull { row ->
            if (row.completed || row.positionMs <= 0L || row.durationMs <= 0L) return@mapNotNull null
            when (row.mediaKind.uppercase(Locale.US)) {
                LibraryMediaKind.MOVIE.name -> movieById[row.contentId]?.let { movie ->
                    ContinueWatchingItem(
                        sourceId = row.sourceId,
                        contentId = movie.movieId,
                        mediaKind = LibraryMediaKind.MOVIE,
                        title = movie.name,
                        subtitle = "Movie",
                        artworkUrl = movie.posterUrl ?: movie.backdropUrl,
                        positionMs = row.positionMs,
                        durationMs = row.durationMs,
                        updatedAt = row.updatedAt,
                    )
                }
                LibraryMediaKind.EPISODE.name -> episodeById[row.contentId]?.let { episode ->
                    val series = seriesById[episode.seriesId]
                    ContinueWatchingItem(
                        sourceId = row.sourceId,
                        contentId = episode.episodeId,
                        mediaKind = LibraryMediaKind.EPISODE,
                        title = episode.seriesName,
                        subtitle = "S${episode.seasonNumber} E${episode.episodeNumber}  ${episode.title}",
                        artworkUrl = series?.posterUrl ?: series?.backdropUrl,
                        positionMs = row.positionMs,
                        durationMs = row.durationMs,
                        updatedAt = row.updatedAt,
                    )
                }
                else -> null
            }
        }

        val downloadModels = downloads.mapNotNull { it.toDomainOrNull() }
        return LibraryCatalog(
            activeSourceId = source.sourceId,
            activeSourceName = source.displayName,
            continueWatching = LibraryOrderingPolicy.continueWatching(continueItems),
            movieCategories = movieCategories.map { LibraryCategory(it.categoryKey, it.name, it.providerOrder) },
            movies = LibraryOrderingPolicy.movies(movieModels),
            seriesCategories = seriesCategories.map { LibraryCategory(it.categoryKey, it.name, it.providerOrder) },
            series = LibraryOrderingPolicy.series(seriesModels),
            downloadedMedia = LibraryOrderingPolicy.downloadedMedia(downloadModels),
        )
    }

    private suspend fun cachedSeriesDetail(series: SeriesEntity): LibrarySeriesDetail? {
        val episodes = libraryDao.getEpisodesForSeries(series.seriesId).map { it.toDomain() }
        return LibrarySeriesDetail(
            series = series.toDomain(),
            episodes = episodes,
            metadata = series.baseMetadata(),
        ).takeIf { episodes.isNotEmpty() }
    }

    private fun LibrarySeriesDetail?.orFailure(code: String, message: String): LibrarySeriesDetailResult = if (this != null) {
        LibrarySeriesDetailResult.Success(this, "Episode or metadata refresh is unavailable. Showing cached details.")
    } else {
        seriesFailure(code, message)
    }

    private suspend fun playableXtreamCredential(source: SourceEntity): SourceCredential.Xtream? {
        if (!source.enabled || source.type != SourceType.XTREAM.name) return null
        return credentialStore.get(source.sourceId) as? SourceCredential.Xtream
    }

    private fun MovieEntity.toDomain(progress: PlaybackProgressEntity?, favorite: Boolean = false): LibraryMovie {
        val validProgress = progress?.takeIf { !it.completed && it.positionMs > 0L && it.durationMs > 0L }
        return LibraryMovie(
            movieId = movieId,
            sourceId = sourceId,
            categoryKey = categoryKey,
            name = name,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            rating = rating,
            providerOrder = providerOrder,
            resumePositionMs = validProgress?.positionMs,
            durationMs = validProgress?.durationMs,
            favorite = favorite,
        )
    }

    private fun SeriesEntity.toDomain(favorite: Boolean = false): LibrarySeries = LibrarySeries(
        seriesId = seriesId,
        sourceId = sourceId,
        categoryKey = categoryKey,
        name = name,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        description = description,
        rating = rating,
        providerOrder = providerOrder,
        favorite = favorite,
    )

    private fun EpisodeLibraryView.toDomain(): LibraryEpisode {
        val resumePosition = progressPositionMs?.takeIf { progressCompleted != true && it > 0L }
        return LibraryEpisode(
            episodeId = episodeId,
            seriesId = seriesId,
            sourceId = sourceId,
            seriesName = seriesName,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            title = title,
            durationMs = progressDurationMs?.takeIf { it > 0L } ?: durationMs,
            resumePositionMs = resumePosition,
        )
    }

    private fun MovieEntity.baseMetadata(): LibraryMediaMetadata = LibraryMediaMetadata(
        title = name,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        rating = rating,
    )

    private fun SeriesEntity.baseMetadata(): LibraryMediaMetadata = LibraryMediaMetadata(
        title = name,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        plot = description,
        rating = rating,
    )

    private fun XtreamMediaInfo.toLibraryMetadata(
        fallbackTitle: String,
        fallbackPoster: String?,
        fallbackBackdrop: String?,
        fallbackRating: String?,
        fallbackPlot: String? = null,
    ): LibraryMediaMetadata = LibraryMediaMetadata(
        title = title?.takeIf(String::isNotBlank) ?: fallbackTitle,
        posterUrl = posterUrl?.takeIf(String::isNotBlank) ?: fallbackPoster,
        backdropUrl = backdropUrl?.takeIf(String::isNotBlank) ?: fallbackBackdrop,
        plot = plot?.takeIf(String::isNotBlank) ?: fallbackPlot,
        releaseDate = releaseDate?.takeIf(String::isNotBlank),
        durationMs = durationSeconds
            ?.takeIf { it >= 0L && it <= Long.MAX_VALUE / 1_000L }
            ?.times(1_000L),
        rating = rating?.takeIf(String::isNotBlank) ?: fallbackRating,
        genre = genre?.takeIf(String::isNotBlank),
        director = director?.takeIf(String::isNotBlank),
        cast = cast?.takeIf(String::isNotBlank),
    )

    private fun DownloadEntity.toDomainOrNull(): LibraryDownloadedMedia? {
        val kind = runCatching { LibraryMediaKind.valueOf(mediaKind.uppercase(Locale.US)) }.getOrNull() ?: return null
        return LibraryDownloadedMedia(downloadId, sourceId, kind, contentId, title, createdAt)
    }

    private fun playbackFailure(code: String, message: String) = LibraryPlaybackResolution.Failure(code, message)
    private fun movieFailure(code: String, message: String) = LibraryMovieDetailResult.Failure(code, message)
    private fun seriesFailure(code: String, message: String) = LibrarySeriesDetailResult.Failure(code, message)

    private data class ProgressKey(val mediaKind: String, val contentId: String)
    private data class FavoriteKey(val mediaKind: String, val contentId: String)
    private data class CoreRows(val movies: List<MovieEntity>, val series: List<SeriesEntity>)
    private data class CategoryRows(
        val movieCategories: List<ProviderCategoryEntity>,
        val seriesCategories: List<ProviderCategoryEntity>,
    )
    private data class LibraryRows(
        val movies: List<MovieEntity>,
        val series: List<SeriesEntity>,
        val episodes: List<EpisodeLibraryView>,
        val movieCategories: List<ProviderCategoryEntity>,
        val seriesCategories: List<ProviderCategoryEntity>,
        val progress: List<PlaybackProgressEntity>,
        val downloads: List<DownloadEntity>,
        val favorites: List<MediaFavoriteEntity>,
    )

    private companion object {
        const val FAVORITE_KIND_MOVIE = "MOVIE"
        const val FAVORITE_KIND_SERIES = "SERIES"
    }
}
