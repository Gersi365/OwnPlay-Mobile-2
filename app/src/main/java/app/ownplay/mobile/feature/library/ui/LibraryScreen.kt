package app.ownplay.mobile.feature.library.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ownplay.mobile.OwnPlayApplication
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.downloads.domain.DownloadRepository
import app.ownplay.mobile.downloads.domain.DownloadRequest
import app.ownplay.mobile.downloads.domain.DownloadMediaKind
import app.ownplay.mobile.design.OwnPlayFeaturePlaceholder
import app.ownplay.mobile.feature.library.data.LibraryArtworkLoader
import app.ownplay.mobile.feature.library.domain.LibraryCatalogSnapshot
import app.ownplay.mobile.feature.library.domain.LibraryContentKind
import app.ownplay.mobile.feature.library.domain.LibraryContinueWatchingItem
import app.ownplay.mobile.feature.library.domain.LibraryDetailRefreshResult
import app.ownplay.mobile.feature.library.domain.LibraryDetailStartPolicy
import app.ownplay.mobile.feature.library.domain.LibraryMovieSummary
import app.ownplay.mobile.feature.library.domain.LibraryRepository
import app.ownplay.mobile.feature.library.domain.LibrarySearchPolicy
import app.ownplay.mobile.feature.library.domain.LibrarySeriesDetail
import app.ownplay.mobile.feature.library.domain.LibrarySeriesSummary
import app.ownplay.mobile.feature.playback.data.Media3PlaybackEngine
import app.ownplay.mobile.feature.playback.domain.PlaybackPresentation
import app.ownplay.mobile.feature.playback.domain.PlaybackReadiness
import app.ownplay.mobile.feature.playback.domain.PlaybackSessionController
import app.ownplay.mobile.feature.playback.domain.PlaybackTarget
import app.ownplay.mobile.feature.playback.ui.PlaybackVideoSurface
import app.ownplay.mobile.sources.domain.SourceId
import app.ownplay.mobile.sources.domain.SourceSummary
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
) {
    val application = LocalContext.current.applicationContext as OwnPlayApplication
    val services = remember(application) { application.services }
    val activeSourceFlow = remember(services.sourceRepository) {
        services.sourceRepository.observeActiveSource()
    }
    val activeSource by activeSourceFlow.collectAsState(initial = null)
    val playbackState by services.playbackSessionController.state.collectAsState()

    val source = activeSource
    if (source == null) {
        OwnPlayFeaturePlaceholder(
            title = "Library",
            message = "Add a source in Settings to browse Movies and Series.",
            modifier = modifier,
        )
        return
    }

    LibrarySourceScreen(
        source = source,
        repository = services.libraryRepository,
        downloadRepository = services.downloadRepository,
        artworkLoader = services.libraryArtworkLoader,
        playbackSessionController = services.playbackSessionController,
        modifier = modifier,
    )

    val libraryTarget = playbackState.target as? PlaybackTarget.Library
    if (
        libraryTarget != null &&
        libraryTarget.sourceId == source.sourceId &&
        playbackState.presentation == PlaybackPresentation.FULLSCREEN
    ) {
        LibraryPlaybackFullscreenPresentation(
            target = libraryTarget,
            readiness = playbackState.readiness,
            playbackEngine = services.playbackEngine,
            onDismiss = services.playbackSessionController::clear,
        )
    }
}

