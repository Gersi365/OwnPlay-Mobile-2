package app.ownplay.mobile.playback.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSpeedPolicyTest {
    @Test
    fun `supported speeds include normal and common vod choices`() {
        assertEquals(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f), PlaybackSpeedPolicy.supportedSpeeds)
    }

    @Test
    fun `requested speed normalizes to nearest supported choice`() {
        assertEquals(1.25f, PlaybackSpeedPolicy.normalize(1.3f))
        assertEquals(1f, PlaybackSpeedPolicy.normalize(Float.NaN))
    }

    @Test
    fun `labels are stable`() {
        assertEquals("1.0×", PlaybackSpeedPolicy.label(1f))
        assertEquals("1.5×", PlaybackSpeedPolicy.label(1.5f))
    }
}
