package app.ownplay.mobile.feature.library.ui

import android.graphics.BitmapFactory
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayShapeTokens
import app.ownplay.mobile.design.OwnPlaySpacing
import app.ownplay.mobile.design.OwnPlayStatePanel
import app.ownplay.mobile.design.OwnPlayTopBar
import app.ownplay.mobile.downloads.domain.DownloadAction
import app.ownplay.mobile.downloads.domain.DownloadItem
import app.ownplay.mobile.downloads.domain.DownloadOperationResult
import app.ownplay.mobile.downloads.domain.DownloadRepository
import app.ownplay.mobile.downloads.ui.DownloadControls
import app.ownplay.mobile.feature.library.domain.ContinueWatchingItem
import app.ownplay.mobile.feature.library.domain.LibraryCatalog
import app.ownplay.mobile.feature.library.domain.LibraryCategory
import app.ownplay.mobile.feature.library.domain.LibraryDownloadedMedia
import app.ownplay.mobile.feature.library.domain.LibraryEpisode
import app.ownplay.mobile.feature.library.domain.LibraryMediaKind
import app.ownplay.mobile.feature.library.domain.LibraryMovie
import app.ownplay.mobile.feature.library.domain.LibraryPlaybackResolution
import app.ownplay.mobile.feature.library.domain.LibraryRepository
import app.ownplay.mobile.feature.library.domain.LibrarySeries
import app.ownplay.mobile.feature.library.domain.LibrarySeriesDetail
import app.ownplay.mobile.feature.library.domain.LibrarySeriesDetailResult
import app.ownplay.mobile.feature.library.domain.LibraryStartMode
import app.ownplay.mobile.feature.library.domain.PlaybackProgressUpdate
import app.ownplay.mobile.feature.library.domain.ResolvedLibraryPlayback
import app.ownplay.mobile.playback.PlaybackController
import app.ownplay.mobile.playback.domain.PlaybackKind
import app.ownplay.mobile.playback.domain.PlaybackLoadRequest
import app.ownplay.mobile.playback.domain.PlaybackMedia
import app.ownplay.mobile.playback.domain.PlaybackPhase
import app.ownplay.mobile.playback.domain.PlaybackSnapshot
import app.ownplay.mobile.playback.domain.VideoTarget
import app.ownplay.mobile.playback.ui.AudioTrackSelectorPanel
import app.ownplay.mobile.playback.ui.PlayerGlassGlyph
import app.ownplay.mobile.playback.ui.PlayerGlassIconAction
import app.ownplay.mobile.playback.ui.PlayerGlassPillAction
import app.ownplay.mobile.playback.ui.PlayerGlassScrims
import app.ownplay.mobile.playback.ui.PlayerGlassSeekBar
import app.ownplay.mobile.playback.ui.PlayerLocalControlHudOverlay
import app.ownplay.mobile.playback.ui.playerLocalVerticalControls
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LibraryShell(
    libraryRepository: LibraryRepository,
    downloadRepository: DownloadRepository,
    playbackController: PlaybackController,
    resumePlaybackEnabled: Boolean,
    onFullscreenChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val catalogFlow = remember(libraryRepository) { libraryRepository.observeCatalog() }
    val downloadsFlow = remember(downloadRepository) { downloadRepository.observeDownloads() }
    val catalog by catalogFlow.collectAsState(initial = null)
    val downloads by downloadsFlow.collectAsState(initial = emptyList())
    val downloadsByContent = remember(downloads) {
        downloads.associateBy { item ->
            DownloadContentKey(item.sourceId, item.mediaKind, item.contentId)
        }
    }
    val scope = rememberCoroutineScope()

    var selectedMovieId by remember { mutableStateOf<String?>(null) }
    var selectedSeriesId by remember { mutableStateOf<String?>(null) }
    var seriesDetail by remember { mutableStateOf<LibrarySeriesDetail?>(null) }
    var seriesWarning by remember { mutableStateOf<String?>(null) }
    var detailError by remember { mutableStateOf<String?>(null) }
    var resolutionError by remember { mutableStateOf<String?>(null) }
    var activePlayback by remember { mutableStateOf<ResolvedLibraryPlayback?>(null) }

    val selectedMovie = catalog?.movies?.firstOrNull { it.movieId == selectedMovieId }
    val selectedSeries = catalog?.series?.firstOrNull { it.seriesId == selectedSeriesId }

    fun downloadFor(sourceId: String, mediaKind: LibraryMediaKind, contentId: String): DownloadItem? =
        downloadsByContent[DownloadContentKey(sourceId, mediaKind, contentId)]

    fun acceptResolution(resolved: LibraryPlaybackResolution) {
        when (resolved) {
            is LibraryPlaybackResolution.Success -> {
                scope.launch {
                    playbackController.load(resolved.value.toLoadRequest())
                    activePlayback = resolved.value
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

    fun performDownloadAction(
        item: DownloadItem?,
        sourceId: String,
        mediaKind: LibraryMediaKind,
        contentId: String,
        title: String,
        action: DownloadAction,
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
                    if (result is DownloadOperationResult.Failure) {
                        resolutionError = result.safeMessage
                    }
                }
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

    LaunchedEffect(activePlayback) {
        onFullscreenChanged(activePlayback != null)
    }

    DisposableEffect(Unit) {
        onDispose { onFullscreenChanged(false) }
    }

    BackHandler(
        enabled = activePlayback == null && (selectedMovieId != null || selectedSeriesId != null),
    ) {
        selectedMovieId = null
        selectedSeriesId = null
        seriesDetail = null
        seriesWarning = null
        detailError = null
        resolutionError = null
    }

    when {
        activePlayback != null -> LibraryFullscreenPlayer(
            playback = activePlayback!!,
            libraryRepository = libraryRepository,
            playbackController = playbackController,
            onClose = {
                activePlayback = null
                resolutionError = null
            },
            modifier = modifier,
        )

        selectedMovie != null -> {
            val downloadItem = downloadFor(selectedMovie.sourceId, LibraryMediaKind.MOVIE, selectedMovie.movieId)
            MovieDetail(
                movie = selectedMovie,
                downloadItem = downloadItem,
                errorMessage = resolutionError,
                preferResume = resumePlaybackEnabled,
                onBack = {
                    selectedMovieId = null
                    resolutionError = null
                },
                onResume = { startMovie(selectedMovie.movieId, LibraryStartMode.RESUME) },
                onBeginning = { startMovie(selectedMovie.movieId, LibraryStartMode.BEGINNING) },
                onDownloadAction = { action ->
                    performDownloadAction(
                        item = downloadItem,
                        sourceId = selectedMovie.sourceId,
                        mediaKind = LibraryMediaKind.MOVIE,
                        contentId = selectedMovie.movieId,
                        title = selectedMovie.name,
                        action = action,
                    )
                },
                modifier = modifier,
            )
        }

        selectedSeries != null -> SeriesDetail(
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
            onResumeEpisode = { episode -> startEpisode(episode.episodeId, LibraryStartMode.RESUME) },
            onBeginningEpisode = { episode -> startEpisode(episode.episodeId, LibraryStartMode.BEGINNING) },
            downloadForEpisode = { episode ->
                downloadFor(episode.sourceId, LibraryMediaKind.EPISODE, episode.episodeId)
            },
            onDownloadAction = { episode, item, action ->
                performDownloadAction(
                    item = item,
                    sourceId = episode.sourceId,
                    mediaKind = LibraryMediaKind.EPISODE,
                    contentId = episode.episodeId,
                    title = "${episode.seriesName} • ${episode.title}",
                    action = action,
                )
            },
            modifier = modifier,
        )

        else -> LibraryHome(
            catalog = catalog,
            downloads = downloads,
            errorMessage = resolutionError,
            onContinueResume = { item ->
                when (item.mediaKind) {
                    LibraryMediaKind.MOVIE -> startMovie(item.contentId, LibraryStartMode.RESUME)
                    LibraryMediaKind.EPISODE -> startEpisode(item.contentId, LibraryStartMode.RESUME)
                }
            },
            onContinueBeginning = { item ->
                when (item.mediaKind) {
                    LibraryMediaKind.MOVIE -> startMovie(item.contentId, LibraryStartMode.BEGINNING)
                    LibraryMediaKind.EPISODE -> startEpisode(item.contentId, LibraryStartMode.BEGINNING)
                }
            },
            onMovieSelected = { movie ->
                resolutionError = null
                selectedMovieId = movie.movieId
            },
            onSeriesSelected = { series ->
                resolutionError = null
                selectedSeriesId = series.seriesId
            },
            onDownloadedAction = { media, item, action ->
                performDownloadAction(
                    item = item,
                    sourceId = media.sourceId,
                    mediaKind = media.mediaKind,
                    contentId = media.contentId,
                    title = media.title,
                    action = action,
                )
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun LibraryHome(
    catalog: LibraryCatalog?,
    downloads: List<DownloadItem>,
    errorMessage: String?,
    onContinueResume: (ContinueWatchingItem) -> Unit,
    onContinueBeginning: (ContinueWatchingItem) -> Unit,
    onMovieSelected: (LibraryMovie) -> Unit,
    onSeriesSelected: (LibrarySeries) -> Unit,
    onDownloadedAction: (LibraryDownloadedMedia, DownloadItem, DownloadAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedMovieCategoryKey by remember(catalog?.activeSourceId) { mutableStateOf<String?>(null) }
    var selectedSeriesCategoryKey by remember(catalog?.activeSourceId) { mutableStateOf<String?>(null) }
    val rawMovieCategories = catalog?.movieCategories.orEmpty()
    val rawSeriesCategories = catalog?.seriesCategories.orEmpty()
    val movieCategories = LibraryBrowsePolicy.visibleCategories(rawMovieCategories)
    val seriesCategories = LibraryBrowsePolicy.visibleCategories(rawSeriesCategories)
    val activeMovieCategoryKey = LibraryBrowsePolicy.activeCategoryKey(movieCategories, selectedMovieCategoryKey)
    val activeSeriesCategoryKey = LibraryBrowsePolicy.activeCategoryKey(seriesCategories, selectedSeriesCategoryKey)
    val visibleMovies = catalog?.movies.orEmpty().let { movies ->
        activeMovieCategoryKey?.let { key -> movies.filter { it.categoryKey == key } } ?: movies
    }
    val visibleSeries = catalog?.series.orEmpty().let { series ->
        activeSeriesCategoryKey?.let { key -> series.filter { it.categoryKey == key } } ?: series
    }

    LaunchedEffect(movieCategories, selectedMovieCategoryKey) {
        val resolvedCategoryKey = LibraryBrowsePolicy.activeCategoryKey(movieCategories, selectedMovieCategoryKey)
        if (selectedMovieCategoryKey != resolvedCategoryKey) {
            selectedMovieCategoryKey = resolvedCategoryKey
        }
    }
    LaunchedEffect(seriesCategories, selectedSeriesCategoryKey) {
        val resolvedCategoryKey = LibraryBrowsePolicy.activeCategoryKey(seriesCategories, selectedSeriesCategoryKey)
        if (selectedSeriesCategoryKey != resolvedCategoryKey) {
            selectedSeriesCategoryKey = resolvedCategoryKey
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        OwnPlayTopBar(showTagline = false)

        Column(
            modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
        ) {
            if (errorMessage != null) {
                OwnPlayStatePanel(title = "Action unavailable", message = errorMessage)
            }

            LibraryShelfHeader(
                title = "Continue Watching",
                actionLabel = catalog?.continueWatching?.size?.takeIf { it > 0 }?.let { "$it in progress" },
            )
            when {
                catalog == null -> OwnPlayStatePanel(
                    title = "Loading Library",
                    message = "Reading the active source and saved progress.",
                )

                catalog.activeSourceId == null -> OwnPlayStatePanel(
                    title = "No active source",
                    message = "Add or select a source in Settings to populate your Library.",
                )

                catalog.continueWatching.isEmpty() -> OwnPlayStatePanel(
                    title = "Nothing to resume yet",
                    message = "Movies and episodes with saved progress will appear here.",
                )

                else -> ContinueWatchingRow(
                    items = catalog.continueWatching,
                    onResume = onContinueResume,
                    onBeginning = onContinueBeginning,
                )
            }

            Spacer(modifier = Modifier.height(OwnPlaySpacing.Sm))
            LibraryShelfHeader(
                title = "Movies",
                actionLabel = catalog?.movies?.size?.takeIf { it > 0 }?.let { "${compactLibraryCount(it)} titles" },
            )
            if (movieCategories.isNotEmpty()) {
                LibraryCategoryStrip(
                    categories = movieCategories,
                    selectedCategoryKey = activeMovieCategoryKey,
                    onSelected = { selectedMovieCategoryKey = it },
                )
            }
            when {
                catalog != null && catalog.activeSourceId != null && catalog.movies.isEmpty() -> OwnPlayStatePanel(
                    title = "No movies available",
                    message = "Refresh ${catalog.activeSourceName ?: "the active source"} to load movie metadata.",
                )

                visibleMovies.isNotEmpty() -> MovieRow(
                    movies = visibleMovies,
                    onMovieSelected = onMovieSelected,
                )

                catalog != null && catalog.movies.isNotEmpty() -> OwnPlayStatePanel(
                    title = "No movies in this category",
                    message = "Choose another provider category.",
                )
            }

            Spacer(modifier = Modifier.height(OwnPlaySpacing.Sm))
            LibraryShelfHeader(
                title = "Series",
                actionLabel = catalog?.series?.size?.takeIf { it > 0 }?.let { "${compactLibraryCount(it)} titles" },
            )
            if (seriesCategories.isNotEmpty()) {
                LibraryCategoryStrip(
                    categories = seriesCategories,
                    selectedCategoryKey = activeSeriesCategoryKey,
                    onSelected = { selectedSeriesCategoryKey = it },
                )
            }
            when {
                catalog != null && catalog.activeSourceId != null && catalog.series.isEmpty() -> OwnPlayStatePanel(
                    title = "No series available",
                    message = "Refresh ${catalog.activeSourceName ?: "the active source"} to load series metadata.",
                )

                visibleSeries.isNotEmpty() -> SeriesRow(
                    seriesItems = visibleSeries,
                    onSeriesSelected = onSeriesSelected,
                )

                catalog != null && catalog.series.isNotEmpty() -> OwnPlayStatePanel(
                    title = "No series in this category",
                    message = "Choose another provider category.",
                )
            }

            Spacer(modifier = Modifier.height(OwnPlaySpacing.Sm))
            LibraryShelfHeader(title = "Downloaded Media")
            if (catalog != null && catalog.activeSourceId != null && catalog.downloadedMedia.isEmpty()) {
                OwnPlayStatePanel(
                    title = "No completed downloads",
                    message = "Completed media appears here after its offline file passes integrity verification.",
                )
            } else if (!catalog?.downloadedMedia.isNullOrEmpty()) {
                DownloadedRow(
                    mediaItems = catalog?.downloadedMedia.orEmpty(),
                    downloads = downloads,
                    onAction = onDownloadedAction,
                )
            }

            Spacer(modifier = Modifier.height(OwnPlaySpacing.Xl))
        }
    }
}

@Composable
private fun LibraryCategoryStrip(
    categories: List<LibraryCategory>,
    selectedCategoryKey: String?,
    onSelected: (String?) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        items(categories, key = { it.categoryKey }) { category ->
            LibraryFilterTab(
                label = category.name,
                selected = selectedCategoryKey == category.categoryKey,
                onClick = { onSelected(category.categoryKey) },
            )
        }
    }
}

@Composable
private fun ContinueWatchingRow(
    items: List<ContinueWatchingItem>,
    onResume: (ContinueWatchingItem) -> Unit,
    onBeginning: (ContinueWatchingItem) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { "${it.mediaKind}:${it.contentId}" }) { item ->
            ContinueWatchingCard(
                item = item,
                onResume = { onResume(item) },
                onBeginning = { onBeginning(item) },
            )
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    item: ContinueWatchingItem,
    onResume: () -> Unit,
    onBeginning: () -> Unit,
) {
    val progress = if (item.durationMs > 0L) {
        (item.positionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val remainingMs = (item.durationMs - item.positionMs).coerceAtLeast(0L)

    Surface(
        modifier = Modifier
            .width(284.dp)
            .clickable(onClick = onResume),
        color = Color.Transparent,
        shape = OwnPlayShapeTokens.Medium,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.78f)
                .clip(OwnPlayShapeTokens.Medium)
                .background(OwnPlayColors.SurfaceElevated),
        ) {
            RemoteArtwork(
                locator = item.artworkUrl,
                contentDescription = "${item.title} artwork",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.08f),
                            0.44f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.92f),
                        ),
                    ),
            )

            LibraryIconAction(
                glyph = LibraryActionGlyph.RESTART,
                contentDescription = "Start ${item.title} over",
                visualSize = 32.dp,
                onClick = onBeginning,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, end = 60.dp, bottom = 13.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.mediaKind.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = OwnPlayColors.Accent,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f),
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = "${formatDuration(remainingMs)} left",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.58f),
                        maxLines = 1,
                    )
                }
            }

            LibraryIconAction(
                glyph = LibraryActionGlyph.PLAY,
                contentDescription = "Resume ${item.title}",
                emphasized = true,
                visualSize = 36.dp,
                onClick = onResume,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 8.dp),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.16f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .background(OwnPlayColors.Accent),
                )
            }
        }
    }
}

