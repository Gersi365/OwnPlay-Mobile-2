package app.ownplay.mobile.downloads.domain

import app.ownplay.mobile.feature.library.domain.LibraryMediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadPoliciesTest {
    @Test
    fun unmanagedDownloadCreatesQueuedWork() {
        assertEquals(
            DownloadTransition.CREATE_QUEUED,
            DownloadStatePolicy.transition(null, DownloadAction.DOWNLOAD),
        )
    }

    @Test
    fun activeDownloadCanPauseAndPausedDownloadCanResume() {
        assertEquals(
            DownloadTransition.MARK_PAUSED,
            DownloadStatePolicy.transition(DownloadState.DOWNLOADING, DownloadAction.PAUSE),
        )
        assertEquals(
            DownloadTransition.MARK_QUEUED,
            DownloadStatePolicy.transition(DownloadState.PAUSED, DownloadAction.RESUME),
        )
    }

    @Test
    fun failedDownloadCanRetryAndCompletedDownloadCanPlayOffline() {
        assertEquals(
            DownloadTransition.MARK_QUEUED,
            DownloadStatePolicy.transition(DownloadState.FAILED, DownloadAction.RETRY),
        )
        assertEquals(
            DownloadTransition.PLAY_OFFLINE,
            DownloadStatePolicy.transition(DownloadState.COMPLETED, DownloadAction.PLAY_OFFLINE),
        )
        assertEquals(
            DownloadTransition.PLAY_OFFLINE,
            DownloadStatePolicy.transition(DownloadState.COMPLETED, DownloadAction.RESUME_OFFLINE),
        )
    }

    @Test
    fun invalidTransitionsAreRejected() {
        assertNull(DownloadStatePolicy.transition(DownloadState.PAUSED, DownloadAction.PAUSE))
        assertNull(DownloadStatePolicy.transition(DownloadState.COMPLETED, DownloadAction.RETRY))
        assertNull(DownloadStatePolicy.transition(null, DownloadAction.REMOVE))
    }

    @Test
    fun allManagedDownloadsCanBeRemovedOutsideFullscreen() {
        DownloadState.entries.forEach { state ->
            assertEquals(
                DownloadTransition.REMOVE,
                DownloadStatePolicy.transition(state, DownloadAction.REMOVE),
            )
        }
    }

    @Test
    fun completedPrimaryActionUsesResumeWhenProgressExists() {
        val withoutProgress = item(state = DownloadState.COMPLETED, resumePositionMs = null)
        val withProgress = item(state = DownloadState.COMPLETED, resumePositionMs = 42_000L)

        assertEquals(DownloadAction.PLAY_OFFLINE, DownloadStatePolicy.primaryAction(withoutProgress))
        assertEquals(DownloadAction.RESUME_OFFLINE, DownloadStatePolicy.primaryAction(withProgress))
    }

    @Test
    fun progressUpdatesDoNotChangeStableDownloadOrdering() {
        val newer = item(
            id = "newer",
            state = DownloadState.DOWNLOADING,
            createdAt = 200L,
            bytesDownloaded = 1L,
        )
        val older = item(
            id = "older",
            state = DownloadState.DOWNLOADING,
            createdAt = 100L,
            bytesDownloaded = 999L,
        )
        val initial = DownloadOrderingPolicy.ordered(listOf(older, newer)).map { it.downloadId }
        val updated = DownloadOrderingPolicy.ordered(
            listOf(
                older.copy(bytesDownloaded = 5_000L, updatedAt = 5_000L),
                newer.copy(bytesDownloaded = 2L, updatedAt = 4_000L),
            ),
        ).map { it.downloadId }

        assertEquals(listOf("newer", "older"), initial)
        assertEquals(initial, updated)
    }

    @Test
    fun onlinePlaybackDoesNotMutateDownloadState() {
        DownloadState.entries.forEach { state ->
            assertEquals(state, DownloadStatePolicy.stateAfterOnlinePlaybackStarts(state))
        }
        assertNull(DownloadStatePolicy.stateAfterOnlinePlaybackStarts(null))
    }

    private fun item(
        id: String = "download",
        state: DownloadState,
        createdAt: Long = 1L,
        bytesDownloaded: Long = 0L,
        resumePositionMs: Long? = null,
    ): DownloadItem = DownloadItem(
        downloadId = id,
        sourceId = "source",
        mediaKind = LibraryMediaKind.MOVIE,
        contentId = "content",
        title = "Title",
        state = state,
        bytesDownloaded = bytesDownloaded,
        totalBytes = 10_000L,
        createdAt = createdAt,
        updatedAt = createdAt,
        resumePositionMs = resumePositionMs,
    )
}
