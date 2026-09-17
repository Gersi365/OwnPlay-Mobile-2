package app.ownplay.mobile.feature.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.mobile.OwnPlayApplication
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayFeaturePlaceholder
import app.ownplay.mobile.feature.library.domain.LibraryCatalogSnapshot
import app.ownplay.mobile.feature.library.domain.LibraryContentKind
import app.ownplay.mobile.feature.library.domain.LibraryMovieSummary
import app.ownplay.mobile.feature.library.domain.LibraryRepository
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
        onFavorite = onFavorite,
    )
}

@Composable
private fun LibrarySeriesRow(
    series: LibrarySeriesSummary,
    categoryName: String?,
    onFavorite: () -> Unit,
) {
    LibraryMediaRow(
        title = series.title,
        categoryName = categoryName,
        rating = series.rating,
        favorite = series.favorite,
        onFavorite = onFavorite,
    )
}

@Composable
private fun LibraryMediaRow(
    title: String,
    categoryName: String?,
    rating: String?,
    favorite: Boolean,
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
            }
            TextButton(onClick = onFavorite) {
                Text(if (favorite) "Unfavorite" else "Favorite")
            }
        }
    }
}
