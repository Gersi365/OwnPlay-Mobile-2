package app.ownplay.mobile.playback.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureInPicturePolicyTest {
    @Test
    fun readyFullscreenPlaybackCanEnterPictureInPicture() {
        assertTrue(
            PictureInPicturePolicy.canEnter(
                enabled = true,
                contentFullscreen = true,
                playback = playback(
                    phase = PlaybackPhase.READY,
                    target = VideoTarget.FULLSCREEN,
                ),
            ),
        )
    }

    @Test
    fun bufferingFullscreenPlaybackCanEnterPictureInPicture() {
        assertTrue(
            PictureInPicturePolicy.canEnter(
                enabled = true,
                contentFullscreen = true,
                playback = playback(
                    phase = PlaybackPhase.BUFFERING,
                    target = VideoTarget.FULLSCREEN,
                ),
            ),
        )
    }

    @Test
    fun disabledPreferenceBlocksPictureInPicture() {
        assertFalse(
            PictureInPicturePolicy.canEnter(
                enabled = false,
                contentFullscreen = true,
                playback = playback(),
            ),
        )
    }

    @Test
    fun pausedPlaybackCannotAutoEnterPictureInPicture() {
        assertFalse(
            PictureInPicturePolicy.canEnter(
                enabled = true,
                contentFullscreen = true,
                playback = playback().copy(playWhenReady = false),
            ),
        )
    }

    @Test
    fun previewTargetCannotEnterPictureInPicture() {
        assertFalse(
            PictureInPicturePolicy.canEnter(
                enabled = true,
                contentFullscreen = true,
                playback = playback(target = VideoTarget.PREVIEW),
            ),
        )
    }

    @Test
    fun missingMediaCannotEnterPictureInPicture() {
        assertFalse(
            PictureInPicturePolicy.canEnter(
                enabled = true,
                contentFullscreen = true,
                playback = playback().copy(mediaId = null),
            ),
        )
    }

    @Test
    fun endedAndErrorPlaybackCannotEnterPictureInPicture() {
        assertFalse(
            PictureInPicturePolicy.canEnter(
                enabled = true,
                contentFullscreen = true,
                playback = playback(phase = PlaybackPhase.ENDED),
            ),
        )
        assertFalse(
            PictureInPicturePolicy.canEnter(
                enabled = true,
                contentFullscreen = true,
                playback = playback(phase = PlaybackPhase.ERROR),
            ),
        )
    }

    @Test
    fun videoDimensionsDrivePictureInPictureAspectRatio() {
        assertEquals(
            PictureInPictureAspectRatio(width = 4, height = 3),
            PictureInPictureAspectRatioPolicy.resolve(videoWidth = 4, videoHeight = 3),
        )
    }

    @Test
    fun invalidOrPlatformExtremeAspectRatioFallsBackToSixteenByNine() {
        assertEquals(
            PictureInPictureAspectRatioPolicy.fallback,
            PictureInPictureAspectRatioPolicy.resolve(videoWidth = null, videoHeight = 1080),
        )
        assertEquals(
            PictureInPictureAspectRatioPolicy.fallback,
            PictureInPictureAspectRatioPolicy.resolve(videoWidth = 4000, videoHeight = 500),
        )
    }

    private fun playback(
        phase: PlaybackPhase = PlaybackPhase.READY,
        target: VideoTarget = VideoTarget.FULLSCREEN,
    ): PlaybackSnapshot = PlaybackSnapshot(
        mediaId = "media-1",
        phase = phase,
        playWhenReady = true,
        activeTarget = target,
    )
}
