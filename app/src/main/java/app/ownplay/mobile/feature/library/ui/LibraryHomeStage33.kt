package app.ownplay.mobile.feature.library.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import app.ownplay.mobile.downloads.domain.DownloadAction
import app.ownplay.mobile.downloads.domain.DownloadItem
import app.ownplay.mobile.downloads.domain.DownloadState
import app.ownplay.mobile.feature.library.domain.ContinueWatchingItem
import app.ownplay.mobile.feature.library.domain.LibraryCatalog
import app.ownplay.mobile.feature.library.domain.LibraryCategory
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
    onContinueResume: (ContinueWatchingItem) -> Unit,
    onContinueMarkWatched: (ContinueWatchingItem) -> Unit,
    onContinueClearProgress: (ContinueWatchingItem) -> Unit,
    onMovieSelected: (LibraryMovie) -> Unit,
    onSeriesSelected: (LibrarySeries) -> Unit,
    onDownloadedSelected: (DownloadItem) -> Unit,
    onDownloadedAction: (DownloadItem, DownloadAction) -> Unit,
    onDownloadedHide: (DownloadItem) -> Unit,
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
    val visibleDownloads = downloads.filterNot { item -> visibility.isDownloadHidden(item.downloadId) }
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
                                onResume = onContinueResume,
                                onMarkWatched = onContinueMarkWatched,
                                onClearProgress = onContinueClearProgress,
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

                    LibraryShelfSection(
                        title = "Downloaded Media",
                        actionLabel = visibleDownloads.size.takeIf { it > 0 }
                            ?.let { "${compactLibraryCountStage33(it)} saved" },
                    ) {
                        if (visibleDownloads.isEmpty()) {
                            LibraryShelfState(
                                title = "No downloaded media",
                                message = "Downloaded movies and episodes stay listed here until you remove them from OwnPlay.",
                            )
                        } else {
                            DownloadedRowStage33(
                                items = visibleDownloads,
                                onSelected = onDownloadedSelected,
                                onAction = onDownloadedAction,
                                onHide = onDownloadedHide,
                            )
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
        val posterWidth = (maxWidth * 0.39f).coerceIn(126.dp, 148.dp)
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
        val posterWidth = (maxWidth * 0.39f).coerceIn(126.dp, 148.dp)
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
        horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
        content = content,
    )
}

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueWatchingRowStage33(
    items: List<ContinueWatchingItem>,
    onResume: (ContinueWatchingItem) -> Unit,
    onMarkWatched: (ContinueWatchingItem) -> Unit,
    onClearProgress: (ContinueWatchingItem) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val posterWidth = (maxWidth * 0.39f).coerceIn(126.dp, 148.dp)
        val listState = rememberLazyListState()
        val flingBehavior = rememberSnapFlingBehavior(listState, SnapPosition.Start)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            state = listState,
            flingBehavior = flingBehavior,
            horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
        ) {
            items(items, key = { "${it.sourceId}:${it.mediaKind}:${it.contentId}" }) { item ->
                ContinueWatchingCardStage33(
                    item = item,
                    cardWidth = posterWidth,
                    onResume = { onResume(item) },
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
    onResume: () -> Unit,
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
                        onClick = onResume,
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

@Composable
private fun DownloadedRowStage33(
    items: List<DownloadItem>,
    onSelected: (DownloadItem) -> Unit,
    onAction: (DownloadItem, DownloadAction) -> Unit,
    onHide: (DownloadItem) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val itemWidth = (maxWidth * 0.84f).coerceIn(244.dp, 292.dp)
        val state = rememberLazyListState()
        val fling = rememberSnapFlingBehavior(state, SnapPosition.Start)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            state = state,
            flingBehavior = fling,
            horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
        ) {
            items(items, key = { it.downloadId }) { item ->
                val mediaLabel = item.mediaKind.name
                val stateLabel = when (item.state) {
                    DownloadState.COMPLETED -> "OFFLINE"
                    DownloadState.DOWNLOADING -> "DOWNLOADING"
                    DownloadState.QUEUED -> "QUEUED"
                    DownloadState.PAUSED -> "PAUSED"
                    DownloadState.FAILED -> "NEEDS ATTENTION"
                }
                val displayTitle = item.metadata?.title ?: item.title
                Column(
                    modifier = Modifier.width(itemWidth),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        PosterCardStage33(
                            title = displayTitle,
                            artworkUrl = item.metadata?.posterUrl ?: item.metadata?.backdropUrl,
                            eyebrow = mediaLabel,
                            cardWidth = 104.dp,
                            onClick = { onSelected(item) },
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "$mediaLabel • $stateLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = OwnPlayColors.Accent.copy(alpha = 0.88f),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                            Text(
                                text = displayTitle,
                                style = MaterialTheme.typography.titleSmall,
                                color = OwnPlayColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 3,
                            )
                            item.metadata?.releaseDate?.takeIf(String::isNotBlank)?.let { releaseDate ->
                                Text(
                                    text = releaseDate,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OwnPlayColors.TextMuted,
                                    maxLines = 1,
                                )
                            }
                            LibrarySecondaryAction(
                                text = "Details",
                                onClick = { onSelected(item) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    LibraryOfflineControls(
                        item = item,
                        onAction = { action -> onAction(item, action) },
                        onHideFromLibrary = { onHide(item) },
                    )
                }
            }
        }
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
