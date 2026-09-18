package app.ownplay.mobile.feature.library.data

import app.ownplay.mobile.data.db.LibraryEpisodeProgressRow
import app.ownplay.mobile.data.db.LibraryMovieProgressRow
import app.ownplay.mobile.feature.library.domain.LibraryContentKind
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryContinueWatchingMapperTest {
    @Test
    fun continueWatchingCombinesMovieAndEpisodeByMostRecentProgress() {
        val items = LibraryCatalogMapper.continueWatching(
            movieRows = listOf(
                LibraryMovieProgressRow(
                    contentId = "movie-a",
                    title = "Movie A",
                    posterUrl = null,
                    positionMs = 10_000L,
                    durationMs = 100_000L,
                    updatedAt = 100L,
                ),
            ),
            episodeRows = listOf(
                LibraryEpisodeProgressRow(
                    contentId = "episode-a",
                    title = "Episode A",
                    seriesTitle = "Series A",
                    posterUrl = null,
                    seasonNumber = 2,
                    episodeNumber = 3,
                    positionMs = 20_000L,
                    durationMs = 80_000L,
                    updatedAt = 200L,
                ),
            ),
        )

        assertEquals(listOf("episode-a", "movie-a"), items.map { it.contentId })
        assertEquals(LibraryContentKind.EPISODE, items.first().contentKind)
        assertEquals("Series A", items.first().seriesTitle)
        assertEquals(2, items.first().seasonNumber)
        assertEquals(3, items.first().episodeNumber)
    }

    @Test
    fun continueWatchingUsesDeterministicTieBreakForSameTimestamp() {
        val items = LibraryCatalogMapper.continueWatching(
            movieRows = listOf(
                LibraryMovieProgressRow("movie-b", "Movie B", null, 1L, 10L, 100L),
                LibraryMovieProgressRow("movie-a", "Movie A", null, 1L, 10L, 100L),
            ),
            episodeRows = emptyList(),
        )

        assertEquals(listOf("movie-a", "movie-b"), items.map { it.contentId })
    }
}
