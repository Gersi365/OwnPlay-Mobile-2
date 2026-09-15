package app.ownplay.mobile.feature.library.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.ownplay.mobile.data.prefs.LibraryVisibilityPreferences
import app.ownplay.mobile.data.prefs.LibraryVisibilitySnapshot
import app.ownplay.mobile.downloads.domain.DownloadAction
import app.ownplay.mobile.downloads.domain.DownloadItem
import app.ownplay.mobile.downloads.domain.DownloadOperationResult
import app.ownplay.mobile.downloads.domain.DownloadRepository
import app.ownplay.mobile.downloads.domain.OfflineAvailability
import app.ownplay.mobile.feature.library.data.LibraryDownloadMetadataResolver
import app.ownplay.mobile.feature.library.domain.LibraryCatalog
import app.ownplay.mobile.feature.library.domain.LibraryEpisode
import app.ownplay.mobile.feature.library.domain.LibraryMediaKind
import app.ownplay.mobile.feature.library.domain.LibraryMediaMetadata
import app.ownplay.mobile.feature.library.domain.LibraryMovieDetail
import app.ownplay.mobile.feature.library.domain.LibraryMovieDetailResult
import app.ownplay.mobile.feature.library.domain.LibraryPlaybackResolution
import app.ownplay.mobile.feature.library.domain.LibraryRepository
import app.ownplay.mobile.feature.library.domain.LibrarySeriesDetail
import app.ownplay.mobile.feature.library.domain.LibrarySeriesDetailResult
import app.ownplay.mobile.feature.library.domain.LibraryStartMode
import app.ownplay.mobile.feature.library.domain.ResolvedLibraryPlayback
import app.ownplay.mobile.playback.PlaybackController
import kotlinx.coroutines.launch

