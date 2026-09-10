package app.ownplay.mobile.feature.library.ui

import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayPanel
import app.ownplay.mobile.design.OwnPlayPrimaryButton
import app.ownplay.mobile.design.OwnPlaySecondaryButton
import app.ownplay.mobile.design.OwnPlaySectionHeader
import app.ownplay.mobile.design.OwnPlayShapeTokens
import app.ownplay.mobile.design.OwnPlaySpacing
import app.ownplay.mobile.design.OwnPlayStatePanel
import app.ownplay.mobile.design.OwnPlayTopBar
import app.ownplay.mobile.design.OwnPlayWordmark
import app.ownplay.mobile.feature.library.domain.ContinueWatchingItem
import app.ownplay.mobile.feature.library.domain.LibraryCatalog
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
import kotlin.math.roundToLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LibraryShell(
    libraryRepository: LibraryRepository,
    playbackController: PlaybackController,
    onFullscreenChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val catalogFlow = remember(libraryRepository) { libraryRepository.observeCatalog() }
    val catalog by catalogFlow.collectAsState(initial = null)
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

    fun startMovie(movieId: String, startMode: LibraryStartMode) {
        scope.launch {
            resolutionError = null
            when (val resolved = libraryRepository.resolveMoviePlayback(movieId, startMode)) {
                is LibraryPlaybackResolution.Success -> {
                    val value = resolved.value
                    playbackController.load(value.toLoadRequest())
                    activePlayback = value
                }

                is LibraryPlaybackResolution.Failure -> {
                    resolutionError = resolved.safeMessage
                }
            }
        }
    }

    fun startEpisode(episodeId: String, startMode: LibraryStartMode) {
        scope.launch {
            resolutionError = null
            when (val resolved = libraryRepository.resolveEpisodePlayback(episodeId, startMode)) {
                is LibraryPlaybackResolution.Success -> {
                    val value = resolved.value
                    playbackController.load(value.toLoadRequest())
                    activePlayback = value
                }

                is LibraryPlaybackResolution.Failure -> {
                    resolutionError = resolved.safeMessage
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

                is LibrarySeriesDetailResult.Failure -> {
                    detailError = result.safeMessage
                }
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

        selectedMovie != null -> MovieDetail(
            movie = selectedMovie,
            errorMessage = resolutionError,
            onBack = {
                selectedMovieId = null
                resolutionError = null
            },
            onResume = { startMovie(selectedMovie.movieId, LibraryStartMode.RESUME) },
            onBeginning = { startMovie(selectedMovie.movieId, LibraryStartMode.BEGINNING) },
            modifier = modifier,
        )

        selectedSeries != null -> SeriesDetail(
            series = selectedSeries,
            detail = seriesDetail,
            warning = seriesWarning,
            errorMessage = detailError ?: resolutionError,
            onBack = {
                selectedSeriesId = null
                seriesDetail = null
                seriesWarning = null
                detailError = null
                resolutionError = null
            },
            onResumeEpisode = { episode -> startEpisode(episode.episodeId, LibraryStartMode.RESUME) },
            onBeginningEpisode = { episode -> startEpisode(episode.episodeId, LibraryStartMode.BEGINNING) },
            modifier = modifier,
        )

        else -> LibraryHome(
            catalog = catalog,
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
            modifier = modifier,
        )
    }
}

@Composable
private fun LibraryHome(
    catalog: LibraryCatalog?,
    errorMessage: String?,
    onContinueResume: (ContinueWatchingItem) -> Unit,
    onContinueBeginning: (ContinueWatchingItem) -> Unit,
    onMovieSelected: (LibraryMovie) -> Unit,
    onSeriesSelected: (LibrarySeries) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        OwnPlayTopBar(showTagline = true)

        Column(
            modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Lg),
        ) {
            if (errorMessage != null) {
                OwnPlayStatePanel(
                    title = "Playback unavailable",
                    message = errorMessage,
                )
            }

            OwnPlaySectionHeader(title = "Continue Watching")
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

                else -> ContinueWatchingCard(
                    item = catalog.continueWatching.first(),
                    onResume = { onContinueResume(catalog.continueWatching.first()) },
                    onBeginning = { onContinueBeginning(catalog.continueWatching.first()) },
                )
            }

            OwnPlaySectionHeader(title = "Movies", actionLabel = catalog?.movies?.size?.takeIf { it > 0 }?.let { "$it titles" })
            if (catalog != null && catalog.activeSourceId != null && catalog.movies.isEmpty()) {
                OwnPlayStatePanel(
                    title = "No movies available",
                    message = "Refresh ${catalog.activeSourceName ?: "the active source"} to load movie metadata.",
                )
            } else if (!catalog?.movies.isNullOrEmpty()) {
                MovieRow(items = catalog!!.movies, onMovieSelected = onMovieSelected)
            }

            OwnPlaySectionHeader(title = "Series", actionLabel = catalog?.series?.size?.takeIf { it > 0 }?.let { "$it titles" })
            if (catalog != null && catalog.activeSourceId != null && catalog.series.isEmpty()) {
                OwnPlayStatePanel(
                    title = "No series available",
                    message = "Refresh ${catalog.activeSourceName ?: "the active source"} to load series metadata.",
                )
            } else if (!catalog?.series.isNullOrEmpty()) {
                SeriesRow(items = catalog!!.series, onSeriesSelected = onSeriesSelected)
            }

            OwnPlaySectionHeader(title = "Downloaded Media")
            if (catalog != null && catalog.activeSourceId != null && catalog.downloadedMedia.isEmpty()) {
                OwnPlayStatePanel(
                    title = "No completed downloads",
                    message = "Integrity-verified completed media will appear here when Downloads is implemented.",
                )
            } else if (!catalog?.downloadedMedia.isNullOrEmpty()) {
                DownloadedRow(items = catalog!!.downloadedMedia)
            }

            Spacer(modifier = Modifier.height(OwnPlaySpacing.Xl))
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
    OwnPlayPanel(modifier = Modifier.fillMaxWidth()) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .background(OwnPlayColors.SurfaceElevated)
                    .padding(OwnPlaySpacing.Lg),
                contentAlignment = Alignment.BottomStart,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs)) {
                    Text(
                        text = item.mediaKind.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = OwnPlayColors.Accent,
                    )
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = OwnPlayColors.TextPrimary,
                    )
                    item.subtitle?.let { subtitle ->
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = OwnPlayColors.TextSecondary,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.padding(OwnPlaySpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(OwnPlayShapeTokens.Small)
                            .background(OwnPlayColors.Divider),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(5.dp)
                                .background(OwnPlayColors.Accent),
                        )
                    }
                    Spacer(modifier = Modifier.width(OwnPlaySpacing.Md))
                    Text(
                        text = "${formatDuration((item.durationMs - item.positionMs).coerceAtLeast(0L))} left",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OwnPlayColors.TextSecondary,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
                ) {
                    OwnPlayPrimaryButton(
                        text = "Resume",
                        onClick = onResume,
                        modifier = Modifier.weight(1f),
                    )
                    OwnPlaySecondaryButton(
                        text = "Play from Beginning",
                        onClick = onBeginning,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MovieRow(
    items: List<LibraryMovie>,
    onMovieSelected: (LibraryMovie) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
    ) {
        items.forEach { movie ->
            PosterCard(
                title = movie.name,
                eyebrow = movie.rating?.let { "★ $it" } ?: "MOVIE",
                onClick = { onMovieSelected(movie) },
            )
        }
    }
}

@Composable
private fun SeriesRow(
    items: List<LibrarySeries>,
    onSeriesSelected: (LibrarySeries) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
    ) {
        items.forEach { series ->
            PosterCard(
                title = series.name,
                eyebrow = series.rating?.let { "★ $it" } ?: "SERIES",
                onClick = { onSeriesSelected(series) },
            )
        }
    }
}

@Composable
private fun PosterCard(
    title: String,
    eyebrow: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.width(132.dp),
        color = OwnPlayColors.Surface,
        shape = OwnPlayShapeTokens.Small,
        border = BorderStroke(1.dp, OwnPlayColors.Divider),
        tonalElevation = 0.dp,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.68f)
                    .background(OwnPlayColors.SurfaceElevated)
                    .padding(OwnPlaySpacing.Sm),
                contentAlignment = Alignment.BottomStart,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = OwnPlayColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                )
            }
            Text(
                text = eyebrow,
                modifier = Modifier.padding(OwnPlaySpacing.Sm),
                style = MaterialTheme.typography.labelMedium,
                color = OwnPlayColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun DownloadedRow(items: List<LibraryDownloadedMedia>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
    ) {
        items.forEach { item ->
            OwnPlayPanel(modifier = Modifier.width(220.dp)) {
                Column(
                    modifier = Modifier.padding(OwnPlaySpacing.Md),
                    verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.45f)
                            .clip(OwnPlayShapeTokens.Small)
                            .background(OwnPlayColors.SurfaceElevated),
                    )
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = OwnPlayColors.TextPrimary,
                    )
                    Text(
                        text = "●  Downloaded",
                        style = MaterialTheme.typography.labelMedium,
                        color = OwnPlayColors.Accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun MovieDetail(
    movie: LibraryMovie,
    errorMessage: String?,
    onBack: () -> Unit,
    onResume: () -> Unit,
    onBeginning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        OwnPlayTopBar(showTagline = false)
        Column(
            modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Lg),
        ) {
            Text(
                text = "‹ Library",
                modifier = Modifier.clickable(onClick = onBack),
                style = MaterialTheme.typography.labelLarge,
                color = OwnPlayColors.Accent,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(OwnPlayShapeTokens.Medium)
                    .background(OwnPlayColors.SurfaceElevated)
                    .padding(OwnPlaySpacing.Xl),
                contentAlignment = Alignment.BottomStart,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs)) {
                    Text(
                        text = "MOVIE",
                        style = MaterialTheme.typography.labelMedium,
                        color = OwnPlayColors.Accent,
                    )
                    Text(
                        text = movie.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = OwnPlayColors.TextPrimary,
                    )
                    movie.rating?.let { rating ->
                        Text(
                            text = "★ $rating",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OwnPlayColors.TextSecondary,
                        )
                    }
                }
            }
            if (errorMessage != null) {
                OwnPlayStatePanel(title = "Playback unavailable", message = errorMessage)
            }
            if (movie.resumePositionMs != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
                ) {
                    OwnPlayPrimaryButton(text = "Resume", onClick = onResume, modifier = Modifier.weight(1f))
                    OwnPlaySecondaryButton(
                        text = "Play from Beginning",
                        onClick = onBeginning,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                OwnPlayPrimaryButton(
                    text = "Play",
                    onClick = onBeginning,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
    onBack: () -> Unit,
    onResumeEpisode: (LibraryEpisode) -> Unit,
    onBeginningEpisode: (LibraryEpisode) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        OwnPlayTopBar(showTagline = false)
        Column(
            modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Lg),
        ) {
            Text(
                text = "‹ Library",
                modifier = Modifier.clickable(onClick = onBack),
                style = MaterialTheme.typography.labelLarge,
                color = OwnPlayColors.Accent,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(OwnPlayShapeTokens.Medium)
                    .background(OwnPlayColors.SurfaceElevated)
                    .padding(OwnPlaySpacing.Xl),
                contentAlignment = Alignment.BottomStart,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs)) {
                    Text(
                        text = "SERIES",
                        style = MaterialTheme.typography.labelMedium,
                        color = OwnPlayColors.Accent,
                    )
                    Text(
                        text = series.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = OwnPlayColors.TextPrimary,
                    )
                    series.rating?.let { rating ->
                        Text(
                            text = "★ $rating",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OwnPlayColors.TextSecondary,
                        )
                    }
                }
            }
            series.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = OwnPlayColors.TextSecondary,
                )
            }
            if (warning != null) {
                OwnPlayStatePanel(title = "Using cached episodes", message = warning)
            }
            if (errorMessage != null) {
                OwnPlayStatePanel(title = "Episodes unavailable", message = errorMessage)
            }
            OwnPlaySectionHeader(title = "Episodes")
            when {
                detail == null && errorMessage == null -> OwnPlayStatePanel(
                    title = "Loading episodes",
                    message = "Refreshing episode metadata for ${series.name}.",
                )

                detail?.episodes.isNullOrEmpty() -> OwnPlayStatePanel(
                    title = "No episodes available",
                    message = "The source did not return playable episodes for this series.",
                )

                else -> Column(verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm)) {
                    detail!!.episodes.forEach { episode ->
                        EpisodeRow(
                            episode = episode,
                            onResume = { onResumeEpisode(episode) },
                            onBeginning = { onBeginningEpisode(episode) },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(OwnPlaySpacing.Xl))
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: LibraryEpisode,
    onResume: () -> Unit,
    onBeginning: () -> Unit,
) {
    OwnPlayPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(OwnPlaySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "S${episode.seasonNumber} E${episode.episodeNumber}",
                    modifier = Modifier.width(72.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = OwnPlayColors.Accent,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = OwnPlayColors.TextPrimary,
                    )
                    episode.durationMs?.let { duration ->
                        Text(
                            text = formatDuration(duration),
                            style = MaterialTheme.typography.bodyMedium,
                            color = OwnPlayColors.TextSecondary,
                        )
                    }
                }
            }
            if (episode.resumePositionMs != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
                ) {
                    OwnPlayPrimaryButton(text = "Resume", onClick = onResume, modifier = Modifier.weight(1f))
                    OwnPlaySecondaryButton(
                        text = "Play from Beginning",
                        onClick = onBeginning,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                OwnPlayPrimaryButton(text = "Play", onClick = onBeginning, modifier = Modifier.fillMaxWidth())
            }
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
    var overlayVisible by remember(playback.contentId) { mutableStateOf(true) }
    var pendingSeekMs by remember(playback.contentId) { mutableStateOf<Long?>(null) }

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

    LaunchedEffect(playback.contentId, overlayVisible) {
        if (overlayVisible) {
            delay(4_000)
            overlayVisible = false
        }
    }

    LaunchedEffect(playback.contentId) {
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

    LaunchedEffect(playback.contentId, playerState.phase) {
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
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) {
                    overlayVisible = !overlayVisible
                },
        )

        if (overlayVisible) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = OwnPlaySpacing.Xl, vertical = OwnPlaySpacing.Lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
            ) {
                OwnPlayWordmark(showTagline = false)
                Text(
                    text = playback.mediaKind.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = OwnPlayColors.Accent,
                )
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(OwnPlaySpacing.Xl),
                color = OwnPlayColors.Surface.copy(alpha = 0.94f),
                shape = OwnPlayShapeTokens.Medium,
                border = BorderStroke(1.dp, OwnPlayColors.Divider),
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(OwnPlaySpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
                ) {
                    Text(
                        text = playback.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = OwnPlayColors.TextPrimary,
                    )
                    playback.subtitle?.let { subtitle ->
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = OwnPlayColors.TextSecondary,
                        )
                    }

                    val duration = playerState.durationMs ?: playback.knownDurationMs
                    if (duration != null && duration > 0L) {
                        val sliderPosition = (pendingSeekMs ?: playerState.positionMs)
                            .coerceIn(0L, duration)
                        Slider(
                            value = sliderPosition.toFloat(),
                            onValueChange = { value -> pendingSeekMs = value.roundToLong().coerceIn(0L, duration) },
                            onValueChangeFinished = {
                                val destination = pendingSeekMs
                                pendingSeekMs = null
                                if (destination != null) {
                                    scope.launch { playbackController.seekTo(destination) }
                                }
                            },
                            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = formatDuration(sliderPosition),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = OwnPlayColors.TextSecondary,
                            )
                            Text(
                                text = formatDuration(duration),
                                style = MaterialTheme.typography.bodyMedium,
                                color = OwnPlayColors.TextSecondary,
                            )
                        }
                    } else {
                        Text(
                            text = when (playerState.phase) {
                                PlaybackPhase.BUFFERING -> "Buffering…"
                                PlaybackPhase.ERROR -> "Playback unavailable"
                                else -> "Preparing duration…"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = OwnPlayColors.TextSecondary,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
                    ) {
                        OwnPlaySecondaryButton(
                            text = "−10s",
                            onClick = {
                                scope.launch {
                                    playbackController.seekTo((playerState.positionMs - 10_000L).coerceAtLeast(0L))
                                }
                            },
                            modifier = Modifier.weight(0.8f),
                        )
                        OwnPlayPrimaryButton(
                            text = if (playerState.playWhenReady) "Pause" else "Play",
                            onClick = {
                                scope.launch { playbackController.setPlayWhenReady(!playerState.playWhenReady) }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        OwnPlaySecondaryButton(
                            text = "+10s",
                            onClick = {
                                scope.launch {
                                    val upper = playerState.durationMs ?: playback.knownDurationMs ?: Long.MAX_VALUE
                                    playbackController.seekTo((playerState.positionMs + 10_000L).coerceAtMost(upper))
                                }
                            },
                            modifier = Modifier.weight(0.8f),
                        )
                        if (playerState.phase == PlaybackPhase.ERROR) {
                            OwnPlaySecondaryButton(
                                text = "Retry",
                                onClick = { scope.launch { playbackController.retry() } },
                                modifier = Modifier.weight(0.8f),
                            )
                        }
                    }
                    Text(
                        text = "BACK TO LIBRARY",
                        modifier = Modifier
                            .align(Alignment.End)
                            .clickable(onClick = ::closePlayer)
                            .padding(vertical = OwnPlaySpacing.Sm),
                        style = MaterialTheme.typography.labelLarge,
                        color = OwnPlayColors.Accent,
                    )
                }
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
        SurfaceView(context).apply {
            keepScreenOn = true
        }
    }

    AndroidView(
        factory = { surfaceView },
        modifier = modifier,
    )

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
        kind = when (mediaKind) {
            LibraryMediaKind.MOVIE -> PlaybackKind.MOVIE
            LibraryMediaKind.EPISODE -> PlaybackKind.EPISODE
        },
        streamFormat = streamFormat,
    ),
    start = start,
)

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