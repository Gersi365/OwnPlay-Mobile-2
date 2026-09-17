package app.ownplay.mobile.feature.library.domain

import app.ownplay.mobile.sources.domain.SourceId
import kotlinx.coroutines.flow.Flow

enum class LibraryContentKind {
    MOVIE,
    SERIES,
    EPISODE,
}

enum class LibraryDetailRefreshResult {
    REFRESHED,
    UNAVAILABLE,
    UNSUPPORTED_SOURCE,
    FAILED,
}

data class LibraryCategory(
    val categoryId: String,
    val displayName: String,
    val providerOrder: Int,
)

data class LibraryMovieSummary(
    val movieId: String,
    val categoryId: String?,
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val rating: String?,
    val providerOrder: Int,
    val favorite: Boolean,
)

data class LibrarySeriesSummary(
    val seriesId: String,
    val categoryId: String?,
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val description: String?,
    val rating: String?,
    val providerOrder: Int,
    val favorite: Boolean,
)

data class LibraryEpisodeSummary(
    val episodeId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val durationMs: Long?,
)

data class LibrarySeason(
    val seasonNumber: Int,
    val episodes: List<LibraryEpisodeSummary>,
)

data class LibrarySeriesDetail(
    val series: LibrarySeriesSummary,
    val seasons: List<LibrarySeason>,
)

data class LibraryCatalogSnapshot(
    val movieCategories: List<LibraryCategory>,
    val seriesCategories: List<LibraryCategory>,
    val movies: List<LibraryMovieSummary>,
    val series: List<LibrarySeriesSummary>,
)

interface LibraryRepository {
    fun observeCatalog(sourceId: SourceId): Flow<LibraryCatalogSnapshot>

    fun observeMovie(
        sourceId: SourceId,
        movieId: String,
    ): Flow<LibraryMovieSummary?>

    fun observeSeriesDetail(
        sourceId: SourceId,
        seriesId: String,
    ): Flow<LibrarySeriesDetail?>

    suspend fun refreshSeriesDetail(
        sourceId: SourceId,
        seriesId: String,
    ): LibraryDetailRefreshResult

    suspend fun setFavorite(
        sourceId: SourceId,
        contentKind: LibraryContentKind,
        contentId: String,
        favorite: Boolean,
    ): Boolean
}

object LibraryDetailStartPolicy {
    fun firstAvailableEpisode(detail: LibrarySeriesDetail): LibraryEpisodeSummary? =
        detail.seasons
            .asSequence()
            .sortedBy(LibrarySeason::seasonNumber)
            .flatMap { season ->
                season.episodes
                    .asSequence()
                    .sortedWith(
                        compareBy<LibraryEpisodeSummary>(
                            LibraryEpisodeSummary::episodeNumber,
                            LibraryEpisodeSummary::episodeId,
                        ),
                    )
            }
            .firstOrNull()

    fun selectedOrFirstAvailable(
        detail: LibrarySeriesDetail,
        selectedEpisodeId: String?,
    ): LibraryEpisodeSummary? {
        val selected = selectedEpisodeId
            ?.takeIf(String::isNotBlank)
            ?.let { episodeId ->
                detail.seasons
                    .asSequence()
                    .flatMap { it.episodes.asSequence() }
                    .firstOrNull { it.episodeId == episodeId }
            }
        return selected ?: firstAvailableEpisode(detail)
    }
}
