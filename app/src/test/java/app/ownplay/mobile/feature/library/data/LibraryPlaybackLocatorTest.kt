package app.ownplay.mobile.feature.library.data

import app.ownplay.mobile.feature.library.domain.LibraryMediaKind
import app.ownplay.mobile.feature.library.domain.ResolvedLibraryPlayback
import app.ownplay.mobile.playback.domain.PlaybackStart
import app.ownplay.mobile.playback.domain.PlaybackStreamFormat
import app.ownplay.mobile.sources.data.xtream.XtreamUrlBuilder
import app.ownplay.mobile.sources.domain.SourceCredential
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryPlaybackLocatorTest {
    private val credential = SourceCredential.Xtream(
        username = "viewer name",
        password = "p@ss word",
    )

    @Test
    fun `movie playback URL is built at runtime and can be redacted`() {
        val uri = LibraryPlaybackLocator.movieUri(
            baseUrl = "https://provider.example",
            credential = credential,
            providerStreamId = "42",
            extension = "mp4",
        )

        assertEquals(
            "https://provider.example/movie/viewer%20name/p%40ss%20word/42.mp4",
            uri,
        )
        val redacted = XtreamUrlBuilder.redact(uri)
        assertFalse(redacted.contains("viewer%20name"))
        assertFalse(redacted.contains("p%40ss%20word"))
        assertTrue(redacted.contains("<redacted>"))
    }

    @Test
    fun `episode playback URL uses Xtream series stream path`() {
        val uri = LibraryPlaybackLocator.episodeUri(
            baseUrl = "https://provider.example/base",
            credential = credential,
            providerEpisodeId = "84",
            extension = "m3u8",
        )

        assertEquals(
            "https://provider.example/base/series/viewer%20name/p%40ss%20word/84.m3u8",
            uri,
        )
        assertEquals(PlaybackStreamFormat.HLS, LibraryPlaybackLocator.streamFormatFor(uri))
    }

    @Test
    fun `non HLS locator uses automatic stream format`() {
        assertEquals(
            PlaybackStreamFormat.AUTO,
            LibraryPlaybackLocator.streamFormatFor("https://provider.example/movie/a/b/42.mp4"),
        )
    }

    @Test
    fun `resolved playback string representation never exposes stream URI`() {
        val uri = LibraryPlaybackLocator.movieUri(
            baseUrl = "https://provider.example",
            credential = credential,
            providerStreamId = "42",
            extension = "mp4",
        )
        val resolved = ResolvedLibraryPlayback(
            sourceId = "source-1",
            contentId = "movie-1",
            mediaKind = LibraryMediaKind.MOVIE,
            title = "Movie",
            subtitle = "Movie",
            uri = uri,
            streamFormat = PlaybackStreamFormat.AUTO,
            start = PlaybackStart.Beginning,
            knownDurationMs = null,
        )

        val text = resolved.toString()
        assertFalse(text.contains(uri))
        assertFalse(text.contains("viewer"))
        assertFalse(text.contains("p@ss"))
        assertTrue(text.contains("uri=<redacted>"))
    }
}
