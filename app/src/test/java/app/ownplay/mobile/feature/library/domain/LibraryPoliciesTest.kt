package app.ownplay.mobile.feature.library.domain

import app.ownplay.mobile.playback.domain.PlaybackStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryPoliciesTest {
    @Test
    fun `resume uses positive saved position`() {
        val start = LibraryStartPolicy.resolve(
            startMode = LibraryStartMode.RESUME,
            savedPositionMs = 42_000L,
        )

        assertEquals(PlaybackStart.Resume(42_000L), start)
    }

    @Test
    fun `resume without usable progress starts from beginning`() {
        assertEquals(
            PlaybackStart.Beginning,
            LibraryStartPolicy.resolve(LibraryStartMode.RESUME, null),
        )
        assertEquals(
            PlaybackStart.Beginning,
            LibraryStartPolicy.resolve(LibraryStartMode.RESUME, 0L),
        )
    }

    @Test
    fun `play from beginning ignores existing resume position`() {
        assertEquals(
            PlaybackStart.Beginning,
            LibraryStartPolicy.resolve(LibraryStartMode.BEGINNING, 88_000L),
        )
    }

    @Test
    fun `completion threshold is explicit at ninety five percent`() {
        assertFalse(
            LibraryCompletionPolicy.isComplete(
                positionMs = 94_999L,
                durationMs = 100_000L,
                ended = false,
            ),
        )
        assertTrue(
            LibraryCompletionPolicy.isComplete(
                positionMs = 95_000L,
                durationMs = 100_000L,
                ended = false,
            ),
        )
        assertTrue(
            LibraryCompletionPolicy.isComplete(
                positionMs = 1L,
                durationMs = 100_000L,
                ended = true,
            ),
        )
    }

    @Test
    fun `movie ordering is provider order then stable title and id`() {
        val ordered = LibraryOrderingPolicy.movies(
            listOf(
                movie(id = "c", name = "Zulu", order = 2),
                movie(id = "b", name = "Alpha", order = 1),
                movie(id = "a", name = "alpha", order = 1),
            ),
        )

        assertEquals(listOf("a", "b", "c"), ordered.map { it.movieId })
    }

    @Test
    fun `continue watching ordering is most recently updated first`() {
        val ordered = LibraryOrderingPolicy.continueWatching(
            listOf(
                continueItem(id = "older", updatedAt = 10L),
                continueItem(id = "newer-b", updatedAt = 20L),
                continueItem(id = "newer-a", updatedAt = 20L),
            ),
        )

        assertEquals(listOf("newer-a", "newer-b", "older"), ordered.map { it.contentId })
    }

    @Test
    fun `download ordering depends on creation identity not progress mutations`() {
        val ordered = LibraryOrderingPolicy.downloadedMedia(
            listOf(
                download(id = "b", createdAt = 50L),
                download(id = "a", createdAt = 50L),
                download(id = "newest", createdAt = 60L),
            ),
        )

        assertEquals(listOf("newest", "a", "b"), ordered.map { it.downloadId })
    }

    private fun movie(id: String, name: String, order: Int) = LibraryMovie(
        movieId = id,
        sourceId = "source",
        name = name,
        posterUrl = null,
        backdropUrl = null,
        rating = null,
        providerOrder = order,
        resumePositionMs = null,
        durationMs = null,
    )

    private fun continueItem(id: String, updatedAt: Long) = ContinueWatchingItem(
        sourceId = "source",
        contentId = id,
        mediaKind = LibraryMediaKind.MOVIE,
        title = id,
        subtitle = null,
        artworkUrl = null,
        positionMs = 10L,
        durationMs = 100L,
        updatedAt = updatedAt,
    )

    private fun download(id: String, createdAt: Long) = LibraryDownloadedMedia(
        downloadId = id,
        sourceId = "source",
        mediaKind = LibraryMediaKind.MOVIE,
        contentId = id,
        title = id,
        createdAt = createdAt,
    )
}
