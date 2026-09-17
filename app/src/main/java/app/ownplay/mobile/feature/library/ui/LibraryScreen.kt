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
import app.ownplay.mobile.OwnPlayApplication
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayFeaturePlaceholder
import app.ownplay.mobile.feature.library.domain.LibraryCatalogSnapshot
import app.ownplay.mobile.feature.library.domain.LibraryContentKind
import app.ownplay.mobile.feature.library.domain.LibraryDetailRefreshResult
import app.ownplay.mobile.feature.library.domain.LibraryDetailStartPolicy
import app.ownplay.mobile.feature.library.domain.LibraryMovieSummary
import app.ownplay.mobile.feature.library.domain.LibraryRepository
import app.ownplay.mobile.feature.library.domain.LibrarySeriesDetail
import app.ownplay.mobile.feature.library.domain.LibrarySeriesSummary
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
        modifier = modifier,
    )
}

@Composable
private fun LibrarySourceScreen(
    source: SourceSummary,
    repository: LibraryRepository,
    modifier: Modifier,
) {
    var selectedSeriesId by remember(source.sourceId) { mutableStateOf<String?>(null) }
    val openSeriesId = selectedSeriesId
    if (openSeriesId != null) {
        LibrarySeriesDetailScreen(
            source = source,
            seriesId = openSeriesId,
            repository = repository,
            onBack = { selectedSeriesId = null },
            modifier = modifier,
        )
        return
    }

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
    val scope = rememberCoroutineScope()
    val movieCategoryNames = catalog.movieCategories.associate { it.categoryId to it.displayName }
    val seriesCategoryNames = catalog.seriesCategories.associate { it.categoryId to it.displayName }

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

        if (catalog.movies.isEmpty() && catalog.series.isEmpty()) {
            item {
                Text(
                    text = "No Movies or Series are available from this source.",
                    color = OwnPlayColors.TextMuted,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        if (catalog.movies.isNotEmpty()) {
            item {
                Text(
                    text = "Movies",
                    color = OwnPlayColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(catalog.movies, key = { "movie:${it.movieId}" }) { movie ->
                LibraryMovieRow(
                    movie = movie,
                    categoryName = movie.categoryId?.let(movieCategoryNames::get),
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

        if (catalog.series.isNotEmpty()) {
            item {
                Text(
                    text = "Series",
                    color = OwnPlayColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(catalog.series, key = { "series:${it.seriesId}" }) { series ->
                LibrarySeriesRow(
                    series = series,
                    categoryName = series.categoryId?.let(seriesCategoryNames::get),
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
    }
}

@Composable
private fun LibrarySeriesDetailScreen(
    source: SourceSummary,
    seriesId: String,
    repository: LibraryRepository,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val detailFlow = remember(repository, source.sourceId, seriesId) {
        repository.observeSeriesDetail(source.sourceId, seriesId)
    }
    val detail by detailFlow.collectAsState(initial = null)
    var refreshing by remember(source.sourceId, seriesId) { mutableStateOf(true) }
    var refreshResult by remember(source.sourceId, seriesId) {
        mutableStateOf<LibraryDetailRefreshResult?>(null)
    }
    var selectedEpisodeId by remember(source.sourceId, seriesId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(repository, source.sourceId, seriesId) {
        refreshing = true
        refreshResult = repository.refreshSeriesDetail(source.sourceId, seriesId)
        refreshing = false
    }

    LaunchedEffect(detail) {
        val current = detail ?: return@LaunchedEffect
        val selectedStillAvailable = selectedEpisodeId?.let { selectedId ->
            current.seasons.any { season ->
                season.episodes.any { episode -> episode.episodeId == selectedId }
            }
        } ?: false
        if (!selectedStillAvailable) {
            selectedEpisodeId = LibraryDetailStartPolicy.firstAvailableEpisode(current)?.episodeId
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

        if (currentDetail.seasons.isEmpty()) {
            item {
                Text(
                    text = if (refreshing) {
                        "Refreshing episode details…"
                    } else {
                        "No episodes are available for this Series."
                    },
                    color = OwnPlayColors.TextMuted,
                )
            }
        }

        currentDetail.seasons.forEach { season ->
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
    onFavorite: () -> Unit,
) {
    Surface(
        color = OwnPlayColors.Surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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

@Composable
private fun LibraryMovieRow(
    movie: LibraryMovieSummary,
    categoryName: String?,
    onFavorite: () -> Unit,
) {
    LibraryMediaRow(
        title = movie.title,
        categoryName = categoryName,
        rating = movie.rating,
        favorite = movie.favorite,
        onOpen = null,
        onFavorite = onFavorite,
    )
}

@Composable
private fun LibrarySeriesRow(
    series: LibrarySeriesSummary,
    categoryName: String?,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
) {
    LibraryMediaRow(
        title = series.title,
        categoryName = categoryName,
        rating = series.rating,
        favorite = series.favorite,
        onOpen = onOpen,
        onFavorite = onFavorite,
    )
}

@Composable
private fun LibraryMediaRow(
    title: String,
    categoryName: String?,
    rating: String?,
    favorite: Boolean,
    onOpen: (() -> Unit)?,
    onFavorite: () -> Unit,
) {
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
                onOpen?.let { open ->
                    TextButton(onClick = open) {
                        Text("Open details")
                    }
                }
            }
            TextButton(onClick = onFavorite) {
                Text(if (favorite) "Unfavorite" else "Favorite")
            }
        }
    }
}
