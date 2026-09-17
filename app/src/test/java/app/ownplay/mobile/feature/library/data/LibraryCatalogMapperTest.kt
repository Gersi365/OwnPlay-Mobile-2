package app.ownplay.mobile.feature.library.data

import app.ownplay.mobile.data.db.EpisodeEntity
import app.ownplay.mobile.data.db.MediaFavoriteEntity
import app.ownplay.mobile.data.db.MovieEntity
import app.ownplay.mobile.data.db.ProviderCategoryEntity
import app.ownplay.mobile.data.db.SeriesEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCatalogMapperTest {
    @Test
    fun catalogPreservesProviderOrderAndAppliesSeparateFavorites() {
        val snapshot = LibraryCatalogMapper.catalog(
            movieCategories = listOf(category("movie-b", "MOVIE", "Movies B", 1)),
            seriesCategories = listOf(category("series-a", "SERIES", "Series A", 0)),
            movies = listOf(movie("movie-2", 2), movie("movie-1", 5)),
            series = listOf(series("series-1", 3)),
            favorites = listOf(
                MediaFavoriteEntity("source-a", "MOVIE", "movie-1", 10L),
            ),
        )

        assertEquals(listOf("movie-2", "movie-1"), snapshot.movies.map { it.movieId })
        assertFalse(snapshot.movies[0].favorite)
        assertTrue(snapshot.movies[1].favorite)
        assertFalse(snapshot.series.single().favorite)
        assertEquals("Movies B", snapshot.movieCategories.single().displayName)
        assertEquals("Series A", snapshot.seriesCategories.single().displayName)
    }

    @Test
    fun seriesDetailGroupsCachedEpisodesBySeasonAndEpisodeOrder() {
        val detail = LibraryCatalogMapper.seriesDetail(
            seriesEntity = series("series-1", 0),
            episodes = listOf(
                episode("e-3", 2, 1),
                episode("e-2", 1, 2),
                episode("e-1", 1, 1),
            ),
            favoriteIds = setOf("series-1"),
        )

        assertTrue(detail.series.favorite)
        assertEquals(listOf(1, 2), detail.seasons.map { it.seasonNumber })
        assertEquals(listOf("e-1", "e-2"), detail.seasons.first().episodes.map { it.episodeId })
    }

    private fun category(
        categoryKey: String,
        kind: String,
        name: String,
        order: Int,
    ) = ProviderCategoryEntity(
        sourceId = "source-a",
        kind = kind,
        categoryKey = categoryKey,
        providerKey = categoryKey,
        name = name,
        providerOrder = order,
        available = true,
        lastSeenGeneration = 1L,
    )

    private fun movie(id: String, order: Int) = MovieEntity(
        movieId = id,
        sourceId = "source-a",
        providerStreamId = id,
        categoryKey = "movie-b",
        name = id,
        posterUrl = null,
        backdropUrl = null,
        extension = "mp4",
        rating = null,
        providerOrder = order,
        available = true,
        lastSeenGeneration = 1L,
    )

    private fun series(id: String, order: Int) = SeriesEntity(
        seriesId = id,
        sourceId = "source-a",
        providerSeriesId = id,
        categoryKey = "series-a",
        name = id,
        posterUrl = null,
        backdropUrl = null,
        description = null,
        rating = null,
        providerOrder = order,
        available = true,
        lastSeenGeneration = 1L,
    )

    private fun episode(
        id: String,
        season: Int,
        number: Int,
    ) = EpisodeEntity(
        episodeId = id,
        seriesId = "series-1",
        seasonNumber = season,
        episodeNumber = number,
        providerEpisodeId = id,
        title = id,
        durationMs = null,
        streamLocator = "opaque://$id",
        extension = "mp4",
        available = true,
        lastSeenGeneration = 1L,
    )
}
