package app.ownplay.mobile.feature.live.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveGuidePolicyTest {
    @Test
    fun `now next are selected from provider timestamps`() {
        val guide = LiveGuidePolicy.nowNext(
            programs = listOf(
                LiveProgram("Previous", 700, 900),
                LiveProgram("Current", 900, 1_100),
                LiveProgram("Next", 1_100, 1_300),
            ),
            nowEpochSeconds = 1_000,
        )
        assertEquals("Current", guide.now?.title)
        assertEquals("Next", guide.next?.title)
    }

    @Test
    fun `future-only guide exposes next without inventing now`() {
        val guide = LiveGuidePolicy.nowNext(
            programs = listOf(LiveProgram("Upcoming", 1_200, 1_400)),
            nowEpochSeconds = 1_000,
        )
        assertNull(guide.now)
        assertEquals("Upcoming", guide.next?.title)
    }
}