@Composable
private fun LibrarySourceScreen(
    source: SourceSummary,
    repository: LibraryRepository,
    downloadRepository: DownloadRepository,
    artworkLoader: LibraryArtworkLoader,
    playbackSessionController: PlaybackSessionController,
    modifier: Modifier,
) {
    var selectedMovieId by remember(source.sourceId) { mutableStateOf<String?>(null) }
    var selectedSeriesId by remember(source.sourceId) { mutableStateOf<String?>(null) }

    val openMovieId = selectedMovieId
    if (openMovieId != null) {
        LibraryMovieDetailScreen(
            source = source,
            movieId = openMovieId,
            repository = repository,
            downloadRepository = downloadRepository,
            artworkLoader = artworkLoader,
            playbackSessionController = playbackSessionController,
            onBack = { selectedMovieId = null },
            modifier = modifier,
        )
        return
    }

    val openSeriesId = selectedSeriesId
    if (openSeriesId != null) {
        LibrarySeriesDetailScreen(
            source = source,
            seriesId = openSeriesId,
            repository = repository,
            downloadRepository = downloadRepository,
            artworkLoader = artworkLoader,
            playbackSessionController = playbackSessionController,
            onBack = { selectedSeriesId = null },
            modifier = modifier,
        )
        return
    }

    val downloadsFlow = remember(downloadRepository, source.sourceId) {
        downloadRepository.observeDownloads(source.sourceId)
    }
    val downloads by downloadsFlow.collectAsState(initial = emptyList())

    val catalogFlow = remember(repository, source.sourceId) {
        repository.observeCatalog(source.sourceId)
    }
    val catalog by catalogFlow.collectAsState(
        initial = LibraryCatalogSnapshot(
            movieCategories = emptyList(),
            seriesCategories = emptyList(),
            movies = emptyList(),
            series = emptyList(),
        ),
    )
    var searchQuery by remember(source.sourceId) { mutableStateOf("") }
    val visibleCatalog = remember(catalog, searchQuery) {
        LibrarySearchPolicy.filterCatalog(catalog, searchQuery)
    }
    val scope = rememberCoroutineScope()
    val movieCategoryNames = catalog.movieCategories.associate { it.categoryId to it.displayName }
    val seriesCategoryNames = catalog.seriesCategories.associate { it.categoryId to it.displayName }

    fun activate(target: PlaybackTarget.Library) {
        scope.launch { playbackSessionController.activateLibraryMedia(target) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Library",
                    color = OwnPlayColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(text = source.displayName, color = OwnPlayColors.TextSecondary)
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search Movies and Series") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (searchQuery.isBlank() && catalog.movies.isEmpty() && catalog.series.isEmpty()) {
            item {
                Text(
                    text = "No Movies or Series are available from this source.",
                    color = OwnPlayColors.TextMuted,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        } else if (
            searchQuery.isNotBlank() &&
            visibleCatalog.movies.isEmpty() &&
            visibleCatalog.series.isEmpty()
        ) {
            item {
                Text(
                    text = "No Movies or Series match your search.",
                    color = OwnPlayColors.TextMuted,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        if (visibleCatalog.continueWatching.isNotEmpty()) {
            item {
                LibrarySectionTitle("Continue Watching")
            }
            items(
                items = visibleCatalog.continueWatching,
                key = { item -> "continue:${item.contentKind}:${item.contentId}" },
            ) { item ->
                LibraryContinueWatchingRow(
                    item = item,
                    onResume = {
                        continueWatchingTarget(source.sourceId, item)?.let(::activate)
                    },
                )
            }
        }

        if (visibleCatalog.movies.isNotEmpty()) {
            item {
                LibrarySectionTitle("Movies")
            }
            items(visibleCatalog.movies, key = { "movie:${it.movieId}" }) { movie ->
                LibraryMovieRow(
                    movie = movie,
                    categoryName = movie.categoryId?.let(movieCategoryNames::get),
                    artworkLoader = artworkLoader,
                    onPlay = {
                        activate(
                            PlaybackTarget.Movie(
                                sourceId = source.sourceId,
                                movieId = movie.movieId,
                            ),
                        )
                    },
                    onDetails = { selectedMovieId = movie.movieId },
                    onFavorite = {
                        scope.launch {
                            repository.setFavorite(
                                sourceId = source.sourceId,
                                contentKind = LibraryContentKind.MOVIE,
                                contentId = movie.movieId,
                                favorite = !movie.favorite,
                            )
                        }
                    },
                )
            }
        }

        if (visibleCatalog.series.isNotEmpty()) {
            item {
                LibrarySectionTitle("Series")
            }
            items(visibleCatalog.series, key = { "series:${it.seriesId}" }) { series ->
                LibrarySeriesRow(
                    series = series,
                    categoryName = series.categoryId?.let(seriesCategoryNames::get),
                    artworkLoader = artworkLoader,
                    onOpen = { selectedSeriesId = series.seriesId },
                    onFavorite = {
                        scope.launch {
                            repository.setFavorite(
                                sourceId = source.sourceId,
                                contentKind = LibraryContentKind.SERIES,
                                contentId = series.seriesId,
                                favorite = !series.favorite,
                            )
                        }
                    },
                )
            }
        }

        if (searchQuery.isBlank() && downloads.isNotEmpty()) {
            item {
                LibrarySectionTitle("Downloaded Media")
            }
            items(
                items = downloads,
                key = { item -> "download:${item.downloadId}" },
            ) { item ->
                LibraryDownloadedMediaRow(item, downloadRepository, playbackSessionController)
            }
        }
    }
}

@Composable
private fun LibrarySectionTitle(title: String) {
    Text(
        text = title,
        color = OwnPlayColors.TextPrimary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun LibraryContinueWatchingRow(
    item: LibraryContinueWatchingItem,
    onResume: () -> Unit,
) {
    val context = when (item.contentKind) {
        LibraryContentKind.MOVIE -> "Movie"
        LibraryContentKind.EPISODE -> listOfNotNull(
            item.seriesTitle,
            item.seasonNumber?.let { season ->
                item.episodeNumber?.let { episode -> "S$season • E$episode" }
            },
        ).joinToString(" • ").ifBlank { "Episode" }
        LibraryContentKind.SERIES -> "Series"
    }

    Surface(
        color = OwnPlayColors.Surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = item.title,
                    color = OwnPlayColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(text = context, color = OwnPlayColors.TextSecondary)
            }
            TextButton(onClick = onResume) {
                Text("Resume")
            }
        }
    }
}

private fun continueWatchingTarget(
    sourceId: SourceId,
    item: LibraryContinueWatchingItem,
): PlaybackTarget.Library? = when (item.contentKind) {
    LibraryContentKind.MOVIE -> PlaybackTarget.Movie(sourceId, item.contentId)
    LibraryContentKind.EPISODE -> PlaybackTarget.Episode(sourceId, item.contentId)
    LibraryContentKind.SERIES -> null
}

@Composable
private fun LibrarySeriesDetailScreen(
    source: SourceSummary,
    seriesId: String,
    repository: LibraryRepository,
    downloadRepository: DownloadRepository,
    artworkLoader: LibraryArtworkLoader,
    playbackSessionController: PlaybackSessionController,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val detailFlow = remember(repository, source.sourceId, seriesId) {
        repository.observeSeriesDetail(source.sourceId, seriesId)
    }
    val detail by detailFlow.collectAsState(initial = null)
    val downloadsFlow = remember(downloadRepository, source.sourceId) {
        downloadRepository.observeDownloads(source.sourceId)
    }
    val downloads by downloadsFlow.collectAsState(initial = emptyList())
    val episodeDownloads = remember(downloads) {
        downloads.filter { it.mediaKind == DownloadMediaKind.EPISODE }.associateBy { it.contentId }
    }
    var refreshing by remember(source.sourceId, seriesId) { mutableStateOf(true) }
    var refreshResult by remember(source.sourceId, seriesId) {
        mutableStateOf<LibraryDetailRefreshResult?>(null)
    }
    var selectedEpisodeId by remember(source.sourceId, seriesId) { mutableStateOf<String?>(null) }
    var episodeSearchQuery by remember(source.sourceId, seriesId) { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(repository, source.sourceId, seriesId) {
        refreshing = true
        refreshResult = repository.refreshSeriesDetail(source.sourceId, seriesId)
        refreshing = false
    }

    LaunchedEffect(detail, episodeSearchQuery) {
        val current = detail ?: return@LaunchedEffect
        val visible = LibrarySearchPolicy.filterSeriesDetail(current, episodeSearchQuery)
        val selectedStillVisible = selectedEpisodeId?.let { selectedId ->
            visible.seasons.any { season ->
                season.episodes.any { episode -> episode.episodeId == selectedId }
            }
        } ?: false
        if (!selectedStillVisible) {
            selectedEpisodeId = LibraryDetailStartPolicy.firstAvailableEpisode(visible)?.episodeId
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onBack) {
                    Text("Back")
                }
                TextButton(
                    enabled = !refreshing,
                    onClick = {
                        refreshing = true
                        scope.launch {
                            refreshResult = repository.refreshSeriesDetail(source.sourceId, seriesId)
                            refreshing = false
                        }
                    },
                ) {
                    Text(if (refreshing) "Refreshing…" else "Refresh")
                }
            }
        }

        val currentDetail = detail
        if (currentDetail == null) {
            item {
                Text(
                    text = when {
                        refreshing -> "Loading Series details…"
                        refreshResult == LibraryDetailRefreshResult.UNAVAILABLE ->
                            "This Series is no longer available."
                        refreshResult == LibraryDetailRefreshResult.UNSUPPORTED_SOURCE ->
                            "Episode details are not supported for this source."
                        else -> "Series details are not available."
                    },
                    color = OwnPlayColors.TextMuted,
                )
            }
            return@LazyColumn
        }

        item {
            LibrarySeriesMetadata(
                detail = currentDetail,
                artworkLoader = artworkLoader,
                onFavorite = {
                    scope.launch {
                        repository.setFavorite(
                            sourceId = source.sourceId,
                            contentKind = LibraryContentKind.SERIES,
                            contentId = currentDetail.series.seriesId,
                            favorite = !currentDetail.series.favorite,
                        )
                    }
                },
            )
        }

        item {
            OutlinedTextField(
                value = episodeSearchQuery,
                onValueChange = { episodeSearchQuery = it },
                label = { Text("Search episodes") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        val visibleDetail = LibrarySearchPolicy.filterSeriesDetail(
            detail = currentDetail,
            query = episodeSearchQuery,
        )
        val selectedEpisode = LibraryDetailStartPolicy.selectedOrFirstAvailable(
            detail = visibleDetail,
            selectedEpisodeId = selectedEpisodeId,
        )
        item {
            TextButton(
                enabled = selectedEpisode != null,
                onClick = {
                    selectedEpisode?.let { episode ->
                        scope.launch {
                            playbackSessionController.activateLibraryMedia(
                                PlaybackTarget.Episode(
                                    sourceId = source.sourceId,
                                    episodeId = episode.episodeId,
                                ),
                            )
                        }
                    }
                },
            ) {
                Text("Play selected episode")
            }
        }

        selectedEpisode?.let { episode ->
            item(key = "selected-episode-download") {
                LibraryDownloadActions(
                    request = DownloadRequest(
                        source.sourceId, DownloadMediaKind.EPISODE, episode.episodeId, episode.title,
                    ),
                    item = episodeDownloads[episode.episodeId],
                    repository = downloadRepository,
                    playbackSessionController = playbackSessionController,
                )
            }
        }

        when (refreshResult) {
            LibraryDetailRefreshResult.FAILED -> item {
                Text(
                    text = "Could not refresh episode details. Showing cached details when available.",
                    color = OwnPlayColors.TextMuted,
                )
            }
            LibraryDetailRefreshResult.UNSUPPORTED_SOURCE -> item {
                Text(
                    text = "Episode details are not supported for this source.",
                    color = OwnPlayColors.TextMuted,
                )
            }
            else -> Unit
        }

        if (visibleDetail.seasons.isEmpty()) {
            item {
                Text(
                    text = when {
                        episodeSearchQuery.isNotBlank() -> "No episodes match your search."
                        refreshing -> "Refreshing episode details…"
                        else -> "No episodes are available for this Series."
                    },
                    color = OwnPlayColors.TextMuted,
                )
            }
        }

        visibleDetail.seasons.forEach { season ->
            item(key = "season:${season.seasonNumber}") {
                Text(
                    text = "Season ${season.seasonNumber}",
                    color = OwnPlayColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(
                items = season.episodes,
                key = { episode -> "episode:${episode.episodeId}" },
            ) { episode ->
                Surface(
                    color = OwnPlayColors.Surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedEpisodeId = episode.episodeId },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = "Episode ${episode.episodeNumber}",
                                color = OwnPlayColors.TextSecondary,
                            )
                            Text(
                                text = episode.title,
                                color = OwnPlayColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (selectedEpisodeId == episode.episodeId) {
                            Text("Selected", color = OwnPlayColors.TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySeriesMetadata(
    detail: LibrarySeriesDetail,
    artworkLoader: LibraryArtworkLoader,
    onFavorite: () -> Unit,
) {
    Surface(
        color = OwnPlayColors.Surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LibraryArtwork(
                url = detail.series.posterUrl,
                loader = artworkLoader,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = detail.series.title,
                    color = OwnPlayColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                detail.series.rating?.takeIf(String::isNotBlank)?.let {
                    Text(text = "Rating: $it", color = OwnPlayColors.TextSecondary)
                }
                detail.series.description?.takeIf(String::isNotBlank)?.let {
                    Text(text = it, color = OwnPlayColors.TextSecondary)
                }
                TextButton(onClick = onFavorite) {
                    Text(if (detail.series.favorite) "Unfavorite" else "Favorite")
                }
            }
        }
    }
}

@Composable
private fun LibraryMovieRow(
    movie: LibraryMovieSummary,
    categoryName: String?,
    artworkLoader: LibraryArtworkLoader,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    onFavorite: () -> Unit,
) {
    LibraryMediaRow(
        title = movie.title,
        posterUrl = movie.posterUrl,
        categoryName = categoryName,
        rating = movie.rating,
        favorite = movie.favorite,
        artworkLoader = artworkLoader,
        primaryActionLabel = "Play",
        onPrimaryAction = onPlay,
        onFavorite = onFavorite,
        secondaryActionLabel = "Details",
        onSecondaryAction = onDetails,
    )
}

@Composable
private fun LibrarySeriesRow(
    series: LibrarySeriesSummary,
    categoryName: String?,
    artworkLoader: LibraryArtworkLoader,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
) {
    LibraryMediaRow(
        title = series.title,
        posterUrl = series.posterUrl,
        categoryName = categoryName,
        rating = series.rating,
        favorite = series.favorite,
        artworkLoader = artworkLoader,
        primaryActionLabel = "Open details",
        onPrimaryAction = onOpen,
        onFavorite = onFavorite,
    )
}

@Composable
private fun LibraryMediaRow(
    title: String,
    posterUrl: String?,
    categoryName: String?,
    rating: String?,
    favorite: Boolean,
    artworkLoader: LibraryArtworkLoader,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    onFavorite: () -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Surface(
        color = OwnPlayColors.Surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LibraryArtwork(
                url = posterUrl,
                loader = artworkLoader,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = title,
                    color = OwnPlayColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                categoryName?.let {
                    Text(text = it, color = OwnPlayColors.TextSecondary)
                }
                rating?.takeIf(String::isNotBlank)?.let {
                    Text(text = "Rating: $it", color = OwnPlayColors.TextMuted)
                }
                TextButton(onClick = onPrimaryAction) {
                    Text(primaryActionLabel)
                }
                if (secondaryActionLabel != null && onSecondaryAction != null) {
                    TextButton(onClick = onSecondaryAction) {
                        Text(secondaryActionLabel)
                    }
                }
            }
            TextButton(onClick = onFavorite) {
                Text(if (favorite) "Unfavorite" else "Favorite")
            }
        }
    }
}

@Composable
private fun LibraryPlaybackFullscreenPresentation(
    target: PlaybackTarget.Library,
    readiness: PlaybackReadiness,
    playbackEngine: Media3PlaybackEngine,
    onDismiss: () -> Unit,
) {
    val title = when (target) {
        is PlaybackTarget.Movie -> "Movie playback"
        is PlaybackTarget.Episode -> "Episode playback"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = OwnPlayColors.Background,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                PlaybackVideoSurface(
                    playbackEngine = playbackEngine,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = title,
                        color = OwnPlayColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    LibraryPlaybackReadinessMessage(readiness, target.offlineDownloadId != null)
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryPlaybackReadinessMessage(readiness: PlaybackReadiness, offline: Boolean) {
    val message = when (readiness) {
        PlaybackReadiness.IDLE -> null
        PlaybackReadiness.PREPARING -> "Preparing playback…"
        PlaybackReadiness.PREPARED -> null
        PlaybackReadiness.UNAVAILABLE -> if (offline) {
            "Offline playback is unavailable. The downloaded file may be missing or damaged."
        } else {
            "Playback is unavailable for this item."
        }
    }
    if (message != null) {
        Text(text = message, color = OwnPlayColors.TextMuted)
    }
}
