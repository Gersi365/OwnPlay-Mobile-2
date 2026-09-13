from pathlib import Path


def replace_once(path_str: str, old: str, new: str) -> None:
    path = Path(path_str)
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected 1 match, found {count}: {old[:120]!r}")
    path.write_text(text.replace(old, new, 1))


def replace_between(path_str: str, start: str, end: str, replacement: str) -> None:
    path = Path(path_str)
    text = path.read_text()
    start_index = text.find(start)
    if start_index < 0:
        raise SystemExit(f"{path}: start marker missing: {start!r}")
    end_index = text.find(end, start_index)
    if end_index < 0:
        raise SystemExit(f"{path}: end marker missing: {end!r}")
    path.write_text(text[:start_index] + replacement + text[end_index:])


shell = "app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryShell.kt"

replace_once(
    shell,
    '''import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
''',
    '''import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
''',
)
replace_once(
    shell,
    '''import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
''',
    '''import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
''',
)
replace_once(
    shell,
    '''import app.ownplay.mobile.design.OwnPlayTopBar
import app.ownplay.mobile.downloads.domain.DownloadAction
''',
    '''import app.ownplay.mobile.design.OwnPlayTopBar
import app.ownplay.mobile.data.prefs.LibraryVisibilityPreferences
import app.ownplay.mobile.data.prefs.LibraryVisibilitySnapshot
import app.ownplay.mobile.downloads.domain.DownloadAction
''',
)
replace_once(
    shell,
    '''@Composable
fun LibraryShell(''',
    '''private enum class LibraryBrowseKind {
    MOVIES,
    SERIES,
}

@Composable
fun LibraryShell(''',
)
replace_once(
    shell,
    '''fun LibraryShell(
    libraryRepository: LibraryRepository,
    downloadRepository: DownloadRepository,
    playbackController: PlaybackController,
''',
    '''fun LibraryShell(
    libraryRepository: LibraryRepository,
    downloadRepository: DownloadRepository,
    libraryVisibilityPreferences: LibraryVisibilityPreferences,
    playbackController: PlaybackController,
''',
)
replace_once(
    shell,
    '''    val catalogFlow = remember(libraryRepository) { libraryRepository.observeCatalog() }
    val downloadsFlow = remember(downloadRepository) { downloadRepository.observeDownloads() }
    val catalog by catalogFlow.collectAsState(initial = null)
    val downloads by downloadsFlow.collectAsState(initial = emptyList())
''',
    '''    val catalogFlow = remember(libraryRepository) { libraryRepository.observeCatalog() }
    val downloadsFlow = remember(downloadRepository) { downloadRepository.observeDownloads() }
    val visibilityFlow = remember(libraryVisibilityPreferences) { libraryVisibilityPreferences.visibility }
    val catalog by catalogFlow.collectAsState(initial = null)
    val downloads by downloadsFlow.collectAsState(initial = emptyList())
    val visibility by visibilityFlow.collectAsState(initial = LibraryVisibilitySnapshot())
''',
)
replace_once(
    shell,
    '''    var detailError by remember { mutableStateOf<String?>(null) }
    var resolutionError by remember { mutableStateOf<String?>(null) }
    var activePlayback by remember { mutableStateOf<ResolvedLibraryPlayback?>(null) }
''',
    '''    var detailError by remember { mutableStateOf<String?>(null) }
    var resolutionError by remember { mutableStateOf<String?>(null) }
    var activePlayback by remember { mutableStateOf<ResolvedLibraryPlayback?>(null) }
    var browseAllKind by remember { mutableStateOf<LibraryBrowseKind?>(null) }
    var browseAllCategoryKey by remember { mutableStateOf<String?>(null) }
    var browseAllCategoryName by remember { mutableStateOf<String?>(null) }
''',
)
replace_once(
    shell,
    '''            is LibraryPlaybackResolution.Success -> {
                scope.launch {
                    playbackController.load(resolved.value.toLoadRequest())
                    activePlayback = resolved.value
                }
            }
''',
    '''            is LibraryPlaybackResolution.Success -> {
                scope.launch {
                    libraryVisibilityPreferences.showContinueWatching(
                        sourceId = resolved.value.sourceId,
                        mediaKind = resolved.value.mediaKind,
                        contentId = resolved.value.contentId,
                    )
                    playbackController.load(resolved.value.toLoadRequest())
                    activePlayback = resolved.value
                }
            }
''',
)
replace_once(
    shell,
    '''                    if (result is DownloadOperationResult.Failure) {
                        resolutionError = result.safeMessage
                    }
''',
    '''                    if (result is DownloadOperationResult.Failure) {
                        resolutionError = result.safeMessage
                    } else if (result is DownloadOperationResult.Success && action == DownloadAction.DOWNLOAD) {
                        result.item?.let { created ->
                            libraryVisibilityPreferences.showDownload(created.downloadId)
                        }
                    }
''',
)

