package app.ownplay.mobile.feature.library.ui

import app.ownplay.mobile.feature.library.domain.LibraryMediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryDetailNavigationPolicyTest {
    @Test
    fun `movie shelf item opens canonical movie details`() {
        assertEquals(
            LibraryDetailTargetStage33.Movie("movie-1"),
            LibraryDetailNavigationPolicyStage33.target(LibraryMediaKind.MOVIE, "movie-1"),
        )
    }

    @Test
    fun `episode shelf item opens parent series and preserves episode focus`() {
        assertEquals(
            LibraryDetailTargetStage33.Series("series-1", "episode-7"),
            LibraryDetailNavigationPolicyStage33.target(
                mediaKind = LibraryMediaKind.EPISODE,
                contentId = "episode-7",
                episodeSeriesId = "series-1",
            ),
        )
    }

    @Test
    fun `episode without parent series cannot invent a detail target`() {
        assertNull(
            LibraryDetailNavigationPolicyStage33.target(
                mediaKind = LibraryMediaKind.EPISODE,
                contentId = "episode-7",
                episodeSeriesId = null,
            ),
        )
    }
}
