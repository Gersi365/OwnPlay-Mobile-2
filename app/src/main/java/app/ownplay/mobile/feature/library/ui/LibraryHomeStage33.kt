package app.ownplay.mobile.feature.library.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ownplay.mobile.data.prefs.LibraryVisibilitySnapshot
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlaySearchField
import app.ownplay.mobile.design.OwnPlayShapeTokens
import app.ownplay.mobile.design.OwnPlaySpacing
import app.ownplay.mobile.design.OwnPlayTopBar
import app.ownplay.mobile.downloads.domain.DownloadItem
import app.ownplay.mobile.downloads.domain.DownloadState
import app.ownplay.mobile.feature.library.domain.ContinueWatchingItem
import app.ownplay.mobile.feature.library.domain.LibraryCatalog
import app.ownplay.mobile.feature.library.domain.LibraryMediaKind
import app.ownplay.mobile.feature.library.domain.LibraryMovie
import app.ownplay.mobile.feature.library.domain.LibrarySeries

internal enum class LibraryBrowseKind {
    MOVIES,
    SERIES,
}

@Composable
internal fun LibraryHomeStage33(
    catalog: LibraryCatalog?,
    downloads: List<DownloadItem>,
    visibility: LibraryVisibilitySnapshot,
    errorMessage: String?,
    onContinueSelected: (ContinueWatchingItem) -> Unit,
    onContinueMarkWatched: (ContinueWatchingItem) -> Unit,
    onContinueClearProgress: (ContinueWatchingItem) -> Unit,
    onMovieSelected: (LibraryMovie) -> Unit,
    onSeriesSelected: (LibrarySeries) -> Unit,
    onContinueOfflineSelected: (DownloadItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchVisible by rememberSaveable(catalog?.activeSourceId) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(catalog?.activeSourceId) { mutableStateOf("") }
    var browseAllKindName by rememberSaveable(catalog?.activeSourceId) { mutableStateOf<String?>(null) }
    var browseAllCategoryKey by rememberSaveable(catalog?.activeSourceId) { mutableStateOf<String?>(null) }
    var browseAllCategoryName by rememberSaveable(catalog?.activeSourceId) { mutableStateOf<String?>(null) }

    val browseAllKind = browseAllKindName?.let { name ->
        runCatching { LibraryBrowseKind.valueOf(name) }.getOrNull()
    }
    val movieCategories = remember(catalog?.movieCategories) {
        LibraryBrowsePolicy.visibleCategories(catalog?.movieCategories.orEmpty())
    }
    val seriesCategories = remember(catalog?.seriesCategories) {
        LibraryBrowsePolicy.visibleCategories(catalog?.seriesCategories.orEmpty())
    }
    val movies = catalog?.movies.orEmpty()
    val series = catalog?.series.orEmpty()
    val moviesByCategory = remember(movies) { movies.groupBy { it.categoryKey } }
    val seriesByCategory = remember(series) { series.groupBy { it.categoryKey } }
    val visibleContinueWatching = catalog?.continueWatching.orEmpty().filterNot { item ->
        visibility.isContinueWatchingHidden(item.sourceId, item.mediaKind, item.contentId)
    }
    val continueOffline = downloads.filter { it.sourceId == catalog?.activeSourceId }
    val normalizedSearchQuery = searchQuery.trim()
    val searchActive = normalizedSearchQuery.isNotEmpty()

    if (browseAllKind != null) {
        val allMovies = if (browseAllKind == LibraryBrowseKind.MOVIES) {
            browseAllCategoryKey?.let { key -> moviesByCategory[key].orEmpty() } ?: movies
        } else emptyList()
        val allSeries = if (browseAllKind == LibraryBrowseKind.SERIES) {
            browseAllCategoryKey?.let { key -> seriesByCategory[key].orEmpty() } ?: series
        } else emptyList()
        LibraryAllGridStage33(
            kind = browseAllKind,
            categoryName = browseAllCategoryName,
            movies = allMovies,
            seriesItems = allSeries,
            onMovieSelected = onMovieSelected,
            onSeriesSelected = onSeriesSelected,
            onBack = {
                browseAllKindName = null
                browseAllCategoryKey = null
                browseAllCategoryName = null
            },
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        OwnPlayTopBar(
            showTagline = false,
            onSearchClick = {
                searchVisible = !searchVisible
                if (!searchVisible) searchQuery = ""
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OwnPlaySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Lg),
        ) {
            if (searchVisible) {
                OwnPlaySearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClose = {
                        searchQuery = ""
                        searchVisible = false
                    },
                    placeholder = "Search movies and series",
                )
            }

            if (errorMessage != null) {
                LibraryShelfState(
                    title = "Action unavailable",
                    message = errorMessage,
                    tone = LibraryStateTone.ERROR,
                )
            }

            when {
                catalog == null -> LibraryShelfSection(title = "Library", prominent = true) {
                    LibraryShelfState(
                        title = "Loading Library",
                        message = "Reading the active source and saved progress.",
                        tone = LibraryStateTone.LOADING,
                    )
                }

                catalog.activeSourceId == null -> LibraryShelfSection(title = "Library", prominent = true) {
                    LibraryShelfState(
                        title = "No active source",
                        message = "Add or select a source in Settings to populate your Library.",
                    )
                }

                else -> {
                    LibraryShelfSection(
                        title = "Continue Watching",
                        actionLabel = visibleContinueWatching.size.takeIf { it > 0 }
                            ?.let { "${compactLibraryCountStage33(it)} in progress" },
                        prominent = true,
                    ) {
                        if (visibleContinueWatching.isEmpty()) {
                            LibraryShelfState(
                                title = "Nothing to resume yet",
                                message = "Movies and episodes with saved progress will appear here.",
                            )
                        } else {
                            ContinueWatchingRowStage33(
                                items = visibleContinueWatching,
                                onSelected = onContinueSelected,
                                onMarkWatched = onContinueMarkWatched,
                                onClearProgress = onContinueClearProgress,
                            )
                        }
                    }

                    LibraryShelfSection(
                        title = "Continue Offline",
                        actionLabel = continueOffline.size.takeIf { it > 0 }
                            ?.let { "${compactLibraryCountStage33(it)} items" },
                        prominent = true,
                    ) {
                        if (continueOffline.isEmpty()) {
                            LibraryShelfState(
                                title = "Nothing offline yet",
                                message = "Movies and episodes you download will appear here.",
                            )
                        } else {
                            ContinueOfflineRowStage33(
                                items = continueOffline,
                                onSelected = onContinueOfflineSelected,
                            )
                        }
                    }

                    if (searchActive) {
                        val matchingMovies = movies.filter { it.name.contains(normalizedSearchQuery, ignoreCase = true) }
                        val matchingSeries = series.filter { it.name.contains(normalizedSearchQuery, ignoreCase = true) }
                        LibraryShelfHeader(
                            title = "Movies",
                            actionLabel = "${compactLibraryCountStage33(matchingMovies.size)} matches",
                        )
                        if (matchingMovies.isEmpty()) {
                            LibraryShelfState(
                                title = "No movie matches",
                                message = "Try another title or close search to browse categories.",
                            )
                        } else {
                            MovieRowStage33(
                                movies = LibraryBrowsePolicy.homePreview(matchingMovies),
                                onMovieSelected = onMovieSelected,
                            )
                        }

                        LibraryShelfHeader(
                            title = "Series",
                            actionLabel = "${compactLibraryCountStage33(matchingSeries.size)} matches",
                        )
                        if (matchingSeries.isEmpty()) {
                            LibraryShelfState(
                                title = "No series matches",
                                message = "Try another title or close search to browse categories.",
                            )
                        } else {
                            SeriesRowStage33(
                                seriesItems = LibraryBrowsePolicy.homePreview(matchingSeries),
                                onSeriesSelected = onSeriesSelected,
                            )
                        }
                    } else {
                        LibraryShelfHeader(
                            title = "Movies",
                            actionLabel = movies.size.takeIf { it > 0 }?.let { "${compactLibraryCountStage33(it)} titles" },
                        )
                        when {
                            movies.isEmpty() -> LibraryShelfState(
                                title = "No movies available",
                                message = "Refresh ${catalog.activeSourceName ?: "the active source"} to load movie metadata.",
                            )
                            movieCategories.isEmpty() -> LibraryCategoryMovieShelfStage33(
                                title = "Movies",
                                movies = movies,
                                onShowAll = {
                                    browseAllKindName = LibraryBrowseKind.MOVIES.name
                                    browseAllCategoryName = "Movies"
                                },
                                onMovieSelected = onMovieSelected,
                            )
                            else -> movieCategories.forEach { category ->
                                val categoryMovies = moviesByCategory[category.categoryKey].orEmpty()
                                if (categoryMovies.isNotEmpty()) {
                                    LibraryCategoryMovieShelfStage33(
                                        title = category.name,
                                        movies = categoryMovies,
                                        onShowAll = {
                                            browseAllKindName = LibraryBrowseKind.MOVIES.name
                                            browseAllCategoryKey = category.categoryKey
                                            browseAllCategoryName = category.name
                                        },
                                        onMovieSelected = onMovieSelected,
                                    )
                                }
                            }
                        }

                        LibraryShelfHeader(
                            title = "Series",
                            actionLabel = series.size.takeIf { it > 0 }?.let { "${compactLibraryCountStage33(it)} titles" },
                        )
                        when {
                            series.isEmpty() -> LibraryShelfState(
                                title = "No series available",
                                message = "Refresh ${catalog.activeSourceName ?: "the active source"} to load series metadata.",
                            )
                            seriesCategories.isEmpty() -> LibraryCategorySeriesShelfStage33(
                                title = "Series",
                                seriesItems = series,
                                onShowAll = {
                                    browseAllKindName = LibraryBrowseKind.SERIES.name
                                    browseAllCategoryName = "Series"
                                },
                                onSeriesSelected = onSeriesSelected,
                            )
                            else -> seriesCategories.forEach { category ->
                                val categorySeries = seriesByCategory[category.categoryKey].orEmpty()
                                if (categorySeries.isNotEmpty()) {
                                    LibraryCategorySeriesShelfStage33(
                                        title = category.name,
                                        seriesItems = categorySeries,
                                        onShowAll = {
                                            browseAllKindName = LibraryBrowseKind.SERIES.name
                                            browseAllCategoryKey = category.categoryKey
                                            browseAllCategoryName = category.name
                                        },
                                        onSeriesSelected = onSeriesSelected,
                                    )
                                }
                            }
                        }
                    }

                }
            }

            Spacer(modifier = Modifier.height(OwnPlaySpacing.Xl))
        }
    }
}

@Composable
private fun LibraryCategoryMovieShelfStage33(
    title: String,
    movies: List<LibraryMovie>,
    onShowAll: () -> Unit,
    onMovieSelected: (LibraryMovie) -> Unit,
) {
    LibraryShelfSection(
        title = title,
        actionLabel = if (movies.size > LibraryBrowsePolicy.HOME_PREVIEW_LIMIT) "Show all" else null,
        onActionClick = if (movies.size > LibraryBrowsePolicy.HOME_PREVIEW_LIMIT) onShowAll else null,
    ) {
        MovieRowStage33(
            movies = LibraryBrowsePolicy.homePreview(movies),
            onMovieSelected = onMovieSelected,
        )
    }
}

@Composable
private fun LibraryCategorySeriesShelfStage33(
    title: String,
    seriesItems: List<LibrarySeries>,
    onShowAll: () -> Unit,
    onSeriesSelected: (LibrarySeries) -> Unit,
) {
    LibraryShelfSection(
        title = title,
        actionLabel = if (seriesItems.size > LibraryBrowsePolicy.HOME_PREVIEW_LIMIT) "Show all" else null,
        onActionClick = if (seriesItems.size > LibraryBrowsePolicy.HOME_PREVIEW_LIMIT) onShowAll else null,
    ) {
        SeriesRowStage33(
            seriesItems = LibraryBrowsePolicy.homePreview(seriesItems),
            onSeriesSelected = onSeriesSelected,
        )
    }
}

@Composable
private fun MovieRowStage33(
    movies: List<LibraryMovie>,
    onMovieSelected: (LibraryMovie) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val posterWidth = compactPosterWidthStage33(maxWidth)
        PosterLazyRowStage33 {
            items(movies, key = { it.movieId }) { movie ->
                PosterCardStage33(
                    title = movie.name,
                    artworkUrl = movie.posterUrl,
                    eyebrow = movie.rating?.let { "★ $it" } ?: "MOVIE",
                    cardWidth = posterWidth,
                    onClick = { onMovieSelected(movie) },
                )
            }
        }
    }
}

