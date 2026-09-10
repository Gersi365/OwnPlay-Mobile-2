package app.ownplay.mobile.playback

import app.ownplay.mobile.playback.domain.PlayerLocalControlPolicy
import app.ownplay.mobile.playback.domain.VideoTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerLocalControlPolicyTest {
    @Test
    fun `controls are active only for preview or fullscreen media`() {
        assertTrue(PlayerLocalControlPolicy.canHandlePlayerControls(VideoTarget.PREVIEW, "channel"))
        assertTrue(PlayerLocalControlPolicy.canHandlePlayerControls(VideoTarget.FULLSCREEN, "movie"))
        assertFalse(PlayerLocalControlPolicy.canHandlePlayerControls(VideoTarget.PIP, "channel"))
        assertFalse(PlayerLocalControlPolicy.canHandlePlayerControls(VideoTarget.NONE, "channel"))
        assertFalse(PlayerLocalControlPolicy.canHandlePlayerControls(VideoTarget.PREVIEW, null))
    }

    @Test
    fun `volume hardware steps clamp to player range`() {
        assertEquals(1f, PlayerLocalControlPolicy.volumeAfterStep(0.99f, 1), 0.0001f)
        assertEquals(0f, PlayerLocalControlPolicy.volumeAfterStep(0.01f, -1), 0.0001f)
        assertEquals(0.55f, PlayerLocalControlPolicy.volumeAfterStep(0.5f, 1), 0.0001f)
    }

    @Test
    fun `brightness never reaches an unusable black window`() {
        assertEquals(0.05f, PlayerLocalControlPolicy.clampBrightness(-1f), 0.0001f)
        assertEquals(1f, PlayerLocalControlPolicy.clampBrightness(2f), 0.0001f)
    }

    @Test
    fun `upward swipe increases local level and downward swipe decreases it`() {
        assertTrue(PlayerLocalControlPolicy.normalizedGestureDelta(-100f, 500f) > 0f)
        assertTrue(PlayerLocalControlPolicy.normalizedGestureDelta(100f, 500f) < 0f)
    }
}