@Composable
fun LibraryShell(
    catalog: LibraryCatalog?,
    libraryRepository: LibraryRepository,
    downloadRepository: DownloadRepository,
    downloadMetadataResolver: LibraryDownloadMetadataResolver,
    libraryVisibilityPreferences: LibraryVisibilityPreferences,
    playbackController: PlaybackController,
    resumePlaybackEnabled: Boolean,
    onFullscreenChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val downloadsFlow = remember(downloadRepository) { downloadRepository.observeDownloads() }
    val visibilityFlow = remember(libraryVisibilityPreferences) { libraryVisibilityPreferences.visibility }
    val storedDownloads by downloadsFlow.collectAsState(initial = emptyList())
    val visibility by visibilityFlow.collectAsState(initial = LibraryVisibilitySnapshot())
    val scope = rememberCoroutineScope()
    val metadataOverrides = remember { mutableStateMapOf<String, LibraryMediaMetadata>() }
    val downloads = storedDownloads.map { item ->
        metadataOverrides[item.downloadId]?.let { metadata -> item.copy(metadata = metadata) } ?: item
    }
    val metadataResolutionKeys = remember(storedDownloads) {
        storedDownloads.map { item ->
            DownloadMetadataResolutionKey(
                downloadId = item.downloadId,
                sourceId = item.sourceId,
                mediaKind = item.mediaKind,
                contentId = item.contentId,
                metadata = item.metadata,
                missingDurationProgressAvailable =
                    (item.metadata?.durationMs ?: 0L) <= 0L && item.resumePositionMs != null,
            )
        }
    }

    val downloadsByContent = remember(downloads) {
        downloads.associateBy { DownloadContentKeyStage33(it.sourceId, it.mediaKind, it.contentId) }
    }

    var selectedMovieId by remember { mutableStateOf<String?>(null) }
    var selectedSeriesId by remember { mutableStateOf<String?>(null) }
    var movieDetail by remember { mutableStateOf<LibraryMovieDetail?>(null) }
    var movieWarning by remember { mutableStateOf<String?>(null) }
    var seriesDetail by remember { mutableStateOf<LibrarySeriesDetail?>(null) }
    var seriesDetailRefreshToken by remember { mutableStateOf(0) }
    var selectedEpisodeId by remember { mutableStateOf<String?>(null) }
    var seriesWarning by remember { mutableStateOf<String?>(null) }
    var detailError by remember { mutableStateOf<String?>(null) }
    var resolutionError by remember { mutableStateOf<String?>(null) }
    var activePlayback by remember { mutableStateOf<ResolvedLibraryPlayback?>(null) }
    var selectedManagedDownloadId by remember { mutableStateOf<String?>(null) }
    var managedDownloadAvailability by remember { mutableStateOf<OfflineAvailability?>(null) }

    val selectedMovie = catalog?.movies?.firstOrNull { it.movieId == selectedMovieId }
    val selectedSeries = catalog?.series?.firstOrNull { it.seriesId == selectedSeriesId }
    val selectedManagedDownload = downloads.firstOrNull { it.downloadId == selectedManagedDownloadId }

    fun downloadFor(sourceId: String, mediaKind: LibraryMediaKind, contentId: String): DownloadItem? =
        downloadsByContent[DownloadContentKeyStage33(sourceId, mediaKind, contentId)]

    fun acceptResolution(resolved: LibraryPlaybackResolution) {
        when (resolved) {
            is LibraryPlaybackResolution.Success -> {
                resolutionError = null
                onFullscreenChanged(true)
                activePlayback = resolved.value
                scope.launch {
                    libraryVisibilityPreferences.showContinueWatching(
                        sourceId = resolved.value.sourceId,
                        mediaKind = resolved.value.mediaKind,
                        contentId = resolved.value.contentId,
                    )
                    playbackController.load(resolved.value.toStage33LoadRequest())
                }
            }
            is LibraryPlaybackResolution.Failure -> resolutionError = resolved.safeMessage
        }
    }

    fun startMovie(movieId: String, startMode: LibraryStartMode) {
        scope.launch {
            resolutionError = null
            acceptResolution(libraryRepository.resolveMoviePlayback(movieId, startMode))
        }
    }

    fun startEpisode(episodeId: String, startMode: LibraryStartMode) {
        scope.launch {
            resolutionError = null
            acceptResolution(libraryRepository.resolveEpisodePlayback(episodeId, startMode))
        }
    }

    fun openDetailTarget(target: LibraryDetailTargetStage33): Boolean {
        val targetAvailable = when (target) {
            is LibraryDetailTargetStage33.Movie -> catalog?.movies?.any { it.movieId == target.movieId } == true
            is LibraryDetailTargetStage33.Series -> catalog?.series?.any { it.seriesId == target.seriesId } == true
        }
        if (!targetAvailable) return false

        resolutionError = null
        selectedManagedDownloadId = null
        managedDownloadAvailability = null
        movieDetail = null
        seriesDetail = null
        movieWarning = null
        seriesWarning = null
        detailError = null
        when (target) {
            is LibraryDetailTargetStage33.Movie -> {
                selectedSeriesId = null
                selectedEpisodeId = null
                selectedMovieId = target.movieId
            }
            is LibraryDetailTargetStage33.Series -> {
                selectedMovieId = null
                selectedEpisodeId = target.episodeId
                selectedSeriesId = target.seriesId
            }
        }
        return true
    }

    fun showManagedDownload(item: DownloadItem) {
        resolutionError = null
        selectedMovieId = null
        selectedSeriesId = null
        selectedEpisodeId = null
        movieDetail = null
        seriesDetail = null
        movieWarning = null
        seriesWarning = null
        detailError = null
        managedDownloadAvailability = null
        selectedManagedDownloadId = item.downloadId
    }

    fun openCanonicalDetails(
        sourceId: String,
        mediaKind: LibraryMediaKind,
        contentId: String,
    ) {
        resolutionError = null
        when (mediaKind) {
            LibraryMediaKind.MOVIE -> {
                val target = LibraryDetailNavigationPolicyStage33.target(mediaKind, contentId)
                if (target == null || !openDetailTarget(target)) {
                    resolutionError = "This movie is no longer available in Library."
                }
            }
            LibraryMediaKind.EPISODE -> scope.launch {
                val context = downloadMetadataResolver
                    .resolveEpisodeContexts(sourceId, listOf(contentId))[contentId]
                val target = LibraryDetailNavigationPolicyStage33.target(
                    mediaKind = mediaKind,
                    contentId = contentId,
                    episodeSeriesId = context?.seriesId,
                )
                if (target == null || !openDetailTarget(target)) {
                    resolutionError = "This episode is no longer available in Library."
                }
            }
        }
    }

    fun openManagedDownload(item: DownloadItem) {
        resolutionError = null
        when (item.mediaKind) {
            LibraryMediaKind.MOVIE -> {
                val target = LibraryDetailNavigationPolicyStage33.target(item.mediaKind, item.contentId)
                if (target == null || !openDetailTarget(target)) {
                    showManagedDownload(item)
                }
            }
            LibraryMediaKind.EPISODE -> scope.launch {
                val context = downloadMetadataResolver
                    .resolveEpisodeContexts(item.sourceId, listOf(item.contentId))[item.contentId]
                val target = LibraryDetailNavigationPolicyStage33.target(
                    mediaKind = item.mediaKind,
                    contentId = item.contentId,
                    episodeSeriesId = context?.seriesId,
                )
                if (context?.available != true || target == null || !openDetailTarget(target)) {
                    showManagedDownload(item)
                }
            }
        }
    }

    fun performDownloadAction(
        item: DownloadItem?,
        sourceId: String,
        mediaKind: LibraryMediaKind,
        contentId: String,
        title: String,
        action: DownloadAction,
        metadata: LibraryMediaMetadata? = null,
        onSuccess: (() -> Unit)? = null,
    ) {
        scope.launch {
            resolutionError = null
            when (action) {
                DownloadAction.PLAY_OFFLINE,
                DownloadAction.RESUME_OFFLINE,
                -> {
                    val downloadId = item?.downloadId ?: return@launch
                    val startMode = if (action == DownloadAction.RESUME_OFFLINE) {
                        LibraryStartMode.RESUME
                    } else {
                        LibraryStartMode.BEGINNING
                    }
                    acceptResolution(downloadRepository.resolveOfflinePlayback(downloadId, startMode))
                }

                else -> {
                    val result = when (action) {
                        DownloadAction.DOWNLOAD -> downloadRepository.requestDownload(
                            sourceId = sourceId,
                            mediaKind = mediaKind,
                            contentId = contentId,
                            title = title,
                        )
                        DownloadAction.PAUSE -> item?.let { downloadRepository.pause(it.downloadId) }
                        DownloadAction.RESUME -> item?.let { downloadRepository.resume(it.downloadId) }
                        DownloadAction.RETRY -> item?.let { downloadRepository.retry(it.downloadId) }
                        DownloadAction.REMOVE -> item?.let { downloadRepository.remove(it.downloadId) }
                        DownloadAction.PLAY_OFFLINE,
                        DownloadAction.RESUME_OFFLINE,
                        -> null
                    }
                    when (result) {
                        is DownloadOperationResult.Failure -> resolutionError = result.safeMessage
                        is DownloadOperationResult.Success -> {
                            if (action == DownloadAction.DOWNLOAD) {
                                result.item?.let { created ->
                                    metadata?.let { snapshot ->
                                        metadataOverrides[created.downloadId] = snapshot
                                        downloadRepository.saveMetadata(created.downloadId, snapshot)
                                    }
                                }
                            }
                            if (action == DownloadAction.REMOVE) {
                                item?.let { metadataOverrides.remove(it.downloadId) }
                            }
                            onSuccess?.invoke()
                        }
                        null -> Unit
                    }
                }
            }
        }
    }

    LaunchedEffect(selectedMovieId) {
        val movieId = selectedMovieId
        movieDetail = null
        movieWarning = null
        detailError = null
        if (movieId != null) {
            when (val result = libraryRepository.loadMovieDetail(movieId)) {
                is LibraryMovieDetailResult.Success -> {
                    movieDetail = result.detail
                    movieWarning = result.refreshWarning
                }
                is LibraryMovieDetailResult.Failure -> detailError = result.safeMessage
            }
        }
    }

    LaunchedEffect(selectedSeriesId, seriesDetailRefreshToken) {
        val seriesId = selectedSeriesId
        seriesDetail = null
        seriesWarning = null
        detailError = null
        if (seriesId != null) {
            when (val result = libraryRepository.loadSeriesDetail(seriesId)) {
                is LibrarySeriesDetailResult.Success -> {
                    seriesDetail = result.detail
                    seriesWarning = result.refreshWarning
                }
                is LibrarySeriesDetailResult.Failure -> detailError = result.safeMessage
            }
        }
    }

    LaunchedEffect(movieDetail, selectedMovieId, downloads) {
        val detail = movieDetail ?: return@LaunchedEffect
        val movie = selectedMovie ?: return@LaunchedEffect
        val item = downloadFor(movie.sourceId, LibraryMediaKind.MOVIE, movie.movieId) ?: return@LaunchedEffect
        val candidate = detail.metadata.withFallbackDuration(movie.durationMs)
        val merged = item.metadata.mergeMissingFrom(candidate)
        if (item.metadata == null || merged != item.metadata) {
            metadataOverrides[item.downloadId] = merged
            downloadRepository.saveMetadata(item.downloadId, merged)
        }
    }

    LaunchedEffect(seriesDetail, selectedSeriesId, downloads) {
        val detail = seriesDetail ?: return@LaunchedEffect
        val metadata = detail.metadata ?: detail.series.toBaseMetadata()
        detail.episodes.forEach { episode ->
            val item = downloadFor(episode.sourceId, LibraryMediaKind.EPISODE, episode.episodeId)
            if (item != null) {
                val candidate = buildEpisodeDownloadMetadata(metadata, episode)
                val merged = item.metadata.mergeMissingFrom(candidate)
                if (item.metadata == null || merged != item.metadata) {
                    metadataOverrides[item.downloadId] = merged
                    downloadRepository.saveMetadata(item.downloadId, merged)
                }
            }
        }
    }

    LaunchedEffect(metadataResolutionKeys) {
        val activeIds = storedDownloads.mapTo(mutableSetOf()) { it.downloadId }
        metadataOverrides.keys.toList().filterNot(activeIds::contains).forEach(metadataOverrides::remove)

        val pending = buildList {
            storedDownloads.forEach { item ->
                val base = downloadMetadataResolver.resolve(item.sourceId, item.mediaKind, item.contentId)
                    ?: return@forEach
                val merged = item.metadata.mergeMissingFrom(base)
                if (item.metadata == null || merged != item.metadata) {
                    add(item.downloadId to merged)
                } else {
                    metadataOverrides.remove(item.downloadId)
                }
            }
        }
        pending.forEach { (downloadId, metadata) ->
            metadataOverrides[downloadId] = metadata
        }
        pending.forEach { (downloadId, metadata) ->
            downloadRepository.saveMetadata(downloadId, metadata)
        }
    }

    LaunchedEffect(selectedManagedDownloadId, selectedManagedDownload?.updatedAt) {
        val selectedId = selectedManagedDownloadId
        val item = selectedManagedDownload
        when {
            selectedId == null -> managedDownloadAvailability = null
            item == null -> {
                selectedManagedDownloadId = null
                managedDownloadAvailability = null
            }
            else -> managedDownloadAvailability = downloadRepository.offlineAvailability(item.downloadId)
        }
    }

    LaunchedEffect(activePlayback) {
        if (activePlayback == null) onFullscreenChanged(false)
    }

    DisposableEffect(Unit) {
        onDispose { onFullscreenChanged(false) }
    }

    BackHandler(
        enabled = activePlayback == null &&
            (selectedMovieId != null || selectedSeriesId != null || selectedManagedDownloadId != null),
    ) {
        selectedMovieId = null
        selectedSeriesId = null
        selectedEpisodeId = null
        selectedManagedDownloadId = null
        managedDownloadAvailability = null
        movieDetail = null
        seriesDetail = null
        movieWarning = null
        seriesWarning = null
        detailError = null
        resolutionError = null
    }

    when {
        activePlayback != null -> LibraryFullscreenPlayerStage33(
            playback = activePlayback!!,
            libraryRepository = libraryRepository,
            playbackController = playbackController,
            onClose = {
                val closedPlayback = activePlayback
                activePlayback = null
                if (closedPlayback?.mediaKind == LibraryMediaKind.EPISODE && selectedSeriesId != null) {
                    seriesDetailRefreshToken += 1
                }
                resolutionError = null
            },
            modifier = modifier,
        )

        selectedManagedDownload != null -> ManagedDownloadDetailStage33(
            item = selectedManagedDownload,
            availability = managedDownloadAvailability,
            errorMessage = resolutionError,
            preferResume = resumePlaybackEnabled,
            onBack = {
                selectedManagedDownloadId = null
                managedDownloadAvailability = null
                resolutionError = null
            },
            onPlayOffline = { mode ->
                val action = if (mode == LibraryStartMode.RESUME) {
                    DownloadAction.RESUME_OFFLINE
                } else {
                    DownloadAction.PLAY_OFFLINE
                }
                performDownloadAction(
                    item = selectedManagedDownload,
                    sourceId = selectedManagedDownload.sourceId,
                    mediaKind = selectedManagedDownload.mediaKind,
                    contentId = selectedManagedDownload.contentId,
                    title = selectedManagedDownload.title,
                    action = action,
                    metadata = selectedManagedDownload.metadata,
                )
            },
            onRemove = {
                performDownloadAction(
                    item = selectedManagedDownload,
                    sourceId = selectedManagedDownload.sourceId,
                    mediaKind = selectedManagedDownload.mediaKind,
                    contentId = selectedManagedDownload.contentId,
                    title = selectedManagedDownload.title,
                    action = DownloadAction.REMOVE,
                    metadata = selectedManagedDownload.metadata,
                    onSuccess = {
                        selectedManagedDownloadId = null
                        managedDownloadAvailability = null
                    },
                )
            },
            onDownloadAction = { action ->
                performDownloadAction(
                    item = selectedManagedDownload,
                    sourceId = selectedManagedDownload.sourceId,
                    mediaKind = selectedManagedDownload.mediaKind,
                    contentId = selectedManagedDownload.contentId,
                    title = selectedManagedDownload.title,
                    action = action,
                    metadata = selectedManagedDownload.metadata,
                )
            },
            modifier = modifier,
        )

        selectedMovie != null -> {
            val downloadItem = downloadFor(selectedMovie.sourceId, LibraryMediaKind.MOVIE, selectedMovie.movieId)
            val displayDetail = movieDetail?.let { detail ->
                detail.copy(metadata = detail.metadata.withFallbackDuration(selectedMovie.durationMs))
            }
            MovieDetailStage33(
                movie = selectedMovie,
                detail = displayDetail,
                warning = movieWarning,
                downloadItem = downloadItem,
                errorMessage = detailError ?: resolutionError,
                preferResume = resumePlaybackEnabled,
                onBack = {
                    selectedMovieId = null
                    movieDetail = null
                    movieWarning = null
                    detailError = null
                    resolutionError = null
                },
                onResume = { startMovie(selectedMovie.movieId, LibraryStartMode.RESUME) },
                onBeginning = { startMovie(selectedMovie.movieId, LibraryStartMode.BEGINNING) },
                onFavoriteToggle = {
                    scope.launch { libraryRepository.setMovieFavorite(selectedMovie.movieId, !selectedMovie.favorite) }
                },
                onDownloadAction = { action, metadata ->
                    performDownloadAction(
                        item = downloadItem,
                        sourceId = selectedMovie.sourceId,
                        mediaKind = LibraryMediaKind.MOVIE,
                        contentId = selectedMovie.movieId,
                        title = selectedMovie.name,
                        action = action,
                        metadata = metadata.withFallbackDuration(selectedMovie.durationMs),
                    )
                },
                modifier = modifier,
            )
        }

        selectedSeries != null -> SeriesDetailStage33(
            series = selectedSeries,
            detail = seriesDetail,
            initialEpisodeId = selectedEpisodeId,
            warning = seriesWarning,
            errorMessage = detailError ?: resolutionError,
            preferResume = resumePlaybackEnabled,
            onBack = {
                selectedSeriesId = null
                selectedEpisodeId = null
                seriesDetail = null
                seriesWarning = null
                detailError = null
                resolutionError = null
            },
            onEpisodePlay = { episode, mode ->
                startEpisode(episode.episodeId, mode)
            },
            onFavoriteToggle = {
                scope.launch { libraryRepository.setSeriesFavorite(selectedSeries.seriesId, !selectedSeries.favorite) }
            },
            downloadForEpisode = { episode ->
                downloadFor(episode.sourceId, LibraryMediaKind.EPISODE, episode.episodeId)
            },
            onDownloadAction = { episode, item, action, metadata ->
                performDownloadAction(
                    item = item,
                    sourceId = episode.sourceId,
                    mediaKind = LibraryMediaKind.EPISODE,
                    contentId = episode.episodeId,
                    title = "${episode.seriesName} • ${episode.title}",
                    action = action,
                    metadata = metadata,
                )
            },
            modifier = modifier,
        )

        else -> LibraryHomeStage33(
            catalog = catalog,
            downloads = downloads,
            visibility = visibility,
            errorMessage = resolutionError,
            onContinueSelected = { item ->
                openCanonicalDetails(
                    sourceId = item.sourceId,
                    mediaKind = item.mediaKind,
                    contentId = item.contentId,
                )
            },
            onContinueMarkWatched = { item ->
                scope.launch {
                    libraryRepository.markWatched(
                        sourceId = item.sourceId,
                        mediaKind = item.mediaKind,
                        contentId = item.contentId,
                        durationMs = item.durationMs,
                    )
                }
            },
            onContinueClearProgress = { item ->
                scope.launch {
                    libraryRepository.clearProgress(
                        sourceId = item.sourceId,
                        mediaKind = item.mediaKind,
                        contentId = item.contentId,
                    )
                }
            },
            onMovieSelected = { movie ->
                resolutionError = null
                selectedSeriesId = null
                selectedEpisodeId = null
                selectedMovieId = movie.movieId
            },
            onSeriesSelected = { series ->
                resolutionError = null
                selectedMovieId = null
                selectedEpisodeId = null
                selectedSeriesId = series.seriesId
            },
            onContinueOfflineSelected = ::openManagedDownload,
            modifier = modifier,
        )
    }
}