replace_once(
    shell,
    '''        else -> LibraryHome(
            catalog = catalog,
            downloads = downloads,
            errorMessage = resolutionError,
''',
    '''        else -> LibraryHome(
            catalog = catalog,
            downloads = downloads,
            visibility = visibility,
            browseAllKind = browseAllKind,
            browseAllCategoryKey = browseAllCategoryKey,
            browseAllCategoryName = browseAllCategoryName,
            errorMessage = resolutionError,
''',
)
replace_once(
    shell,
    '''            onContinueBeginning = { item ->
                when (item.mediaKind) {
                    LibraryMediaKind.MOVIE -> startMovie(item.contentId, LibraryStartMode.BEGINNING)
                    LibraryMediaKind.EPISODE -> startEpisode(item.contentId, LibraryStartMode.BEGINNING)
                }
            },
            onMovieSelected = { movie ->
''',
    '''            onContinueBeginning = { item ->
                when (item.mediaKind) {
                    LibraryMediaKind.MOVIE -> startMovie(item.contentId, LibraryStartMode.BEGINNING)
                    LibraryMediaKind.EPISODE -> startEpisode(item.contentId, LibraryStartMode.BEGINNING)
                }
            },
            onContinueDismiss = { item ->
                scope.launch {
                    libraryVisibilityPreferences.hideContinueWatching(
                        sourceId = item.sourceId,
                        mediaKind = item.mediaKind,
                        contentId = item.contentId,
                    )
                }
            },
            onBrowseAll = { kind, categoryKey, categoryName ->
                browseAllKind = kind
                browseAllCategoryKey = categoryKey
                browseAllCategoryName = categoryName
            },
            onBrowseAllBack = {
                browseAllKind = null
                browseAllCategoryKey = null
                browseAllCategoryName = null
            },
            onMovieSelected = { movie ->
''',
)
replace_once(
    shell,
    '''            onDownloadedAction = { media, item, action ->
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
''',
    '''            onDownloadedAction = { media, item, action ->
                performDownloadAction(
                    item = item,
                    sourceId = media.sourceId,
                    mediaKind = media.mediaKind,
                    contentId = media.contentId,
                    title = media.title,
                    action = action,
                )
            },
            onDownloadedHide = { media ->
                scope.launch { libraryVisibilityPreferences.hideDownload(media.downloadId) }
            },
            modifier = modifier,
''',
)

