package app.ownplay.mobile.feature.playback.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PlaybackSessionController internal constructor(
    private val sourceResolver: LivePlaybackSourceResolver,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(PlaybackSessionState())
    private var preparedSource: LivePlaybackSource? = null

    val state: StateFlow<PlaybackSessionState> = mutableState.asStateFlow()

    suspend fun activateLiveChannel(target: PlaybackTarget) {
        mutex.withLock {
            val current = mutableState.value
            val targetChanged = current.target != target
            val next = PlaybackSessionPolicy.activateLiveChannel(current, target)
            mutableState.value = next

            if (!targetChanged) return

            preparedSource = null
            val resolved = try {
                sourceResolver.resolve(target)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }

            if (mutableState.value.target != target) return

            preparedSource = resolved
            mutableState.value = mutableState.value.copy(
                readiness = if (resolved == null) {
                    PlaybackReadiness.UNAVAILABLE
                } else {
                    PlaybackReadiness.PREPARED
                },
            )
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
        preparedSource = null
        mutableState.value = PlaybackSessionState()
    }
}
