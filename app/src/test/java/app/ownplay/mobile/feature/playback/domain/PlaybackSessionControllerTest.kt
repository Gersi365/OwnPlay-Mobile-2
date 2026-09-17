package app.ownplay.mobile.feature.playback.domain

import app.ownplay.mobile.sources.domain.SourceId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackSessionControllerTest {
    @Test
    fun differentChannelActivationReplacesTheSingleTargetAndReturnsToPreview() = runBlocking {
        val resolver = FakeResolver()
        val controller = PlaybackSessionController(resolver)
        val first = PlaybackTarget(SourceId("source-a"), "channel-a")
        val second = PlaybackTarget(SourceId("source-a"), "channel-b")

        controller.activateLiveChannel(first)
        controller.enterFullscreen()
        controller.activateLiveChannel(second)

        assertEquals(second, controller.state.value.target)
        assertEquals(PlaybackPresentation.PREVIEW, controller.state.value.presentation)
        assertEquals(PlaybackReadiness.PREPARED, controller.state.value.readiness)
        assertEquals(listOf(first, second), resolver.resolvedTargets)
    }

    @Test
    fun samePreviewedChannelPromotesToFullscreenWithoutResolvingAgain() = runBlocking {
        val resolver = FakeResolver()
        val controller = PlaybackSessionController(resolver)
        val target = PlaybackTarget(SourceId("source-a"), "channel-a")

        controller.activateLiveChannel(target)
        controller.activateLiveChannel(target)

        assertEquals(target, controller.state.value.target)
        assertEquals(PlaybackPresentation.FULLSCREEN, controller.state.value.presentation)
        assertEquals(PlaybackReadiness.PREPARED, controller.state.value.readiness)
        assertEquals(listOf(target), resolver.resolvedTargets)
    }

    @Test
    fun presentationChangesKeepTheSamePreparedSessionTarget() = runBlocking {
        val resolver = FakeResolver()
        val controller = PlaybackSessionController(resolver)
        val target = PlaybackTarget(SourceId("source-a"), "channel-a")

        controller.activateLiveChannel(target)
        controller.enterPictureInPicture()
        assertEquals(PlaybackPresentation.PICTURE_IN_PICTURE, controller.state.value.presentation)

        controller.returnToPreview()
        assertEquals(target, controller.state.value.target)
        assertEquals(PlaybackPresentation.PREVIEW, controller.state.value.presentation)
        assertEquals(PlaybackReadiness.PREPARED, controller.state.value.readiness)
        assertEquals(listOf(target), resolver.resolvedTargets)
    }

    @Test
    fun sameChannelIdFromAnotherSourceIsA differentTarget() = runBlocking {
        val resolver = FakeResolver()
        val controller = PlaybackSessionController(resolver)
        val first = PlaybackTarget(SourceId("source-a"), "channel-a")
        val second = PlaybackTarget(SourceId("source-b"), "channel-a")

        controller.activateLiveChannel(first)
        controller.activateLiveChannel(second)

        assertEquals(second, controller.state.value.target)
        assertEquals(PlaybackPresentation.PREVIEW, controller.state.value.presentation)
        assertEquals(listOf(first, second), resolver.resolvedTargets)
    }

    @Test
    fun clearReleasesTargetFromPublicSessionState() = runBlocking {
        val controller = PlaybackSessionController(FakeResolver())
        controller.activateLiveChannel(PlaybackTarget(SourceId("source-a"), "channel-a"))

        controller.clear()

        assertNull(controller.state.value.target)
        assertEquals(PlaybackPresentation.NONE, controller.state.value.presentation)
        assertEquals(PlaybackReadiness.IDLE, controller.state.value.readiness)
    }

    @Test
    fun secretBearingPlaybackSourcesStayRedactedFromDiagnostics() {
        val direct = LivePlaybackSource.Direct("https://provider.example/live?token=top-secret")
        val xtream = LivePlaybackSource.Xtream(
            baseUrl = "https://provider.example",
            username = "private-user",
            password = "private-password",
            streamId = "123",
            opaqueStreamIdentity = "xtream://live/123",
        )

        assertFalse(direct.toString().contains("top-secret"))
        assertFalse(xtream.toString().contains("private-user"))
        assertFalse(xtream.toString().contains("private-password"))
        assertFalse(xtream.toString().contains("123"))
    }

    private class FakeResolver : LivePlaybackSourceResolver {
        val resolvedTargets = mutableListOf<PlaybackTarget>()

        override suspend fun resolve(target: PlaybackTarget): LivePlaybackSource {
            resolvedTargets += target
            return LivePlaybackSource.Direct(
                "https://provider.example/live/${target.channelId}?token=top-secret",
            )
        }
    }
}
