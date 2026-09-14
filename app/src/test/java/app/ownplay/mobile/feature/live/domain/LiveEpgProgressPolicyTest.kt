package app.ownplay.mobile.feature.live.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveEpgProgressPolicyTest {
    private val program = LiveProgram(
        title = "Current show",
        startEpochSeconds = 1_000L,
        endEpochSeconds = 1_600L,
    )

    @Test
    fun `computes bounded current program progress`() {
        assertEquals(0f, LiveEpgProgressPolicy.fraction(program, 1_000L))
        assertEquals(0.5f, LiveEpgProgressPolicy.fraction(program, 1_300L))
        assertEquals(1f, LiveEpgProgressPolicy.fraction(program, 1_600L))
    }

    @Test
    fun `does not draw progress for missing or stale timing`() {
        assertNull(LiveEpgProgressPolicy.fraction(null, 1_300L))
        assertNull(LiveEpgProgressPolicy.fraction(program.copy(startEpochSeconds = null), 1_300L))
        assertNull(LiveEpgProgressPolicy.fraction(program.copy(endEpochSeconds = null), 1_300L))
        assertNull(LiveEpgProgressPolicy.fraction(program, 999L))
        assertNull(LiveEpgProgressPolicy.fraction(program, 1_601L))
        assertNull(LiveEpgProgressPolicy.fraction(program.copy(endEpochSeconds = 1_000L), 1_000L))
    }
}