@Composable
private fun MovieRow(movies: List<LibraryMovie>, onMovieSelected: (LibraryMovie) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
    ) {
        items(
            items = movies,
            key = { movie -> movie.movieId },
        ) { movie ->
            PosterCard(
                title = movie.name,
                artworkUrl = movie.posterUrl,
                eyebrow = movie.rating?.let { "★ $it" } ?: "MOVIE",
                onClick = { onMovieSelected(movie) },
            )
        }
    }
}

@Composable
private fun SeriesRow(seriesItems: List<LibrarySeries>, onSeriesSelected: (LibrarySeries) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
    ) {
        items(
            items = seriesItems,
            key = { series -> series.seriesId },
        ) { series ->
            PosterCard(
                title = series.name,
                artworkUrl = series.posterUrl,
                eyebrow = series.rating?.let { "★ $it" } ?: "SERIES",
                onClick = { onSeriesSelected(series) },
            )
        }
    }
}

@Composable
private fun PosterCard(
    title: String,
    artworkUrl: String?,
    eyebrow: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(138.dp)
            .aspectRatio(0.68f)
            .clip(OwnPlayShapeTokens.Medium)
            .background(OwnPlayColors.SurfaceElevated)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (artworkUrl.isNullOrBlank()) {
            Text(
                text = title,
                modifier = Modifier.padding(OwnPlaySpacing.Md),
                style = MaterialTheme.typography.labelLarge,
                color = OwnPlayColors.TextSecondary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
            )
        } else {
            RemoteArtwork(
                locator = artworkUrl,
                contentDescription = "$title poster",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.52f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.94f),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelSmall,
                color = OwnPlayColors.Accent.copy(alpha = 0.88f),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun DownloadedRow(
    mediaItems: List<LibraryDownloadedMedia>,
    downloads: List<DownloadItem>,
    onAction: (LibraryDownloadedMedia, DownloadItem, DownloadAction) -> Unit,
) {
    val byId = remember(downloads) { downloads.associateBy { it.downloadId } }
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
    ) {
        items(
            items = mediaItems,
            key = { media -> media.downloadId },
        ) { media ->
            Surface(
                modifier = Modifier.width(244.dp),
                color = OwnPlayColors.Surface.copy(alpha = 0.52f),
                shape = OwnPlayShapeTokens.Medium,
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .height(38.dp)
                                .clip(OwnPlayShapeTokens.Action)
                                .background(OwnPlayColors.Accent.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "✓",
                                style = MaterialTheme.typography.titleMedium,
                                color = OwnPlayColors.Accent,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = media.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = OwnPlayColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                            )
                            Text(
                                text = "Available offline",
                                style = MaterialTheme.typography.bodySmall,
                                color = OwnPlayColors.TextMuted,
                            )
                        }
                    }
                    byId[media.downloadId]?.let { item ->
                        DownloadControls(
                            item = item,
                            onAction = { action -> onAction(media, item, action) },
                            compact = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieDetail(
    movie: LibraryMovie,
    downloadItem: DownloadItem?,
    errorMessage: String?,
    preferResume: Boolean,
    onBack: () -> Unit,
    onResume: () -> Unit,
    onBeginning: () -> Unit,
    onDownloadAction: (DownloadAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        LibraryHero(
            title = movie.name,
            label = "MOVIE",
            rating = movie.rating,
            artworkUrl = movie.backdropUrl ?: movie.posterUrl,
            onBack = onBack,
        )
        Column(
            modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
        ) {
            if (errorMessage != null) {
                OwnPlayStatePanel(title = "Action unavailable", message = errorMessage)
            }
            if (movie.resumePositionMs != null) {
                PlaybackChoiceButtons(
                    preferResume = preferResume,
                    onResume = onResume,
                    onBeginning = onBeginning,
                )
            } else {
                LibraryPrimaryAction(
                    text = "Play",
                    onClick = onBeginning,
                    modifier = Modifier.fillMaxWidth(0.44f),
                    glyph = LibraryActionGlyph.PLAY,
                )
            }
            DownloadControls(item = downloadItem, onAction = onDownloadAction, compact = true)
            Spacer(modifier = Modifier.height(OwnPlaySpacing.Xl))
        }
    }
}

@Composable
private fun SeriesDetail(
    series: LibrarySeries,
    detail: LibrarySeriesDetail?,
    warning: String?,
    errorMessage: String?,
    preferResume: Boolean,
    onBack: () -> Unit,
    onResumeEpisode: (LibraryEpisode) -> Unit,
    onBeginningEpisode: (LibraryEpisode) -> Unit,
    downloadForEpisode: (LibraryEpisode) -> DownloadItem?,
    onDownloadAction: (LibraryEpisode, DownloadItem?, DownloadAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        LibraryHero(
            title = series.name,
            label = "SERIES",
            rating = series.rating,
            artworkUrl = series.backdropUrl ?: series.posterUrl,
            onBack = onBack,
        )
        Column(
            modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
        ) {
            series.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OwnPlayColors.TextSecondary,
                )
            }
            if (warning != null) {
                OwnPlayStatePanel(title = "Using cached episodes", message = warning)
            }
            if (errorMessage != null) {
                OwnPlayStatePanel(title = "Action unavailable", message = errorMessage)
            }
            LibraryShelfHeader(title = "Episodes")
            when {
                detail == null && errorMessage == null -> OwnPlayStatePanel(
                    title = "Loading episodes",
                    message = "Refreshing episode metadata for ${series.name}.",
                )

                detail?.episodes.isNullOrEmpty() -> OwnPlayStatePanel(
                    title = "No episodes available",
                    message = "The source did not return playable episodes for this series.",
                )

                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    detail?.episodes.orEmpty().forEach { episode ->
                        val downloadItem = downloadForEpisode(episode)
                        EpisodeRow(
                            episode = episode,
                            downloadItem = downloadItem,
                            preferResume = preferResume,
                            onResume = { onResumeEpisode(episode) },
                            onBeginning = { onBeginningEpisode(episode) },
                            onDownloadAction = { action -> onDownloadAction(episode, downloadItem, action) },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(OwnPlaySpacing.Xl))
        }
    }
}

@Composable
private fun LibraryHero(
    title: String,
    label: String,
    rating: String?,
    artworkUrl: String?,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.55f)
            .background(OwnPlayColors.SurfaceElevated),
        contentAlignment = Alignment.BottomStart,
    ) {
        RemoteArtwork(
            locator = artworkUrl,
            contentDescription = "$title backdrop",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.18f),
                        0.44f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.94f),
                    ),
                ),
        )
        LibraryIconAction(
            glyph = LibraryActionGlyph.BACK,
            contentDescription = "Back to Library",
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(OwnPlaySpacing.Md),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = OwnPlayColors.Accent,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
            rating?.let {
                Text(
                    text = "★ $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.72f),
                )
            }
        }
    }
}

