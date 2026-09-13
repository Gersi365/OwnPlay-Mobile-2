package app.ownplay.mobile.feature.library.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    libraryRepository: LibraryRepository,
    downloadRepository: DownloadRepository,
    libraryVisibilityPreferences: LibraryVisibilityPreferences,
    playbackController: PlaybackController,
    resumePlaybackEnabled: Boolean,
    initialOfflineDownloadId: String? = null,
    onInitialOfflineConsumed: () -> Unit = {},
    onFullscreenChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val catalogFlow = remember(libraryRepository) { libraryRepository.observeCatalog() }
    val downloadsFlow = remember(downloadRepository) { downloadRepository.observeDownloads() }
    val visibilityFlow = remember(libraryVisibilityPreferences) { libraryVisibilityPreferences.visibility }
    val catalog by catalogFlow.collectAsState(initial = null)
    val downloads by downloadsFlow.collectAsState(initial = emptyList())
    val visibility by visibilityFlow.collectAsState(initial = LibraryVisibilitySnapshot())
    val scope = rememberCoroutineScope()

    val downloadsByContent = remember(downloads) {
        downloads.associateBy { DownloadContentKeyStage33(it.sourceId, it.mediaKind, it.contentId) }
    }

    var selectedMovieId by remember { mutableStateOf<String?>(null) }
    var selectedSeriesId by remember { mutableStateOf<String?>(null) }
    var selectedDownloadId by remember { mutableStateOf<String?>(null) }
    var movieDetail by remember { mutableStateOf<LibraryMovieDetail?>(null) }
    var movieWarning by remember { mutableStateOf<String?>(null) }
    var seriesDetail by remember { mutableStateOf<LibrarySeriesDetail?>(null) }
    var seriesWarning by remember { mutableStateOf<String?>(null) }
    var detailError by remember { mutableStateOf<String?>(null) }
    var resolutionError by remember { mutableStateOf<String?>(null) }
    var offlineAvailability by remember { mutableStateOf<OfflineAvailability?>(null) }
    var activePlayback by remember { mutableStateOf<ResolvedLibraryPlayback?>(null) }

    val selectedMovie = catalog?.movies?.firstOrNull { it.movieId == selectedMovieId }
    val selectedSeries = catalog?.series?.firstOrNull { it.seriesId == selectedSeriesId }
    val selectedDownload = downloads.firstOrNull { it.downloadId == selectedDownloadId }

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

    fun startDownloadedOnline(item: DownloadItem, startMode: LibraryStartMode) {
        when (item.mediaKind) {
            LibraryMediaKind.MOVIE -> startMovie(item.contentId, startMode)
            LibraryMediaKind.EPISODE -> startEpisode(item.contentId, startMode)
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
        closeAfterRemove: Boolean = false,
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
                                    libraryVisibilityPreferences.showDownload(created.downloadId)
                                    metadata?.let { snapshot ->
                                        downloadRepository.saveMetadata(created.downloadId, snapshot)
                                    }
                                }
                            }
                            if (action == DownloadAction.REMOVE && closeAfterRemove) {
                                selectedDownloadId = null
                                offlineAvailability = null
                            }
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

    LaunchedEffect(selectedSeriesId) {
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
        if (item.metadata == null) {
            downloadRepository.saveMetadata(item.downloadId, detail.metadata)
        }
    }

    LaunchedEffect(seriesDetail, selectedSeriesId, downloads) {
        val detail = seriesDetail ?: return@LaunchedEffect
        val metadata = detail.metadata ?: detail.series.toBaseMetadata()
        detail.episodes.forEach { episode ->
            val item = downloadFor(episode.sourceId, LibraryMediaKind.EPISODE, episode.episodeId)
            if (item != null && item.metadata == null) {
                downloadRepository.saveMetadata(
                    item.downloadId,
                    buildEpisodeDownloadMetadata(metadata, episode),
                )
            }
        }
    }

    LaunchedEffect(catalog, downloads) {
        val movieById = catalog?.movies.orEmpty().associateBy { it.movieId }
        downloads.forEach { item ->
            if (item.metadata == null && item.mediaKind == LibraryMediaKind.MOVIE) {
                val movie = movieById[item.contentId]
                if (movie != null && movie.sourceId == item.sourceId) {
                    downloadRepository.saveMetadata(item.downloadId, movie.toBaseMetadata())
                }
            }
        }
    }

    LaunchedEffect(selectedDownloadId, selectedDownload?.state, selectedDownload?.updatedAt) {
        val downloadId = selectedDownloadId
        offlineAvailability = null
        if (downloadId != null && selectedDownload != null) {
            offlineAvailability = downloadRepository.offlineAvailability(downloadId)
        }
    }

    LaunchedEffect(initialOfflineDownloadId) {
        val downloadId = initialOfflineDownloadId ?: return@LaunchedEffect
        resolutionError = null
        when (
            val resolved = downloadRepository.resolveOfflinePlayback(
                downloadId = downloadId,
                startMode = LibraryStartMode.RESUME,
            )
        ) {
            is LibraryPlaybackResolution.Success -> acceptResolution(resolved)
            is LibraryPlaybackResolution.Failure -> {
                resolutionError = resolved.safeMessage
                selectedDownloadId = downloads.firstOrNull { it.downloadId == downloadId }?.downloadId
            }
        }
        onInitialOfflineConsumed()
    }

    LaunchedEffect(activePlayback) {
        if (activePlayback == null) onFullscreenChanged(false)
    }

    DisposableEffect(Unit) {
        onDispose { onFullscreenChanged(false) }
    }

    BackHandler(
        enabled = activePlayback == null &&
            (selectedMovieId != null || selectedSeriesId != null || selectedDownloadId != null),
    ) {
        selectedMovieId = null
        selectedSeriesId = null
        selectedDownloadId = null
        movieDetail = null
        seriesDetail = null
        movieWarning = null
        seriesWarning = null
        detailError = null
        resolutionError = null
        offlineAvailability = null
    }

    when {
        activePlayback != null -> LibraryFullscreenPlayerStage33(
            playback = activePlayback!!,
            libraryRepository = libraryRepository,
            playbackController = playbackController,
            onClose = {
                activePlayback = null
                resolutionError = null
            },
            modifier = modifier,
        )

        selectedDownload != null -> DownloadedMediaDetailStage33(
            item = selectedDownload,
            availability = offlineAvailability,
            errorMessage = resolutionError,
            preferResume = resumePlaybackEnabled,
            onBack = {
                selectedDownloadId = null
                resolutionError = null
                offlineAvailability = null
            },
            onPlayOffline = { mode ->
                scope.launch {
                    acceptResolution(downloadRepository.resolveOfflinePlayback(selectedDownload.downloadId, mode))
                }
            },
            onPlayFromLibrary = { mode -> startDownloadedOnline(selectedDownload, mode) },
            onRedownload = {
                scope.launch {
                    resolutionError = null
                    when (val result = downloadRepository.redownload(selectedDownload.downloadId)) {
                        is DownloadOperationResult.Failure -> resolutionError = result.safeMessage
                        is DownloadOperationResult.Success -> offlineAvailability = OfflineAvailability.INCOMPLETE
                    }
                }
            },
            onRemove = {
                performDownloadAction(
                    item = selectedDownload,
                    sourceId = selectedDownload.sourceId,
                    mediaKind = selectedDownload.mediaKind,
                    contentId = selectedDownload.contentId,
                    title = selectedDownload.title,
                    action = DownloadAction.REMOVE,
                    closeAfterRemove = true,
                )
            },
            onDownloadAction = { action ->
                performDownloadAction(
                    item = selectedDownload,
                    sourceId = selectedDownload.sourceId,
                    mediaKind = selectedDownload.mediaKind,
                    contentId = selectedDownload.contentId,
                    title = selectedDownload.title,
                    action = action,
                    metadata = selectedDownload.metadata,
                    closeAfterRemove = action == DownloadAction.REMOVE,
                )
            },
            modifier = modifier,
        )

        selectedMovie != null -> {
            val downloadItem = downloadFor(selectedMovie.sourceId, LibraryMediaKind.MOVIE, selectedMovie.movieId)
            MovieDetailStage33(
                movie = selectedMovie,
                detail = movieDetail,
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
                        metadata = metadata,
                    )
                },
                modifier = modifier,
            )
        }

        selectedSeries != null -> SeriesDetailStage33(
            series = selectedSeries,
            detail = seriesDetail,
            warning = seriesWarning,
            errorMessage = detailError ?: resolutionError,
            preferResume = resumePlaybackEnabled,
            onBack = {
                selectedSeriesId = null
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
            onContinueResume = { item ->
                when (item.mediaKind) {
                    LibraryMediaKind.MOVIE -> startMovie(item.contentId, LibraryStartMode.RESUME)
                    LibraryMediaKind.EPISODE -> startEpisode(item.contentId, LibraryStartMode.RESUME)
                }
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
                selectedDownloadId = null
                selectedMovieId = movie.movieId
            },
            onSeriesSelected = { series ->
                resolutionError = null
                selectedMovieId = null
                selectedDownloadId = null
                selectedSeriesId = series.seriesId
            },
            onDownloadedSelected = { item ->
                resolutionError = null
                selectedMovieId = null
                selectedSeriesId = null
                selectedDownloadId = item.downloadId
            },
            modifier = modifier,
        )
    }
}

private data class DownloadContentKeyStage33(
    val sourceId: String,
    val mediaKind: LibraryMediaKind,
    val contentId: String,
)
