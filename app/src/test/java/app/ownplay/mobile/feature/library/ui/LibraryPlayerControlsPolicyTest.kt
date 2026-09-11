package app.ownplay.mobile.feature.library.ui

import app.ownplay.mobile.playback.domain.PlaybackPhase
import app.ownplay.mobile.playback.domain.PlaybackSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryPlayerControlsPolicyTest {
    @Test
    fun `auto hide only while ready and actively playing`() {
        assertTrue(
            LibraryPlayerControlsPolicy.shouldAutoHide(
                PlaybackSnapshot(phase = PlaybackPhase.READY, isPlaying = true),
            ),
        )
    }

    @Test
    fun `paused controls stay visible`() {
        assertFalse(
            LibraryPlayerControlsPolicy.shouldAutoHide(
                PlaybackSnapshot(phase = PlaybackPhase.READY, isPlaying = false),
            ),
        )
    }

    @Test
    fun `buffering error ended idle and released controls stay visible`() {
        listOf(
            PlaybackPhase.BUFFERING,
            PlaybackPhase.ERROR,
            PlaybackPhase.ENDED,
            PlaybackPhase.IDLE,
            PlaybackPhase.RELEASED,
        ).forEach { phase ->
            assertFalse(
                "phase=$phase must not auto-hide controls",
                LibraryPlayerControlsPolicy.shouldAutoHide(
                    PlaybackSnapshot(phase = phase, isPlaying = true),
                ),
            )
        }
    }
}