home = '''@Composable
private fun LibraryHome(
    catalog: LibraryCatalog?,
    downloads: List<DownloadItem>,
    visibility: LibraryVisibilitySnapshot,
    browseAllKind: LibraryBrowseKind?,
    browseAllCategoryKey: String?,
    browseAllCategoryName: String?,
    errorMessage: String?,
    onContinueResume: (ContinueWatchingItem) -> Unit,
    onContinueBeginning: (ContinueWatchingItem) -> Unit,
    onContinueDismiss: (ContinueWatchingItem) -> Unit,
    onBrowseAll: (LibraryBrowseKind, String?, String?) -> Unit,
    onBrowseAllBack: () -> Unit,
    onMovieSelected: (LibraryMovie) -> Unit,
    onSeriesSelected: (LibrarySeries) -> Unit,
    onDownloadedAction: (LibraryDownloadedMedia, DownloadItem, DownloadAction) -> Unit,
    onDownloadedHide: (LibraryDownloadedMedia) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchVisible by remember(catalog?.activeSourceId) { mutableStateOf(false) }
    var searchQuery by remember(catalog?.activeSourceId) { mutableStateOf("") }
    val normalizedSearchQuery = searchQuery.trim()
    val searchActive = normalizedSearchQuery.isNotEmpty()
    val movieCategories = remember(catalog?.movieCategories) {
        LibraryBrowsePolicy.visibleCategories(catalog?.movieCategories.orEmpty())
    }
    val seriesCategories = remember(catalog?.seriesCategories) {
        LibraryBrowsePolicy.visibleCategories(catalog?.seriesCategories.orEmpty())
    }
    val movies = catalog?.movies.orEmpty()
    val series = catalog?.series.orEmpty()
    val visibleContinueWatching = catalog?.continueWatching.orEmpty().filterNot { item ->
        visibility.isContinueWatchingHidden(item.sourceId, item.mediaKind, item.contentId)
    }
    val visibleDownloadedMedia = catalog?.downloadedMedia.orEmpty().filterNot { media ->
        visibility.isDownloadHidden(media.downloadId)
    }

    if (catalog != null && browseAllKind != null) {
        val selectedMovies = if (browseAllKind == LibraryBrowseKind.MOVIES) {
            browseAllCategoryKey?.let { key -> movies.filter { it.categoryKey == key } } ?: movies
        } else {
            emptyList()
        }
        val selectedSeries = if (browseAllKind == LibraryBrowseKind.SERIES) {
            browseAllCategoryKey?.let { key -> series.filter { it.categoryKey == key } } ?: series
        } else {
            emptyList()
        }
        LibraryAllGrid(
            kind = browseAllKind,
            categoryName = browseAllCategoryName,
            movies = selectedMovies,
            seriesItems = selectedSeries,
            onMovieSelected = onMovieSelected,
            onSeriesSelected = onSeriesSelected,
            onBack = onBrowseAllBack,
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        OwnPlayTopBar(
            showTagline = false,
            onSearchClick = {
                searchVisible = !searchVisible
                if (!searchVisible) searchQuery = ""
            },
        )

        Column(
            modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg),
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
                        actionLabel = visibleContinueWatching.size
                            .takeIf { it > 0 }
                            ?.let { "${compactLibraryCount(it)} in progress" },
                        prominent = true,
                    ) {
                        if (visibleContinueWatching.isEmpty()) {
                            LibraryShelfState(
                                title = "Nothing to resume yet",
                                message = "Movies and episodes with saved progress will appear here.",
                            )
                        } else {
                            ContinueWatchingRow(
                                items = visibleContinueWatching,
                                onResume = onContinueResume,
                                onBeginning = onContinueBeginning,
                                onDismiss = onContinueDismiss,
                            )
                        }
                    }

                    if (searchActive) {
                        val matchingMovies = movies.filter { it.name.contains(normalizedSearchQuery, ignoreCase = true) }
                        val matchingSeries = series.filter { it.name.contains(normalizedSearchQuery, ignoreCase = true) }
                        LibraryShelfHeader(
                            title = "Movies",
                            actionLabel = "${compactLibraryCount(matchingMovies.size)} matches",
                        )
                        if (matchingMovies.isEmpty()) {
                            LibraryShelfState(
                                title = "No movie matches",
                                message = "Try another title or close search to browse categories.",
                            )
                        } else {
                            MovieRow(
                                movies = LibraryBrowsePolicy.homePreview(matchingMovies),
                                onMovieSelected = onMovieSelected,
                            )
                        }

                        LibraryShelfHeader(
                            title = "Series",
                            actionLabel = "${compactLibraryCount(matchingSeries.size)} matches",
                        )
                        if (matchingSeries.isEmpty()) {
                            LibraryShelfState(
                                title = "No series matches",
                                message = "Try another title or close search to browse categories.",
                            )
                        } else {
                            SeriesRow(
                                seriesItems = LibraryBrowsePolicy.homePreview(matchingSeries),
                                onSeriesSelected = onSeriesSelected,
                            )
                        }
                    } else {
                        LibraryShelfHeader(
                            title = "Movies",
                            actionLabel = catalog.movies.size.takeIf { it > 0 }?.let { "${compactLibraryCount(it)} titles" },
                        )
                        when {
                            catalog.movies.isEmpty() -> LibraryShelfState(
                                title = "No movies available",
                                message = "Refresh ${catalog.activeSourceName ?: "the active source"} to load movie metadata.",
                            )

                            movieCategories.isEmpty() -> LibraryCategoryMovieShelf(
                                title = "Movies",
                                movies = movies,
                                onShowAll = { onBrowseAll(LibraryBrowseKind.MOVIES, null, "Movies") },
                                onMovieSelected = onMovieSelected,
                            )

                            else -> movieCategories.forEach { category ->
                                val categoryMovies = movies.filter { it.categoryKey == category.categoryKey }
                                if (categoryMovies.isNotEmpty()) {
                                    LibraryCategoryMovieShelf(
                                        title = category.name,
                                        movies = categoryMovies,
                                        onShowAll = {
                                            onBrowseAll(LibraryBrowseKind.MOVIES, category.categoryKey, category.name)
                                        },
                                        onMovieSelected = onMovieSelected,
                                    )
                                }
                            }
                        }

                        LibraryShelfHeader(
                            title = "Series",
                            actionLabel = catalog.series.size.takeIf { it > 0 }?.let { "${compactLibraryCount(it)} titles" },
                        )
                        when {
                            catalog.series.isEmpty() -> LibraryShelfState(
                                title = "No series available",
                                message = "Refresh ${catalog.activeSourceName ?: "the active source"} to load series metadata.",
                            )

                            seriesCategories.isEmpty() -> LibraryCategorySeriesShelf(
                                title = "Series",
                                seriesItems = series,
                                onShowAll = { onBrowseAll(LibraryBrowseKind.SERIES, null, "Series") },
                                onSeriesSelected = onSeriesSelected,
                            )

                            else -> seriesCategories.forEach { category ->
                                val categorySeries = series.filter { it.categoryKey == category.categoryKey }
                                if (categorySeries.isNotEmpty()) {
                                    LibraryCategorySeriesShelf(
                                        title = category.name,
                                        seriesItems = categorySeries,
                                        onShowAll = {
                                            onBrowseAll(LibraryBrowseKind.SERIES, category.categoryKey, category.name)
                                        },
                                        onSeriesSelected = onSeriesSelected,
                                    )
                                }
                            }
                        }
                    }

                    LibraryShelfSection(
                        title = "Downloaded Media",
                        actionLabel = visibleDownloadedMedia.size
                            .takeIf { it > 0 }
                            ?.let { "${compactLibraryCount(it)} offline" },
                    ) {
                        if (visibleDownloadedMedia.isEmpty()) {
                            LibraryShelfState(
                                title = "No completed downloads",
                                message = "Completed media appears here after its offline file passes integrity verification.",
                            )
                        } else {
                            DownloadedRow(
                                mediaItems = visibleDownloadedMedia,
                                downloads = downloads,
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
private fun LibraryCategoryMovieShelf(
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
        MovieRow(
            movies = LibraryBrowsePolicy.homePreview(movies),
            onMovieSelected = onMovieSelected,
        )
    }
}

@Composable
private fun LibraryCategorySeriesShelf(
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
        SeriesRow(
            seriesItems = LibraryBrowsePolicy.homePreview(seriesItems),
            onSeriesSelected = onSeriesSelected,
        )
    }
}

@Composable
private fun LibraryAllGrid(
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
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(vertical = 10.dp),
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
                    LibraryBrowseKind.MOVIES -> "${compactLibraryCount(movies.size)} movies"
                    LibraryBrowseKind.SERIES -> "${compactLibraryCount(seriesItems.size)} series"
                },
                style = MaterialTheme.typography.bodySmall,
                color = OwnPlayColors.TextMuted,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
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
                LibraryBrowseKind.MOVIES -> gridItems(
                    items = movies,
                    key = { movie -> movie.movieId },
                ) { movie ->
                    PosterCard(
                        title = movie.name,
                        artworkUrl = movie.posterUrl,
                        eyebrow = movie.rating?.let { "★ $it" } ?: "MOVIE",
                        cardWidth = null,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onMovieSelected(movie) },
                    )
                }

                LibraryBrowseKind.SERIES -> gridItems(
                    items = seriesItems,
                    key = { item -> item.seriesId },
                ) { item ->
                    PosterCard(
                        title = item.name,
                        artworkUrl = item.posterUrl,
                        eyebrow = item.rating?.let { "★ $it" } ?: "SERIES",
                        cardWidth = null,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSeriesSelected(item) },
                    )
                }
            }
        }
    }
}

'''
replace_between(
    shell,
    "@Composable\nprivate fun LibraryHome(",
    "@Composable\nprivate fun LibraryCategoryStrip(",
    home,
)

