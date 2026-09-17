package app.ownplay.mobile.feature.playback.domain

import app.ownplay.mobile.sources.domain.SourceId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PlaybackSessionController internal constructor(
    private val sourceResolver: LivePlaybackSourceResolver,
    private val mediaPreparer: LivePlaybackMediaPreparer,
    private val playbackEngine: PlaybackEngine,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(PlaybackSessionState())

    @Volatile
    private var activeMediaRevision: Long? = null

    val state: StateFlow<PlaybackSessionState> = mutableState.asStateFlow()

    init {
        playbackEngine.setEventListener(::onPlaybackEngineEvent)
    }

    suspend fun activateLiveChannel(target: PlaybackTarget) {
        mutex.withLock {
            val current = mutableState.value
            val targetChanged = current.target != target
            val next = PlaybackSessionPolicy.activateLiveChannel(current, target)
            mutableState.value = next

            if (!targetChanged) return

            activeMediaRevision = null
            playbackEngine.clear()
            val media = try {
                sourceResolver.resolve(target)?.let(mediaPreparer::prepare)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }

            if (mutableState.value.target != target) return

            if (media == null) {
                mutableState.value = mutableState.value.copy(readiness = PlaybackReadiness.UNAVAILABLE)
                return
            }

            try {
                activeMediaRevision = playbackEngine.replace(media)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                activeMediaRevision = null
                playbackEngine.clear()
                mutableState.value = mutableState.value.copy(readiness = PlaybackReadiness.UNAVAILABLE)
            }
        }
    }

    fun enterFullscreen() {
        mutableState.value = PlaybackSessionPolicy.present(
            current = mutableState.value,
            presentation = PlaybackPresentation.FULLSCREEN,
        )
    }

    fun enterPictureInPicture() {
        val current = mutableState.value
        if (!PlaybackPictureInPicturePolicy.isEligible(current)) return
        mutableState.value = PlaybackSessionPolicy.present(
            current = current,
            presentation = PlaybackPresentation.PICTURE_IN_PICTURE,
        )
    }

    fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        if (isInPictureInPictureMode) {
            enterPictureInPicture()
        } else if (mutableState.value.presentation == PlaybackPresentation.PICTURE_IN_PICTURE) {
            returnToPreview()
        }
    }

    fun returnToPreview() {
        mutableState.value = PlaybackSessionPolicy.present(
            current = mutableState.value,
            presentation = PlaybackPresentation.PREVIEW,
        )
    }

    fun reconcileActiveSource(activeSourceId: SourceId?) {
        val target = mutableState.value.target ?: return
        if (activeSourceId != target.sourceId) {
            clear()
        }
    }

    suspend fun revalidateActiveTarget() {
        val target = mutableState.value.target ?: return
        val stillResolvable = try {
            sourceResolver.resolve(target) != null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }

        if (mutableState.value.target == target && !stillResolvable) {
            clear()
        }
    }

    fun clear() {
        activeMediaRevision = null
        playbackEngine.clear()
        mutableState.value = PlaybackSessionState()
    }

    internal fun release() {
        activeMediaRevision = null
        playbackEngine.release()
        mutableState.value = PlaybackSessionState()
    }

    private fun onPlaybackEngineEvent(event: PlaybackEngineEvent) {
        if (event.mediaRevision != activeMediaRevision) return
        val current = mutableState.value
        if (current.target == null) return

        mutableState.value = when (event.readiness) {
            PlaybackEngineReadiness.PREPARING ->
                current.copy(readiness = PlaybackReadiness.PREPARING)
            PlaybackEngineReadiness.READY ->
                current.copy(readiness = PlaybackReadiness.PREPARED)
            PlaybackEngineReadiness.FAILED ->
                current.copy(readiness = PlaybackReadiness.UNAVAILABLE)
        }
    }
}