@Composable
private fun RemoteArtwork(
    locator: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = locator) {
        value = locator?.takeIf { it.isNotBlank() }?.let { loadLibraryArtwork(it) }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    } else {
        Box(modifier = modifier.background(OwnPlayColors.SurfaceElevated))
    }
}

private suspend fun loadLibraryArtwork(locator: String) = withContext(Dispatchers.IO) {
    runCatching {
        val connection = URL(locator).openConnection() as? HttpURLConnection ?: return@runCatching null
        connection.connectTimeout = 4_000
        connection.readTimeout = 5_000
        connection.instanceFollowRedirects = true
        try {
            if (connection.responseCode !in 200..299) return@runCatching null
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8_192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count <= 0) break
                    total += count
                    if (total > MAX_LIBRARY_ARTWORK_BYTES) return@runCatching null
                    output.write(buffer, 0, count)
                }
            }
            val bytes = output.toByteArray()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

private const val MAX_LIBRARY_ARTWORK_BYTES = 4 * 1024 * 1024

@Composable
private fun EpisodeRow(
    episode: LibraryEpisode,
    downloadItem: DownloadItem?,
    preferResume: Boolean,
    onResume: () -> Unit,
    onBeginning: () -> Unit,
    onDownloadAction: (DownloadAction) -> Unit,
) {
    val hasProgress = episode.resumePositionMs != null
    val primaryIsResume = hasProgress && preferResume
    val primaryLabel = if (primaryIsResume) "Resume" else "Play"
    val primaryAction = if (primaryIsResume) onResume else onBeginning

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = OwnPlayColors.Surface.copy(alpha = 0.40f),
        shape = OwnPlayShapeTokens.Medium,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.width(54.dp)) {
                    Text(
                        text = "S${episode.seasonNumber}",
                        style = MaterialTheme.typography.labelMedium,
                        color = OwnPlayColors.TextMuted,
                    )
                    Text(
                        text = "E${episode.episodeNumber}",
                        style = MaterialTheme.typography.titleMedium,
                        color = OwnPlayColors.Accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = OwnPlayColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                    )
                    episode.durationMs?.let { duration ->
                        Text(
                            text = formatDuration(duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = OwnPlayColors.TextMuted,
                        )
                    }
                }
                LibraryIconAction(
                    glyph = LibraryActionGlyph.PLAY,
                    contentDescription = "$primaryLabel ${episode.title}",
                    emphasized = true,
                    onClick = primaryAction,
                )
            }

            if (hasProgress) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs),
                ) {
                    LibrarySecondaryAction(
                        text = if (primaryIsResume) "Start Over" else "Resume",
                        onClick = if (primaryIsResume) onBeginning else onResume,
                        modifier = Modifier.weight(0.55f),
                        glyph = if (primaryIsResume) LibraryActionGlyph.RESTART else LibraryActionGlyph.PLAY,
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        DownloadControls(item = downloadItem, onAction = onDownloadAction, compact = true)
                    }
                }
            } else {
                DownloadControls(item = downloadItem, onAction = onDownloadAction, compact = true)
            }
        }
    }
}

