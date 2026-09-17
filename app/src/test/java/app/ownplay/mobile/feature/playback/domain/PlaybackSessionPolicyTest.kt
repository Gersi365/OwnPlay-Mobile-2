package app.ownplay.mobile.feature.playback.domain

import app.ownplay.mobile.sources.domain.SourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackSessionPolicyTest {
    private val sourceId = SourceId("source")

    @Test
    fun differentChannelActivationOwnsOneTargetAndOpensPreview() {
        val first = PlaybackSessionPolicy.activateLiveChannel(
            state = PlaybackSessionState(),
            target = PlaybackTarget(sourceId, "channel-a"),
        )
        val second = PlaybackSessionPolicy.activateLiveChannel(
            state = first.copy(presentation = PlaybackPresentation.FULLSCREEN),
            target = PlaybackTarget(sourceId, "channel-b"),
        )

        assertEquals("channel-b", second.target?.channelId)
        assertEquals(PlaybackPresentation.PREVIEW, second.presentation)
        assertEquals(2L, second.targetRevision)
    }

    @Test
    fun activatingSamePreviewedChannelPromotesSameTargetToFullscreen() {
        val preview = PlaybackSessionPolicy.activateLiveChannel(
            state = PlaybackSessionState(),
            target = PlaybackTarget(sourceId, "channel-a"),
        )
        val fullscreen = PlaybackSessionPolicy.activateLiveChannel(
            state = preview,
            target = PlaybackTarget(sourceId, "channel-a"),
        )

        assertEquals(preview.target, fullscreen.target)
        assertEquals(preview.targetRevision, fullscreen.targetRevision)
        assertEquals(PlaybackPresentation.FULLSCREEN, fullscreen.presentation)
    }

    @Test
    fun previewFullscreenAndPipKeepSessionTargetContinuity() {
        val preview = PlaybackSessionPolicy.activateLiveChannel(
            state = PlaybackSessionState(),
            target = PlaybackTarget(sourceId, "channel-a"),
        )
        val fullscreen = PlaybackSessionPolicy.activateLiveChannel(
            state = preview,
            target = preview.target!!,
        )
        val pip = PlaybackSessionPolicy.enterPictureInPicture(fullscreen)
        val returned = PlaybackSessionPolicy.returnToPreview(pip)

        assertEquals(preview.target, pip.target)
        assertEquals(preview.targetRevision, pip.targetRevision)
        assertEquals(PlaybackPresentation.PICTURE_IN_PICTURE, pip.presentation)
        assertEquals(preview.target, returned.target)
        assertEquals(preview.targetRevision, returned.targetRevision)
        assertEquals(PlaybackPresentation.PREVIEW, returned.presentation)
    }

    @Test
    fun retainingAnotherSourceClearsOnlyActiveOwnership() {
        val preview = PlaybackSessionPolicy.activateLiveChannel(
            state = PlaybackSessionState(),
            target = PlaybackTarget(sourceId, "channel-a"),
        )
        val cleared = PlaybackSessionPolicy.retainSource(preview, SourceId("other-source"))

        assertNull(cleared.target)
        assertEquals(PlaybackPresentation.NONE, cleared.presentation)
        assertEquals(preview.targetRevision, cleared.targetRevision)
    }
}