# The old category-strip function is no longer used by Library Home. Keep it temporarily
# because it is isolated and harmless; removing it is not required for the behavior change.

replace_once(
    shell,
    '''private fun ContinueWatchingRow(
    items: List<ContinueWatchingItem>,
    onResume: (ContinueWatchingItem) -> Unit,
    onBeginning: (ContinueWatchingItem) -> Unit,
) {''',
    '''private fun ContinueWatchingRow(
    items: List<ContinueWatchingItem>,
    onResume: (ContinueWatchingItem) -> Unit,
    onBeginning: (ContinueWatchingItem) -> Unit,
    onDismiss: (ContinueWatchingItem) -> Unit,
) {''',
)
replace_once(
    shell,
    '''                    onResume = { onResume(item) },
                    onBeginning = { onBeginning(item) },
                )
''',
    '''                    onResume = { onResume(item) },
                    onBeginning = { onBeginning(item) },
                    onDismiss = { onDismiss(item) },
                )
''',
)
replace_once(
    shell,
    '''private fun ContinueWatchingCard(
    item: ContinueWatchingItem,
    cardWidth: Dp,
    onResume: () -> Unit,
    onBeginning: () -> Unit,
) {''',
    '''private fun ContinueWatchingCard(
    item: ContinueWatchingItem,
    cardWidth: Dp,
    onResume: () -> Unit,
    onBeginning: () -> Unit,
    onDismiss: () -> Unit,
) {''',
)
replace_once(
    shell,
    '''            LibraryIconAction(
                glyph = LibraryActionGlyph.RESTART,
                contentDescription = "Start ${item.title} over",
                visualSize = 32.dp,
                onClick = onBeginning,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            )

            Column(''',
    '''            LibraryIconAction(
                glyph = LibraryActionGlyph.DISMISS,
                contentDescription = "Remove ${item.title} from Continue Watching",
                visualSize = 32.dp,
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
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

            Column(''',
)

replace_once(
    shell,
    '''private fun PosterCard(
    title: String,
    artworkUrl: String?,
    eyebrow: String,
    cardWidth: Dp,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .width(cardWidth)
            .aspectRatio(0.68f)
''',
    '''private fun PosterCard(
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
''',
)

replace_once(
    shell,
    '''private fun DownloadedRow(
    mediaItems: List<LibraryDownloadedMedia>,
    downloads: List<DownloadItem>,
    onAction: (LibraryDownloadedMedia, DownloadItem, DownloadAction) -> Unit,
) {''',
    '''private fun DownloadedRow(
    mediaItems: List<LibraryDownloadedMedia>,
    downloads: List<DownloadItem>,
    onAction: (LibraryDownloadedMedia, DownloadItem, DownloadAction) -> Unit,
    onHide: (LibraryDownloadedMedia) -> Unit,
) {''',
)
replace_once(
    shell,
    '''                        LibraryOfflineControls(
                            item = item,
                            onAction = { action -> onAction(media, item, action) },
                        )
''',
    '''                        LibraryOfflineControls(
                            item = item,
                            onAction = { action -> onAction(media, item, action) },
                            onHideFromLibrary = { onHide(media) },
                        )
''',
)