@Composable
private fun PlaybackChoiceButtons(
    preferResume: Boolean,
    onResume: () -> Unit,
    onBeginning: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (preferResume) {
            LibraryPrimaryAction(
                text = "Resume",
                onClick = onResume,
                glyph = LibraryActionGlyph.PLAY,
            )
            LibrarySecondaryAction(
                text = "Start Over",
                onClick = onBeginning,
                glyph = LibraryActionGlyph.RESTART,
            )
        } else {
            LibraryPrimaryAction(
                text = "Start Over",
                onClick = onBeginning,
                glyph = LibraryActionGlyph.RESTART,
            )
            LibrarySecondaryAction(
                text = "Resume",
                onClick = onResume,
                glyph = LibraryActionGlyph.PLAY,
            )
        }
    }
}

@Composable
private fun LibraryFullscreenPlayer(
    playback: ResolvedLibraryPlayback,
    libraryRepository: LibraryRepository,
    playbackController: PlaybackController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerState by playbackController.state.collectAsState()
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    var overlayVisible by remember(playback.contentId, playback.offline) { mutableStateOf(true) }
    var audioSelectorVisible by remember(playback.contentId, playback.offline) { mutableStateOf(false) }
    var pendingSeekMs by remember(playback.contentId, playback.offline) { mutableStateOf<Long?>(null) }

    suspend fun persistSnapshot(snapshot: PlaybackSnapshot) {
        if (snapshot.mediaId != playback.contentId) return
        val duration = snapshot.durationMs ?: playback.knownDurationMs ?: return
        if (duration <= 0L) return
        libraryRepository.saveProgress(
            PlaybackProgressUpdate(
                sourceId = playback.sourceId,
                mediaKind = playback.mediaKind,
                contentId = playback.contentId,
                positionMs = snapshot.positionMs,
                durationMs = duration,
                ended = snapshot.phase == PlaybackPhase.ENDED,
            ),
        )
    }

    suspend fun persistCurrent() {
        persistSnapshot(playbackController.currentSnapshot())
    }

    fun closePlayer() {
        scope.launch {
            persistCurrent()
            playbackController.stop(clearMedia = true)
            onClose()
        }
    }

    BackHandler(onBack = ::closePlayer)

    LaunchedEffect(
        playback.contentId,
        playback.offline,
        overlayVisible,
        playerState.isPlaying,
        playerState.phase,
    ) {
        if (overlayVisible && LibraryPlayerControlsPolicy.shouldAutoHide(playerState)) {
            delay(4_000)
            if (LibraryPlayerControlsPolicy.shouldAutoHide(playbackController.currentSnapshot())) {
                overlayVisible = false
            }
        }
    }

    LaunchedEffect(overlayVisible) {
        if (!overlayVisible) audioSelectorVisible = false
    }

    LaunchedEffect(playback.contentId, playback.offline) {
        var persistCountdown = 0
        while (true) {
            delay(2_000)
            val snapshot = playbackController.currentSnapshot()
            persistCountdown += 1
            if (persistCountdown >= 3) {
                persistSnapshot(snapshot)
                persistCountdown = 0
            }
        }
    }

    LaunchedEffect(playback.contentId, playback.offline, playerState.phase) {
        if (playerState.mediaId == playback.contentId && playerState.phase == PlaybackPhase.READY) {
            persistSnapshot(playerState)
        } else if (playerState.mediaId == playback.contentId && playerState.phase == PlaybackPhase.ENDED) {
            persistSnapshot(playerState)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        LibraryPlaybackSurface(
            playbackController = playbackController,
            controllerScope = scope,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .playerLocalVerticalControls(playbackController, scope)
                .clickable(interactionSource = interactionSource, indication = null) {
                    overlayVisible = !overlayVisible
                },
        )

        PlayerLocalControlHudOverlay(
            modifier = Modifier.align(Alignment.Center),
        )

        if (overlayVisible) {
            PlayerGlassScrims()

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
            ) {
                PlayerGlassIconAction(
                    glyph = PlayerGlassGlyph.BACK,
                    contentDescription = "Back",
                    onClick = ::closePlayer,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = playback.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                    )
                    Text(
                        text = playback.subtitle
                            ?: if (playback.offline) "Offline" else playback.mediaKind.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.68f),
                        maxLines = 1,
                    )
                }
            }

            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerGlassIconAction(
                    glyph = PlayerGlassGlyph.REWIND_10,
                    contentDescription = "Rewind 10 seconds",
                    onClick = {
                        scope.launch {
                            playbackController.seekTo((playerState.positionMs - 10_000L).coerceAtLeast(0L))
                        }
                    },
                )
                PlayerGlassIconAction(
                    glyph = if (playerState.playWhenReady) PlayerGlassGlyph.PAUSE else PlayerGlassGlyph.PLAY,
                    contentDescription = if (playerState.playWhenReady) "Pause" else "Play",
                    emphasized = true,
                    onClick = {
                        scope.launch { playbackController.setPlayWhenReady(!playerState.playWhenReady) }
                    },
                )
                PlayerGlassIconAction(
                    glyph = PlayerGlassGlyph.FORWARD_10,
                    contentDescription = "Forward 10 seconds",
                    onClick = {
                        scope.launch {
                            val upper = playerState.durationMs ?: playback.knownDurationMs ?: Long.MAX_VALUE
                            playbackController.seekTo((playerState.positionMs + 10_000L).coerceAtMost(upper))
                        }
                    },
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.92f)
                    .padding(bottom = OwnPlaySpacing.Md),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val duration = playerState.durationMs ?: playback.knownDurationMs
                if (duration != null && duration > 0L) {
                    val seekPosition = (pendingSeekMs ?: playerState.positionMs).coerceIn(0L, duration)
                    PlayerGlassSeekBar(
                        fraction = seekPosition.toFloat() / duration.toFloat(),
                        onFractionChange = { fraction ->
                            pendingSeekMs = (duration.toDouble() * fraction.toDouble()).toLong().coerceIn(0L, duration)
                        },
                        onChangeFinished = {
                            val destination = pendingSeekMs
                            pendingSeekMs = null
                            if (destination != null) {
                                scope.launch { playbackController.seekTo(destination) }
                            }
                        },
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            formatDuration(seekPosition),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f),
                        )
                        Text(
                            formatDuration(duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f),
                        )
                    }
                } else {
                    Text(
                        text = when (playerState.phase) {
                            PlaybackPhase.BUFFERING -> "Buffering…"
                            PlaybackPhase.ERROR -> "Playback unavailable"
                            else -> "Preparing duration…"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (playerState.phase == PlaybackPhase.ERROR) {
                        PlayerGlassPillAction(
                            text = "Retry",
                            onClick = { scope.launch { playbackController.retry() } },
                        )
                    }
                    if (playerState.audioTracks.isNotEmpty()) {
                        PlayerGlassPillAction(
                            text = "Audio",
                            emphasized = audioSelectorVisible,
                            onClick = { audioSelectorVisible = !audioSelectorVisible },
                        )
                    }
                }
            }

            if (audioSelectorVisible && playerState.audioTracks.isNotEmpty()) {
                AudioTrackSelectorPanel(
                    tracks = playerState.audioTracks,
                    onSelect = { selectionId ->
                        scope.launch { playbackController.selectAudioTrack(selectionId) }
                        audioSelectorVisible = false
                    },
                    onDismiss = { audioSelectorVisible = false },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(OwnPlaySpacing.Lg),
                )
            }
        }
    }
}

