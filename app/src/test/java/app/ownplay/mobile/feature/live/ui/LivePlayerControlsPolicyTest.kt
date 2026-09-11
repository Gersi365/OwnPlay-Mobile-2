package app.ownplay.mobile.feature.live.ui

import app.ownplay.mobile.playback.domain.PlaybackPhase
import app.ownplay.mobile.playback.domain.PlaybackSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePlayerControlsPolicyTest {
    @Test
    fun `auto hide only while ready and actively playing`() {
        assertTrue(
            LivePlayerControlsPolicy.shouldAutoHide(
                PlaybackSnapshot(phase = PlaybackPhase.READY, isPlaying = true),
            ),
        )
    }

    @Test
    fun `paused controls stay visible`() {
        assertFalse(
            LivePlayerControlsPolicy.shouldAutoHide(
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
                LivePlayerControlsPolicy.shouldAutoHide(
                    PlaybackSnapshot(phase = phase, isPlaying = true),
                ),
            )
        }
    }
}
