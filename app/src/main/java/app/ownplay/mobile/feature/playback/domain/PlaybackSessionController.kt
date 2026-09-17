package app.ownplay.mobile.feature.playback.domain

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

    val state: StateFlow<PlaybackSessionState> = mutableState.asStateFlow()

    suspend fun activateLiveChannel(target: PlaybackTarget) {
        mutex.withLock {
            val current = mutableState.value
            val targetChanged = current.target != target
            val next = PlaybackSessionPolicy.activateLiveChannel(current, target)
            mutableState.value = next

            if (!targetChanged) return

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
                playbackEngine.replace(media)
                mutableState.value = mutableState.value.copy(readiness = PlaybackReadiness.PREPARED)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
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
        mutableState.value = PlaybackSessionPolicy.present(
            current = mutableState.value,
            presentation = PlaybackPresentation.PICTURE_IN_PICTURE,
        )
    }

    fun returnToPreview() {
        mutableState.value = PlaybackSessionPolicy.present(
            current = mutableState.value,
            presentation = PlaybackPresentation.PREVIEW,
        )
    }

    fun clear() {
        playbackEngine.clear()
        mutableState.value = PlaybackSessionState()
    }
}
