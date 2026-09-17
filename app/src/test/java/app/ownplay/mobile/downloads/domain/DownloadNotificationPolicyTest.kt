package app.ownplay.mobile.downloads.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadNotificationPolicyTest {
    @Test
    fun `active download exposes pause with determinate progress`() {
        val presentation = DownloadNotificationPolicy.present(snapshot(DownloadState.DOWNLOADING, 42L, 100L))

        assertTrue(presentation.visible)
        assertEquals("Downloading · 42%", presentation.statusText)
        assertEquals(42, presentation.progressPercent)
        assertFalse(presentation.progressIndeterminate)
        assertEquals(DownloadAction.PAUSE, presentation.action)
    }

    @Test
    fun `queued download exposes pause and preserves partial progress`() {
        val presentation = DownloadNotificationPolicy.present(snapshot(DownloadState.QUEUED, 25L, 100L))

        assertTrue(presentation.visible)
        assertEquals("Queued · 25%", presentation.statusText)
        assertEquals(DownloadAction.PAUSE, presentation.action)
    }

    @Test
    fun `paused download exposes resume without indeterminate animation`() {
        val presentation = DownloadNotificationPolicy.present(snapshot(DownloadState.PAUSED, 7L, null))

        assertTrue(presentation.visible)
        assertEquals("Paused", presentation.statusText)
        assertNull(presentation.progressPercent)
        assertFalse(presentation.progressIndeterminate)
        assertEquals(DownloadAction.RESUME, presentation.action)
    }

    @Test
    fun `unknown active total uses indeterminate progress`() {
        val presentation = DownloadNotificationPolicy.present(snapshot(DownloadState.DOWNLOADING, 4096L, null))

        assertTrue(presentation.progressIndeterminate)
        assertNull(presentation.progressPercent)
    }

    @Test
    fun `completed and failed downloads do not keep persistent control notifications`() {
        listOf(DownloadState.COMPLETED, DownloadState.FAILED).forEach { state ->
            val presentation = DownloadNotificationPolicy.present(snapshot(state, 100L, 100L))
            assertFalse(presentation.visible)
            assertNull(presentation.action)
        }
    }

    private fun snapshot(state: DownloadState, bytes: Long, total: Long?) = DownloadNotificationSnapshot(
        downloadId = "download-id",
        title = "Movie title",
        state = state,
        bytesDownloaded = bytes,
        totalBytes = total,
    )
}
