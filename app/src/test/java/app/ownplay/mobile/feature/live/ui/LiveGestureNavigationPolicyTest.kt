package app.ownplay.mobile.feature.live.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveGestureNavigationPolicyTest {
    @Test
    fun `horizontal swipe direction uses a deliberate threshold`() {
        assertEquals(
            LiveNavigationDirection.NEXT,
            LiveGestureNavigationPolicy.directionForHorizontalSwipe(-80f, minDistancePx = 56f),
        )
        assertEquals(
            LiveNavigationDirection.PREVIOUS,
            LiveGestureNavigationPolicy.directionForHorizontalSwipe(80f, minDistancePx = 56f),
        )
        assertNull(LiveGestureNavigationPolicy.directionForHorizontalSwipe(-40f, minDistancePx = 56f))
        assertNull(LiveGestureNavigationPolicy.directionForHorizontalSwipe(40f, minDistancePx = 56f))
    }

    @Test
    fun `adjacent navigation wraps for continuous one handed browsing`() {
        val keys = listOf("one", "two", "three")

        assertEquals(
            "three",
            LiveGestureNavigationPolicy.adjacentKey(keys, "two", LiveNavigationDirection.NEXT),
        )
        assertEquals(
            "one",
            LiveGestureNavigationPolicy.adjacentKey(keys, "two", LiveNavigationDirection.PREVIOUS),
        )
        assertEquals(
            "one",
            LiveGestureNavigationPolicy.adjacentKey(keys, "three", LiveNavigationDirection.NEXT),
        )
        assertEquals(
            "three",
            LiveGestureNavigationPolicy.adjacentKey(keys, "one", LiveNavigationDirection.PREVIOUS),
        )
    }

    @Test
    fun `navigation ignores single item lists and resolves missing current key deterministically`() {
        assertNull(
            LiveGestureNavigationPolicy.adjacentKey(
                listOf("only"),
                "only",
                LiveNavigationDirection.NEXT,
            ),
        )
        assertEquals(
            "one",
            LiveGestureNavigationPolicy.adjacentKey(
                listOf("one", "two"),
                "missing",
                LiveNavigationDirection.NEXT,
            ),
        )
        assertEquals(
            "two",
            LiveGestureNavigationPolicy.adjacentKey(
                listOf("one", "two"),
                "missing",
                LiveNavigationDirection.PREVIOUS,
            ),
        )
    }
}
