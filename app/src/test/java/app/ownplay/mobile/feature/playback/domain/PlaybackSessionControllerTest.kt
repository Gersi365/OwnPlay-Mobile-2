package app.ownplay.mobile.feature.playback.domain

import app.ownplay.mobile.sources.domain.SourceId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackSessionControllerTest {
    @Test
    fun differentChannelActivationReplacesTheSingleTargetAndMedia() = runBlocking {
        val resolver = FakeResolver()
        val engine = FakeEngine()
        val controller = PlaybackSessionController(resolver, FakePreparer(), engine)
        val first = PlaybackTarget(SourceId("source-a"), "channel-a")
        val second = PlaybackTarget(SourceId("source-a"), "channel-b")

        controller.activateLiveChannel(first)
        engine.emitReady()
        controller.enterFullscreen()
        controller.activateLiveChannel(second)

        assertEquals(second, controller.state.value.target)
        assertEquals(PlaybackPresentation.PREVIEW, controller.state.value.presentation)
        assertEquals(PlaybackReadiness.PREPARING, controller.state.value.readiness)
        assertEquals(listOf(first, second), resolver.resolvedTargets)
        assertEquals(2, engine.replacedMedia.size)

        engine.emitReady()
        assertEquals(PlaybackReadiness.PREPARED, controller.state.value.readiness)
    }

    @Test
    fun samePreviewedChannelPromotesToFullscreenWithoutReplacingAgain() = runBlocking {
        val engine = FakeEngine()
        val controller = PlaybackSessionController(FakeResolver(), FakePreparer(), engine)
        val target = PlaybackTarget(SourceId("source-a"), "channel-a")

        controller.activateLiveChannel(target)
        engine.emitReady()
        controller.activateLiveChannel(target)

        assertEquals(PlaybackPresentation.FULLSCREEN, controller.state.value.presentation)
        assertEquals(PlaybackReadiness.PREPARED, controller.state.value.readiness)
        assertEquals(1, engine.replacedMedia.size)
    }

    @Test
    fun engineEventsDriveReadinessAndIgnoreStaleRevision() = runBlocking {
        val engine = FakeEngine()
        val controller = PlaybackSessionController(FakeResolver(), FakePreparer(), engine)
        val first = PlaybackTarget(SourceId("source-a"), "channel-a")
        val second = PlaybackTarget(SourceId("source-a"), "channel-b")

        controller.activateLiveChannel(first)
        val firstRevision = engine.activeRevision
        controller.activateLiveChannel(second)
        val secondRevision = engine.activeRevision

        engine.emit(PlaybackEngineReadiness.READY, firstRevision)
        assertEquals(PlaybackReadiness.PREPARING, controller.state.value.readiness)

        engine.emit(PlaybackEngineReadiness.READY, secondRevision)
        assertEquals(PlaybackReadiness.PREPARED, controller.state.value.readiness)

        engine.emit(PlaybackEngineReadiness.PREPARING, secondRevision)
        assertEquals(PlaybackReadiness.PREPARING, controller.state.value.readiness)

        engine.emit(PlaybackEngineReadiness.FAILED, secondRevision)
        assertEquals(PlaybackReadiness.UNAVAILABLE, controller.state.value.readiness)
    }

    @Test
    fun pictureInPictureRequiresPreparedFullscreenAndReturnsToPreview() = runBlocking {
        val engine = FakeEngine()
        val controller = PlaybackSessionController(FakeResolver(), FakePreparer(), engine)
        val target = PlaybackTarget(SourceId("source-a"), "channel-a")

        controller.activateLiveChannel(target)
        controller.enterFullscreen()
        controller.onPictureInPictureModeChanged(true)
        assertEquals(PlaybackPresentation.FULLSCREEN, controller.state.value.presentation)

        engine.emitReady()
        controller.onPictureInPictureModeChanged(true)
        assertEquals(PlaybackPresentation.PICTURE_IN_PICTURE, controller.state.value.presentation)

        controller.onPictureInPictureModeChanged(false)
        assertEquals(target, controller.state.value.target)
        assertEquals(PlaybackPresentation.PREVIEW, controller.state.value.presentation)
        assertEquals(1, engine.replacedMedia.size)
    }

    @Test
    fun activeSourceMismatchClearsOwnedSession() = runBlocking {
        val engine = FakeEngine()
        val controller = PlaybackSessionController(FakeResolver(), FakePreparer(), engine)
        val target = PlaybackTarget(SourceId("source-a"), "channel-a")

        controller.activateLiveChannel(target)
        engine.emitReady()
        controller.reconcileActiveSource(SourceId("source-a"))
        assertEquals(target, controller.state.value.target)

        controller.reconcileActiveSource(SourceId("source-b"))
        assertNull(controller.state.value.target)
        assertEquals(PlaybackReadiness.IDLE, controller.state.value.readiness)
        assertEquals(2, engine.clearCount)
    }

    @Test
    fun revalidationClearsTargetThatNoLongerResolves() = runBlocking {
        val resolver = FakeResolver()
        val engine = FakeEngine()
        val controller = PlaybackSessionController(resolver, FakePreparer(), engine)
        val target = PlaybackTarget(SourceId("source-a"), "channel-a")

        controller.activateLiveChannel(target)
        engine.emitReady()
        resolver.unavailableChannelId = "channel-a"
        controller.revalidateActiveTarget()

        assertNull(controller.state.value.target)
        assertEquals(PlaybackReadiness.IDLE, controller.state.value.readiness)
        assertEquals(2, engine.clearCount)
    }

    @Test
    fun unavailablePreparationDoesNotLeavePriorMediaOwnedByEngine() = runBlocking {
        val engine = FakeEngine()
        val controller = PlaybackSessionController(
            FakeResolver(),
            FakePreparer(unavailableChannelId = "channel-b"),
            engine,
        )

        controller.activateLiveChannel(PlaybackTarget(SourceId("source-a"), "channel-a"))
        engine.emitReady()
        controller.activateLiveChannel(PlaybackTarget(SourceId("source-a"), "channel-b"))

        assertEquals(PlaybackReadiness.UNAVAILABLE, controller.state.value.readiness)
        assertEquals(1, engine.replacedMedia.size)
        assertEquals(2, engine.clearCount)
    }

    @Test
    fun engineReplaceFailureMapsToUnavailableAndClearsEngine() = runBlocking {
        val engine = FakeEngine(failOnReplace = true)
        val controller = PlaybackSessionController(FakeResolver(), FakePreparer(), engine)

        controller.activateLiveChannel(PlaybackTarget(SourceId("source-a"), "channel-a"))

        assertEquals(PlaybackReadiness.UNAVAILABLE, controller.state.value.readiness)
        assertEquals(2, engine.clearCount)
    }

    @Test
    fun releaseResetsSessionAndReleasesEngine() = runBlocking {
        val engine = FakeEngine()
        val controller = PlaybackSessionController(FakeResolver(), FakePreparer(), engine)

        controller.activateLiveChannel(PlaybackTarget(SourceId("source-a"), "channel-a"))
        controller.release()

        assertNull(controller.state.value.target)
        assertEquals(1, engine.releaseCount)
    }

    private class FakeResolver : LivePlaybackSourceResolver {
        val resolvedTargets = mutableListOf<PlaybackTarget>()
        var unavailableChannelId: String? = null

        override suspend fun resolve(target: PlaybackTarget): LivePlaybackSource? {
            resolvedTargets += target
            if (target.channelId == unavailableChannelId) return null
            return LivePlaybackSource.Direct("https://provider.example/live/${target.channelId}")
        }
    }

    private class FakePreparer(
        private val unavailableChannelId: String? = null,
    ) : LivePlaybackMediaPreparer {
        override fun prepare(source: LivePlaybackSource): PreparedPlaybackMedia? {
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
        var releaseCount: Int = 0
        var activeRevision: Long = 0L
            private set
        private var nextRevision: Long = 0L
        private var listener: ((PlaybackEngineEvent) -> Unit)? = null

        override fun setEventListener(listener: (PlaybackEngineEvent) -> Unit) {
            this.listener = listener
        }

        override fun replace(media: PreparedPlaybackMedia): Long {
            if (failOnReplace) error("engine failure")
            nextRevision += 1L
            activeRevision = nextRevision
            replacedMedia += media
            return activeRevision
        }

        override fun clear() {
            clearCount += 1
        }

        override fun release() {
            releaseCount += 1
        }

        fun emitReady() {
            emit(PlaybackEngineReadiness.READY, activeRevision)
        }

        fun emit(readiness: PlaybackEngineReadiness, revision: Long) {
            listener?.invoke(PlaybackEngineEvent(revision, readiness))
        }
    }
}
