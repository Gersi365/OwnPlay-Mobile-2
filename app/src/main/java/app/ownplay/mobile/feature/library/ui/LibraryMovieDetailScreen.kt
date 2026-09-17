package app.ownplay.mobile.feature.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.feature.library.data.LibraryArtworkLoader
import app.ownplay.mobile.feature.library.domain.LibraryContentKind
import app.ownplay.mobile.feature.library.domain.LibraryMovieDetailLoadResult
import app.ownplay.mobile.feature.library.domain.LibraryMovieDetailMetadata
import app.ownplay.mobile.feature.library.domain.LibraryRepository
import app.ownplay.mobile.feature.playback.domain.PlaybackSessionController
import app.ownplay.mobile.feature.playback.domain.PlaybackTarget
import app.ownplay.mobile.sources.domain.SourceSummary
import kotlinx.coroutines.launch

@Composable
internal fun LibraryMovieDetailScreen(
    source: SourceSummary,
    movieId: String,
    repository: LibraryRepository,
    artworkLoader: LibraryArtworkLoader,
    playbackSessionController: PlaybackSessionController,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val movieFlow = remember(repository, source.sourceId, movieId) {
        repository.observeMovie(source.sourceId, movieId)
    }
    val movie by movieFlow.collectAsState(initial = null)
    var loading by remember(source.sourceId, movieId) { mutableStateOf(true) }
    var detailResult by remember(source.sourceId, movieId) {
        mutableStateOf<LibraryMovieDetailLoadResult?>(null)
    }
    val scope = rememberCoroutineScope()

    LaunchedEffect(repository, source.sourceId, movieId) {
        loading = true
        detailResult = repository.loadMovieDetail(source.sourceId, movieId)
        loading = false
    }

    val cachedMovie = movie
    val metadata = (detailResult as? LibraryMovieDetailLoadResult.Loaded)?.metadata

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            TextButton(onClick = onBack) {
                Text("Back")
            }
        }

        if (cachedMovie == null) {
            item {
                Text(
                    text = when (detailResult) {
                        LibraryMovieDetailLoadResult.Unavailable -> "This Movie is no longer available."
                        else -> "Movie details are not available."
                    },
                    color = OwnPlayColors.TextMuted,
                )
            }
            return@LazyColumn
        }

        item {
            Surface(
                color = OwnPlayColors.Surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LibraryArtwork(
                        url = metadata?.posterUrl ?: cachedMovie.posterUrl,
                        loader = artworkLoader,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = cachedMovie.title,
                            color = OwnPlayColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        LibraryMovieDetailPresentation.metadataLine(
                            metadata ?: LibraryMovieDetailMetadata(
                                posterUrl = cachedMovie.posterUrl,
                                backdropUrl = cachedMovie.backdropUrl,
                                plot = null,
                                releaseDate = null,
                                year = null,
                                runtimeMs = null,
                                rating = cachedMovie.rating,
                            ),
                        )?.let { line ->
                            Text(text = line, color = OwnPlayColors.TextSecondary)
                        }
                        metadata?.releaseDate
                            ?.takeIf { it.isNotBlank() && it != metadata.year }
                            ?.let { date ->
                                Text(text = "Release: $date", color = OwnPlayColors.TextMuted)
                            }
                        metadata?.plot?.takeIf(String::isNotBlank)?.let { plot ->
                            Text(text = plot, color = OwnPlayColors.TextSecondary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        playbackSessionController.activateLibraryMedia(
                                            PlaybackTarget.Movie(
                                                sourceId = source.sourceId,
                                                movieId = cachedMovie.movieId,
                                            ),
                                        )
                                    }
                                },
                            ) {
                                Text("Play")
                            }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        repository.setFavorite(
                                            sourceId = source.sourceId,
                                            contentKind = LibraryContentKind.MOVIE,
                                            contentId = cachedMovie.movieId,
                                            favorite = !cachedMovie.favorite,
                                        )
                                    }
                                },
                            ) {
                                Text(if (cachedMovie.favorite) "Unfavorite" else "Favorite")
                            }
                        }
                    }
                }
            }
        }

        when {
            loading -> item {
                Text("Loading Movie details…", color = OwnPlayColors.TextMuted)
            }
            detailResult == LibraryMovieDetailLoadResult.UnsupportedSource -> item {
                Text(
                    "Extended Movie details are not supported for this source.",
                    color = OwnPlayColors.TextMuted,
                )
            }
            detailResult == LibraryMovieDetailLoadResult.Failed -> item {
                Text(
                    "Could not load extended Movie details. Showing cached information.",
                    color = OwnPlayColors.TextMuted,
                )
            }
            detailResult == LibraryMovieDetailLoadResult.Unavailable -> item {
                Text(
                    "This Movie is no longer available.",
                    color = OwnPlayColors.TextMuted,
                )
            }
        }
    }
}
