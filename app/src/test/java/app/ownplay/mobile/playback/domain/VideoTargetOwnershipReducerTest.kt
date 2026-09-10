package app.ownplay.mobile.playback.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class VideoTargetOwnershipReducerTest {
    @Test
    fun targetTransferAdvancesGeneration() {
        val preview = VideoTargetOwnershipReducer.reduce(
            VideoTargetOwnership(),
            VideoTargetEvent.Acquire(VideoTarget.PREVIEW),
        )
        val fullscreen = VideoTargetOwnershipReducer.reduce(
            preview,
            VideoTargetEvent.Acquire(VideoTarget.FULLSCREEN),
        )

        assertEquals(VideoTarget.PREVIEW, preview.activeTarget)
        assertEquals(1L, preview.generation)
        assertEquals(VideoTarget.FULLSCREEN, fullscreen.activeTarget)
        assertEquals(2L, fullscreen.generation)
    }

    @Test
    fun staleReleaseCannotDetachNewOwner() {
        val fullscreen = VideoTargetOwnership(
            activeTarget = VideoTarget.FULLSCREEN,
            generation = 2L,
        )

        val result = VideoTargetOwnershipReducer.reduce(
            fullscreen,
            VideoTargetEvent.Release(VideoTarget.PREVIEW),
        )

        assertSame(fullscreen, result)
    }

    @Test
    fun releasingActiveTargetReturnsOwnershipToNone() {
        val result = VideoTargetOwnershipReducer.reduce(
            VideoTargetOwnership(activeTarget = VideoTarget.PIP, generation = 4L),
            VideoTargetEvent.Release(VideoTarget.PIP),
        )

        assertEquals(VideoTarget.NONE, result.activeTarget)
        assertEquals(5L, result.generation)
    }

    @Test
    fun acquiringSameTargetIsIdempotent() {
        val preview = VideoTargetOwnership(
            activeTarget = VideoTarget.PREVIEW,
            generation = 1L,
        )

        val result = VideoTargetOwnershipReducer.reduce(
            preview,
            VideoTargetEvent.Acquire(VideoTarget.PREVIEW),
        )

        assertSame(preview, result)
    }
}