@Composable
private fun LibraryPlaybackSurface(
    playbackController: PlaybackController,
    controllerScope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val surfaceView = remember(context) {
        SurfaceView(context).apply { keepScreenOn = true }
    }

    AndroidView(factory = { surfaceView }, modifier = modifier)

    DisposableEffect(playbackController, surfaceView) {
        controllerScope.launch {
            playbackController.bindVideoTarget(VideoTarget.FULLSCREEN, surfaceView)
        }
        onDispose {
            controllerScope.launch {
                playbackController.unbindVideoTarget(VideoTarget.FULLSCREEN, surfaceView)
            }
        }
    }
}

private fun ResolvedLibraryPlayback.toLoadRequest(): PlaybackLoadRequest = PlaybackLoadRequest(
    media = PlaybackMedia(
        id = contentId,
        uri = uri,
        title = title,
        kind = if (offline) {
            PlaybackKind.OFFLINE
        } else {
            when (mediaKind) {
                LibraryMediaKind.MOVIE -> PlaybackKind.MOVIE
                LibraryMediaKind.EPISODE -> PlaybackKind.EPISODE
            }
        },
        streamFormat = streamFormat,
    ),
    start = start,
)

private fun compactLibraryCount(count: Int): String = when {
    count >= 1_000_000 -> {
        val whole = count / 1_000_000
        val decimal = (count % 1_000_000) / 100_000
        if (decimal == 0) "${whole}M" else "${whole}.${decimal}M"
    }

    count >= 1_000 -> {
        val whole = count / 1_000
        val decimal = (count % 1_000) / 100
        if (decimal == 0) "${whole}K" else "${whole}.${decimal}K"
    }

    else -> count.toString()
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private data class DownloadContentKey(
    val sourceId: String,
    val mediaKind: LibraryMediaKind,
    val contentId: String,
)
