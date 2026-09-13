package app.ownplay.mobile.feature.live.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveOrientationPolicyTest {
    @Test
    fun `physical landscape bands are detected without rotating browse ui`() {
        assertTrue(LiveOrientationPolicy.isLandscape(90))
        assertTrue(LiveOrientationPolicy.isLandscape(270))
        assertFalse(LiveOrientationPolicy.isLandscape(0))
        assertTrue(LiveOrientationPolicy.isPortrait(0))
        assertTrue(LiveOrientationPolicy.isPortrait(180))
    }

    @Test
    fun `manual fullscreen exit stays in preview until portrait rearms orientation entry`() {
        assertTrue(LiveOrientationPolicy.shouldAutoEnterFullscreen(90, armed = true))
        assertFalse(LiveOrientationPolicy.shouldAutoEnterFullscreen(90, armed = false))
        assertFalse(LiveOrientationPolicy.shouldAutoEnterFullscreen(0, armed = true))
    }

    @Test
    fun `fullscreen exits only when physical orientation transitions from landscape to portrait`() {
        assertTrue(LiveOrientationPolicy.shouldAutoExitFullscreen(90, 0))
        assertTrue(LiveOrientationPolicy.shouldAutoExitFullscreen(270, 180))
        assertFalse(LiveOrientationPolicy.shouldAutoExitFullscreen(0, 0))
        assertFalse(LiveOrientationPolicy.shouldAutoExitFullscreen(null, 0))
        assertFalse(LiveOrientationPolicy.shouldAutoExitFullscreen(90, 270))
    }
}