@Composable
private fun SeriesRowStage33(
    seriesItems: List<LibrarySeries>,
    onSeriesSelected: (LibrarySeries) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val posterWidth = compactPosterWidthStage33(maxWidth)
        PosterLazyRowStage33 {
            items(seriesItems, key = { it.seriesId }) { series ->
                PosterCardStage33(
                    title = series.name,
                    artworkUrl = series.posterUrl,
                    eyebrow = series.rating?.let { "★ $it" } ?: "SERIES",
                    cardWidth = posterWidth,
                    onClick = { onSeriesSelected(series) },
                )
            }
        }
    }
}

@Composable
private fun PosterLazyRowStage33(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val state = rememberLazyListState()
    val fling = rememberSnapFlingBehavior(state, SnapPosition.Start)
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        state = state,
        flingBehavior = fling,
        horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
        content = content,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PosterCardStage33(
    title: String,
    artworkUrl: String?,
    eyebrow: String,
    cardWidth: Dp?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardModifier = if (cardWidth != null) modifier.width(cardWidth) else modifier
    Box(
        modifier = cardModifier
            .aspectRatio(0.68f)
            .clip(OwnPlayShapeTokens.Medium)
            .background(OwnPlayColors.SurfaceElevated)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = null,
            ),
    ) {
        LibraryRemoteArtwork(
            locator = artworkUrl,
            contentDescription = "$title poster",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.52f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.94f),
                    ),
                ),
        )
        if (isPressed) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)))
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
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
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueWatchingRowStage33(
    items: List<ContinueWatchingItem>,
    onSelected: (ContinueWatchingItem) -> Unit,
    onMarkWatched: (ContinueWatchingItem) -> Unit,
    onClearProgress: (ContinueWatchingItem) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val posterWidth = compactPosterWidthStage33(maxWidth)
        val listState = rememberLazyListState()
        val flingBehavior = rememberSnapFlingBehavior(listState, SnapPosition.Start)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            state = listState,
            flingBehavior = flingBehavior,
            horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
        ) {
            items(items, key = { "${it.sourceId}:${it.mediaKind}:${it.contentId}" }) { item ->
                ContinueWatchingCardStage33(
                    item = item,
                    cardWidth = posterWidth,
                    onSelected = { onSelected(item) },
                    onMarkWatched = { onMarkWatched(item) },
                    onClearProgress = { onClearProgress(item) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueWatchingCardStage33(
    item: ContinueWatchingItem,
    cardWidth: Dp,
    onSelected: () -> Unit,
    onMarkWatched: () -> Unit,
    onClearProgress: () -> Unit,
) {
    val progress = if (item.durationMs > 0L) {
        (item.positionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var menuVisible by remember(item.contentId) { mutableStateOf(false) }

    Column(
        modifier = Modifier.width(cardWidth),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.68f)
                    .clip(OwnPlayShapeTokens.Medium)
                    .background(OwnPlayColors.SurfaceElevated)
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onSelected,
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuVisible = true
                        },
                    ),
            ) {
                LibraryRemoteArtwork(
                    locator = item.artworkUrl,
                    contentDescription = "${item.title} poster",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                if (isPressed) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)))
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color.White.copy(alpha = 0.18f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(2.dp)
                            .background(OwnPlayColors.Accent),
                    )
                }
            }
            DropdownMenu(
                expanded = menuVisible,
                onDismissRequest = { menuVisible = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Mark as watched") },
                    onClick = {
                        menuVisible = false
                        onMarkWatched()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Clear progress") },
                    onClick = {
                        menuVisible = false
                        onClearProgress()
                    },
                )
            }
        }
        Text(
            text = item.title,
            modifier = Modifier
                .fillMaxWidth()
                .basicMarquee(),
            style = MaterialTheme.typography.labelLarge,
            color = OwnPlayColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        item.subtitle?.takeIf(String::isNotBlank)?.let { subtitle ->
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = OwnPlayColors.TextMuted,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueOfflineRowStage33(
    items: List<DownloadItem>,
    onSelected: (DownloadItem) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val posterWidth = compactPosterWidthStage33(maxWidth)
        val state = rememberLazyListState()
        val fling = rememberSnapFlingBehavior(state, SnapPosition.Start)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            state = state,
            flingBehavior = fling,
            horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
        ) {
            items(items, key = { it.downloadId }) { item ->
                ContinueOfflineCardStage33(
                    item = item,
                    cardWidth = posterWidth,
                    onSelected = { onSelected(item) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueOfflineCardStage33(
    item: DownloadItem,
    cardWidth: Dp,
    onSelected: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val progress = item.progressFraction
    val displayTitle = if (item.mediaKind == LibraryMediaKind.EPISODE) {
        item.title
    } else {
        item.metadata?.title ?: item.title
    }
    val stateLabel = when (item.state) {
        DownloadState.COMPLETED -> "Available offline"
        DownloadState.QUEUED -> "Queued for download"
        DownloadState.DOWNLOADING -> progress
            ?.let { "Downloading ${(it * 100).toInt().coerceIn(0, 100)}%" }
            ?: "Downloading"
        DownloadState.PAUSED -> progress
            ?.let { "Paused at ${(it * 100).toInt().coerceIn(0, 100)}%" }
            ?: "Paused"
        DownloadState.FAILED -> "Download needs attention"
    }

    Column(
        modifier = Modifier.width(cardWidth),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(OwnPlayShapeTokens.Medium)
                .background(OwnPlayColors.SurfaceElevated)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onSelected,
                    onLongClick = null,
                ),
        ) {
            LibraryRemoteArtwork(
                locator = item.metadata?.posterUrl ?: item.metadata?.backdropUrl,
                contentDescription = "$displayTitle poster",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (isPressed) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)))
            }
            if (progress != null && item.state != DownloadState.COMPLETED) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color.White.copy(alpha = 0.18f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(2.dp)
                            .background(OwnPlayColors.Accent),
                    )
                }
            }
        }
        Text(
            text = displayTitle,
            modifier = Modifier
                .fillMaxWidth()
                .basicMarquee(),
            style = MaterialTheme.typography.labelLarge,
            color = OwnPlayColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            text = stateLabel,
            style = MaterialTheme.typography.bodySmall,
            color = if (item.state == DownloadState.FAILED) OwnPlayColors.Accent else OwnPlayColors.TextMuted,
            maxLines = 1,
        )
    }
}

