package app.ownplay.mobile.playback.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullscreenOrientationPolicyTest {
    @Test
    fun `orientation bands include dead zones to avoid hand jitter`() {
        assertEquals(PhysicalOrientationBand.PORTRAIT, FullscreenOrientationPolicy.classify(0))
        assertEquals(PhysicalOrientationBand.PORTRAIT, FullscreenOrientationPolicy.classify(180))
        assertEquals(PhysicalOrientationBand.LANDSCAPE, FullscreenOrientationPolicy.classify(90))
        assertEquals(PhysicalOrientationBand.LANDSCAPE, FullscreenOrientationPolicy.classify(270))
        assertEquals(PhysicalOrientationBand.TRANSITION, FullscreenOrientationPolicy.classify(45))
        assertEquals(PhysicalOrientationBand.TRANSITION, FullscreenOrientationPolicy.classify(135))
    }

    @Test
    fun `stable landscape latch emits only after dwell and only once`() {
        val latch = StableOrientationLatch(
            targetBand = PhysicalOrientationBand.LANDSCAPE,
            stabilityMillis = 500L,
        )

        assertFalse(latch.onOrientation(90, 0L))
        assertFalse(latch.onOrientation(92, 499L))
        assertTrue(latch.onOrientation(92, 500L))
        assertFalse(latch.onOrientation(90, 1_000L))
    }

    @Test
    fun `stable landscape latch restarts dwell after leaving target band`() {
        val latch = StableOrientationLatch(
            targetBand = PhysicalOrientationBand.LANDSCAPE,
            stabilityMillis = 500L,
        )

        assertFalse(latch.onOrientation(90, 0L))
        assertFalse(latch.onOrientation(45, 400L))
        assertFalse(latch.onOrientation(90, 450L))
        assertFalse(latch.onOrientation(90, 949L))
        assertTrue(latch.onOrientation(90, 950L))
    }

    @Test
    fun `stable landscape latch ignores stable portrait`() {
        val latch = StableOrientationLatch(
            targetBand = PhysicalOrientationBand.LANDSCAPE,
            stabilityMillis = 500L,
        )

        assertFalse(latch.onOrientation(0, 0L))
        assertFalse(latch.onOrientation(5, 1_000L))
        assertFalse(latch.onOrientation(180, 2_000L))
    }

    @Test
    fun `portrait cannot exit fullscreen before stable physical landscape was confirmed`() {
        val latch = FullscreenOrientationLatch(stabilityMillis = 500L)

        assertFalse(latch.onOrientation(0, 0L))
        assertFalse(latch.onOrientation(5, 750L))
        assertFalse(latch.onOrientation(10, 1_500L))
    }

    @Test
    fun `fullscreen exit requires stable landscape then stable portrait`() {
        val latch = FullscreenOrientationLatch(stabilityMillis = 500L)

        assertFalse(latch.onOrientation(90, 0L))
        assertFalse(latch.onOrientation(92, 499L))
        assertFalse(latch.onOrientation(92, 500L))

        assertFalse(latch.onOrientation(10, 600L))
        assertFalse(latch.onOrientation(8, 1_099L))
        assertTrue(latch.onOrientation(8, 1_100L))
        assertFalse(latch.onOrientation(0, 1_700L))
    }

    @Test
    fun `transition band resets dwell timing`() {
        val latch = FullscreenOrientationLatch(stabilityMillis = 500L)

        assertFalse(latch.onOrientation(90, 0L))
        assertFalse(latch.onOrientation(45, 400L))
        assertFalse(latch.onOrientation(90, 450L))
        assertFalse(latch.onOrientation(90, 949L))
        assertFalse(latch.onOrientation(90, 950L))

        assertFalse(latch.onOrientation(0, 1_000L))
        assertFalse(latch.onOrientation(0, 1_499L))
        assertTrue(latch.onOrientation(0, 1_500L))
    }
}
