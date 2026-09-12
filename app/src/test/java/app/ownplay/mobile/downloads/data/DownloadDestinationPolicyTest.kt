package app.ownplay.mobile.downloads.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadDestinationPolicyTest {
    @Test
    fun movieUsesPublicOwnPlayHierarchy() {
        val destination = DownloadDestinationPolicy.movie(
            title = "The Room Below - 2026",
            extension = "mkv",
        )

        assertEquals(
            "OwnPlay Downloads/Movies/The Room Below - 2026",
            destination.relativeDirectory,
        )
        assertEquals("The Room Below - 2026.mkv", destination.displayName)
        assertEquals("video/x-matroska", destination.mimeType)
    }

    @Test
    fun episodeUsesSeriesAndSeasonHierarchy() {
        val destination = DownloadDestinationPolicy.episode(
            seriesTitle = "ITI Stolen Heartbeats (SUB)",
            seasonNumber = 1,
            episodeNumber = 3,
            episodeTitle = "Never Trust an Ambitious Nurse",
            extension = ".mp4",
        )

        assertEquals(
            "OwnPlay Downloads/Series/ITI Stolen Heartbeats (SUB)/Season 01",
            destination.relativeDirectory,
        )
        assertEquals(
            "S01E03 - Never Trust an Ambitious Nurse.mp4",
            destination.displayName,
        )
        assertEquals("video/mp4", destination.mimeType)
    }

    @Test
    fun pathComponentsCannotEscapeOwnPlayHierarchy() {
        val destination = DownloadDestinationPolicy.movie(
            title = " ../Bad\\Name:Test? ",
            extension = "MP4",
        )

        assertTrue(destination.relativeDirectory.startsWith("OwnPlay Downloads/Movies/"))
        assertFalse(destination.relativeDirectory.contains("\\"))
        assertFalse(destination.displayName.contains("/"))
        assertFalse(destination.displayName.contains(":"))
        assertFalse(destination.displayName.contains("?"))
        assertEquals("mp4", destination.displayName.substringAfterLast('.'))
    }

    @Test
    fun extensionCanBeRecoveredFromResolvedUri() {
        assertEquals(
            "ts",
            DownloadDestinationPolicy.extensionFromUri("https://example.test/movie/12345.ts?token=redacted"),
        )
        assertEquals(null, DownloadDestinationPolicy.extensionFromUri("https://example.test/movie/12345"))
    }
}
