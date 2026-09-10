package app.ownplay.mobile.playback.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackStartPolicyTest {
    @Test
    fun defaultLeavesPositionUnspecifiedWithoutClearingProgress() {
        val decision = PlaybackStartPolicy.resolve(PlaybackStart.Default)

        assertNull(decision.positionMs)
        assertFalse(decision.clearSavedProgressBeforeStart)
    }

    @Test
    fun resumeUsesSavedPositionAndClampsNegativeValues() {
        assertEquals(
            42_000L,
            PlaybackStartPolicy.resolve(PlaybackStart.Resume(42_000L)).positionMs,
        )
        assertEquals(
            0L,
            PlaybackStartPolicy.resolve(PlaybackStart.Resume(-1L)).positionMs,
        )
    }

    @Test
    fun beginningStartsAtZeroWithoutPrematurelyClearingSavedProgress() {
        val decision = PlaybackStartPolicy.resolve(PlaybackStart.Beginning)

        assertEquals(0L, decision.positionMs)
        assertFalse(decision.clearSavedProgressBeforeStart)
    }
}
