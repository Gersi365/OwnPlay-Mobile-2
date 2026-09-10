package app.ownplay.mobile.feature.live.ui

import app.ownplay.mobile.playback.domain.PlaybackPhase
import app.ownplay.mobile.playback.domain.PlaybackSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePlaybackFallbackPolicyTest {
    @Test
    fun `player error uses fallback`() {
        assertTrue(LivePlaybackFallbackPolicy.shouldUseFallback(PlaybackSnapshot(phase = PlaybackPhase.ERROR)))
    }

    @Test
    fun `ready stream with unsupported audio uses fallback`() {
        assertTrue(
            LivePlaybackFallbackPolicy.shouldUseFallback(
                PlaybackSnapshot(
                    phase = PlaybackPhase.READY,
                    audioTrackPresent = true,
                    audioTrackSupported = false,
                    audioTrackSelected = false,
                ),
            ),
        )
    }

    @Test
    fun `ready stream with selected supported audio stays on primary`() {
        assertFalse(
            LivePlaybackFallbackPolicy.shouldUseFallback(
                PlaybackSnapshot(
                    phase = PlaybackPhase.READY,
                    audioTrackPresent = true,
                    audioTrackSupported = true,
                    audioTrackSelected = true,
                ),
            ),
        )
    }
}
