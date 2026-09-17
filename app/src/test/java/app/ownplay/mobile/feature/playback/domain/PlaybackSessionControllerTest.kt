package app.ownplay.mobile.feature.playback.domain

import app.ownplay.mobile.sources.domain.SourceId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackSessionControllerTest {
    @Test
    fun differentChannelActivationReplacesTheSingleTargetAndMedia() = runBlocking {
        val resolver = FakeResolver()
        val preparer = FakePreparer()
        val engine = FakeEngine()
        val controller = PlaybackSessionController(resolver, preparer, engine)
        val first = PlaybackTarget(SourceId("source-a"), "channel-a")
        val second = PlaybackTarget(SourceId("source-a"), "channel-b")

        controller.activateLiveChannel(first)
        controller.enterFullscreen()
        controller.activateLiveChannel(second)

        assertEquals(second, controller.state.value.target)
        assertEquals(PlaybackPresentation.PREVIEW, controller.state.value.presentation)
        assertEquals(PlaybackReadiness.PREPARED, controller.state.value.readiness)
        assertEquals(listOf(first, second), resolver.resolvedTargets)
        assertEquals(2, preparer.preparedSources.size)
        assertEquals(2, engine.replacedMedia.size)
    }

    @Test
    fun samePreviewedChannelPromotesToFullscreenWithoutResolvingOrReplacingAgain() = runBlocking {
        val resolver = FakeResolver()
        val preparer = FakePreparer()
        val engine = FakeEngine()
        val controller = PlaybackSessionController(resolver, preparer, engine)
        val target = PlaybackTarget(SourceId("source-a"), "channel-a")

        controller.activateLiveChannel(target)
        controller.activateLiveChannel(target)

        assertEquals(target, controller.state.value.target)
        assertEquals(PlaybackPresentation.FULLSCREEN, controller.state.value.presentation)
        assertEquals(PlaybackReadiness.PREPARED, controller.state.value.readiness)
        assertEquals(listOf(target), resolver.resolvedTargets)
        assertEquals(1, preparer.preparedSources.size)
        assertEquals(1, engine.replacedMedia.size)
    }

    @Test
    fun presentationChangesKeepTheSamePreparedMediaSession() = runBlocking {
        val resolver = FakeResolver()
        val preparer = FakePreparer()
        val engine = FakeEngine()
        val controller = PlaybackSessionController(resolver, preparer, engine)
        val target = PlaybackTarget(SourceId("source-a"), "channel-a")

        controller.activateLiveChannel(target)
        controller.enterPictureInPicture()
        assertEquals(PlaybackPresentation.PICTURE_IN_PICTURE, controller.state.value.presentation)

        controller.returnToPreview()
        assertEquals(target, controller.state.value.target)
        assertEquals(PlaybackPresentation.PREVIEW, controller.state.value.presentation)
        assertEquals(PlaybackReadiness.PREPARED, controller.state.value.readiness)
        assertEquals(1, engine.replacedMedia.size)
    }

    @Test
    fun sameChannelIdFromAnotherSourceIsADifferentTarget() = runBlocking {
        val resolver = FakeResolver()
        val preparer = FakePreparer()
        val engine = FakeEngine()
        val controller = PlaybackSessionController(resolver, preparer, engine)
        val first = PlaybackTarget(SourceId("source-a"), "channel-a")
        val second = PlaybackTarget(SourceId("source-b"), "channel-a")

        controller.activateLiveChannel(first)
        controller.activateLiveChannel(second)

        assertEquals(second, controller.state.value.target)
        assertEquals(PlaybackPresentation.PREVIEW, controller.state.value.presentation)
        assertEquals(listOf(first, second), resolver.resolvedTargets)
        assertEquals(2, engine.replacedMedia.size)
    }

    @Test
    fun unavailablePreparationDoesNotLeavePriorMediaOwnedByEngine() = runBlocking {
        val resolver = FakeResolver()
        val preparer = FakePreparer(unavailableChannelId = "channel-b")
        val engine = FakeEngine()
        val controller = PlaybackSessionController(resolver, preparer, engine)

        controller.activateLiveChannel(PlaybackTarget(SourceId("source-a"), "channel-a"))
        controller.activateLiveChannel(PlaybackTarget(SourceId("source-a"), "channel-b"))

        assertEquals(PlaybackReadiness.UNAVAILABLE, controller.state.value.readiness)
        assertEquals(1, engine.replacedMedia.size)
        assertEquals(2, engine.clearCount)
    }

    @Test
    fun engineFailureMapsToUnavailableAndClearsEngine() = runBlocking {
        val engine = FakeEngine(failOnReplace = true)
        val controller = PlaybackSessionController(FakeResolver(), FakePreparer(), engine)

        controller.activateLiveChannel(PlaybackTarget(SourceId("source-a"), "channel-a"))

        assertEquals(PlaybackReadiness.UNAVAILABLE, controller.state.value.readiness)
        assertEquals(2, engine.clearCount)
    }

    @Test
    fun clearReleasesTargetAndEngineOwnership() = runBlocking {
        val engine = FakeEngine()
        val controller = PlaybackSessionController(FakeResolver(), FakePreparer(), engine)
        controller.activateLiveChannel(PlaybackTarget(SourceId("source-a"), "channel-a"))

        controller.clear()

        assertNull(controller.state.value.target)
        assertEquals(PlaybackPresentation.NONE, controller.state.value.presentation)
        assertEquals(PlaybackReadiness.IDLE, controller.state.value.readiness)
        assertEquals(2, engine.clearCount)
    }

    @Test
    fun secretBearingPlaybackSourcesAndPreparedMediaStayRedactedFromDiagnostics() {
        val direct = LivePlaybackSource.Direct("https://provider.example/live?token=top-secret")
        val xtream = LivePlaybackSource.Xtream(
            baseUrl = "https://provider.example",
            username = "private-user",
            password = "private-password",
            streamId = "123",
            opaqueStreamIdentity = "xtream://live/123?ext=ts",
        )
        val media = PreparedPlaybackMedia("https://provider.example/live?token=top-secret")

        assertFalse(direct.toString().contains("top-secret"))
        assertFalse(xtream.toString().contains("private-user"))
        assertFalse(xtream.toString().contains("private-password"))
        assertFalse(xtream.toString().contains("123"))
        assertFalse(media.toString().contains("top-secret"))
        assertFalse(media.toString().contains("provider.example"))
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

    private class FakePreparer(
        private val unavailableChannelId: String? = null,
    ) : LivePlaybackMediaPreparer {
        val preparedSources = mutableListOf<LivePlaybackSource>()

        override fun prepare(source: LivePlaybackSource): PreparedPlaybackMedia? {
            preparedSources += source
            val direct = source as LivePlaybackSource.Direct
            if (unavailableChannelId != null && direct.streamLocator.contains(unavailableChannelId)) {
                return null
            }
            return PreparedPlaybackMedia(direct.streamLocator)
        }
    }

    private class FakeEngine(
        private val failOnReplace: Boolean = false,
    ) : PlaybackEngine {
        val replacedMedia = mutableListOf<PreparedPlaybackMedia>()
        var clearCount: Int = 0

        override fun replace(media: PreparedPlaybackMedia) {
            if (failOnReplace) error("engine failure")
            replacedMedia += media
        }

        override fun clear() {
            clearCount += 1
        }
    }
}
