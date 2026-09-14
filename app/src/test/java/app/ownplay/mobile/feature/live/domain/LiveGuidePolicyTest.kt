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
    fun `exact program boundary promotes the next program`() {
        val guide = LiveGuidePolicy.nowNext(
            programs = listOf(
                LiveProgram("Current", 900, 1_100),
                LiveProgram("Next", 1_100, 1_300),
            ),
            nowEpochSeconds = 1_100,
        )

        assertEquals("Next", guide.now?.title)
        assertNull(guide.next)
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

    @Test
    fun `current program end is the next relevant boundary`() {
        val guide = LiveGuidePolicy.nowNext(
            programs = listOf(
                LiveProgram("Current", 900, 1_100),
                LiveProgram("Next", 1_100, 1_300),
            ),
            nowEpochSeconds = 1_000,
        )

        assertEquals(1_100L, LiveGuidePolicy.nextBoundaryEpochSeconds(guide, 1_000))
    }

    @Test
    fun `future program start is the next boundary during a guide gap`() {
        val guide = LiveGuidePolicy.nowNext(
            programs = listOf(LiveProgram("Upcoming", 1_200, 1_400)),
            nowEpochSeconds = 1_000,
        )

        assertEquals(1_200L, LiveGuidePolicy.nextBoundaryEpochSeconds(guide, 1_000))
    }

    @Test
    fun `past timed guide does not become current through list-order fallback`() {
        val guide = LiveGuidePolicy.nowNext(
            programs = listOf(
                LiveProgram("Old one", 600, 800),
                LiveProgram("Old two", 800, 950),
            ),
            nowEpochSeconds = 1_000,
        )

        assertNull(guide.now)
        assertNull(guide.next)
        assertNull(LiveGuidePolicy.nextBoundaryEpochSeconds(guide, 1_000))
    }

    @Test
    fun `timestamp-less provider guide uses list order`() {
        val guide = LiveGuidePolicy.nowNext(
            programs = listOf(
                LiveProgram("Provider first", null, null),
                LiveProgram("Provider second", null, null),
            ),
            nowEpochSeconds = 1_000,
        )

        assertEquals("Provider first", guide.now?.title)
        assertEquals("Provider second", guide.next?.title)
        assertNull(LiveGuidePolicy.nextBoundaryEpochSeconds(guide, 1_000))
    }
}