@Composable
private fun LibraryAllGridStage33(
    kind: LibraryBrowseKind,
    categoryName: String?,
    movies: List<LibraryMovie>,
    seriesItems: List<LibrarySeries>,
    onMovieSelected: (LibraryMovie) -> Unit,
    onSeriesSelected: (LibrarySeries) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    Column(modifier = modifier.fillMaxSize()) {
        OwnPlayTopBar(showTagline = false)
        Column(
            modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "‹ Library",
                modifier = Modifier.combinedClickable(onClick = onBack, onLongClick = null).padding(vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge,
                color = OwnPlayColors.Accent,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = categoryName ?: if (kind == LibraryBrowseKind.MOVIES) "Movies" else "Series",
                style = MaterialTheme.typography.headlineSmall,
                color = OwnPlayColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when (kind) {
                    LibraryBrowseKind.MOVIES -> "${compactLibraryCountStage33(movies.size)} movies"
                    LibraryBrowseKind.SERIES -> "${compactLibraryCountStage33(seriesItems.size)} series"
                },
                style = MaterialTheme.typography.bodySmall,
                color = OwnPlayColors.TextMuted,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(
                start = OwnPlaySpacing.Lg,
                end = OwnPlaySpacing.Lg,
                top = OwnPlaySpacing.Sm,
                bottom = OwnPlaySpacing.Xl,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (kind) {
                LibraryBrowseKind.MOVIES -> gridItems(movies, key = { it.movieId }) { movie ->
                    PosterCardStage33(
                        title = movie.name,
                        artworkUrl = movie.posterUrl,
                        eyebrow = movie.rating?.let { "★ $it" } ?: "MOVIE",
                        cardWidth = null,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onMovieSelected(movie) },
                    )
                }
                LibraryBrowseKind.SERIES -> gridItems(seriesItems, key = { it.seriesId }) { series ->
                    PosterCardStage33(
                        title = series.name,
                        artworkUrl = series.posterUrl,
                        eyebrow = series.rating?.let { "★ $it" } ?: "SERIES",
                        cardWidth = null,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSeriesSelected(series) },
                    )
                }
            }
        }
    }
}

private fun compactPosterWidthStage33(maxWidth: Dp): Dp =
    (maxWidth * 0.31f).coerceIn(104.dp, 118.dp)

private fun compactLibraryCountStage33(count: Int): String = when {
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