private fun LibraryMediaMetadata?.mergeMissingFrom(fallback: LibraryMediaMetadata): LibraryMediaMetadata {
    val current = this ?: return fallback
    fun String?.orFallback(value: String?): String? = this?.takeIf(String::isNotBlank) ?: value
    return current.copy(
        posterUrl = current.posterUrl.orFallback(fallback.posterUrl),
        backdropUrl = current.backdropUrl.orFallback(fallback.backdropUrl),
        plot = current.plot.orFallback(fallback.plot),
        releaseDate = current.releaseDate.orFallback(fallback.releaseDate),
        durationMs = current.durationMs?.takeIf { it > 0L } ?: fallback.durationMs,
        rating = current.rating.orFallback(fallback.rating),
        genre = current.genre.orFallback(fallback.genre),
        director = current.director.orFallback(fallback.director),
        cast = current.cast.orFallback(fallback.cast),
    )
}

private fun LibraryMediaMetadata.withFallbackDuration(fallbackDurationMs: Long?): LibraryMediaMetadata =
    if ((durationMs == null || durationMs <= 0L) && (fallbackDurationMs ?: 0L) > 0L) {
        copy(durationMs = fallbackDurationMs)
    } else {
        this
    }

private data class DownloadMetadataResolutionKey(
    val downloadId: String,
    val sourceId: String,
    val mediaKind: LibraryMediaKind,
    val contentId: String,
    val metadata: LibraryMediaMetadata?,
    val missingDurationProgressAvailable: Boolean,
)

private data class DownloadContentKeyStage33(
    val sourceId: String,
    val mediaKind: LibraryMediaKind,
    val contentId: String,
)
