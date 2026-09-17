package app.ownplay.mobile.feature.playback.domain

import app.ownplay.mobile.sources.domain.SourceId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class PlaybackPresentation {
    NONE,
    PREVIEW,
    FULLSCREEN,
    PICTURE_IN_PICTURE,
}

data class PlaybackTarget(
    val sourceId: SourceId,
    val channelId: String,
) {
    init {
        require(channelId.isNotBlank()) { "Playback channel id must not be blank" }
    }
}

data class PlaybackSessionState(
    val target: PlaybackTarget? = null,
    val presentation: PlaybackPresentation = PlaybackPresentation.NONE,
    val targetRevision: Long = 0L,
) {
    init {
        require((target == null) == (presentation == PlaybackPresentation.NONE)) {
            "Playback presentation must match active-target ownership"
        }
    }
}

object PlaybackSessionPolicy {
    fun activateLiveChannel(
        state: PlaybackSessionState,
        target: PlaybackTarget,
    ): PlaybackSessionState {
        if (state.target != target) {
            return PlaybackSessionState(
                target = target,
                presentation = PlaybackPresentation.PREVIEW,
                targetRevision = state.targetRevision + 1L,
            )
        }

        return if (state.presentation == PlaybackPresentation.PREVIEW) {
            state.copy(presentation = PlaybackPresentation.FULLSCREEN)
        } else {
            state
        }
    }

    fun enterPictureInPicture(state: PlaybackSessionState): PlaybackSessionState =
        state.withActiveTarget { current ->
            current.copy(presentation = PlaybackPresentation.PICTURE_IN_PICTURE)
        }

    fun returnToPreview(state: PlaybackSessionState): PlaybackSessionState =
        state.withActiveTarget { current ->
            current.copy(presentation = PlaybackPresentation.PREVIEW)
        }

    fun retainSource(
        state: PlaybackSessionState,
        sourceId: SourceId,
    ): PlaybackSessionState =
        if (state.target == null || state.target.sourceId == sourceId) {
            state
        } else {
            clear(state)
        }

    fun clear(state: PlaybackSessionState): PlaybackSessionState =
        PlaybackSessionState(targetRevision = state.targetRevision)

    private inline fun PlaybackSessionState.withActiveTarget(
        transform: (PlaybackSessionState) -> PlaybackSessionState,
    ): PlaybackSessionState = if (target == null) this else transform(this)
}

interface PlaybackSessionController {
    val state: StateFlow<PlaybackSessionState>

    fun activateLiveChannel(sourceId: SourceId, channelId: String)

    fun enterPictureInPicture()

    fun returnToPreview()

    fun retainSource(sourceId: SourceId)

    fun clear()
}

class DefaultPlaybackSessionController : PlaybackSessionController {
    private val mutableState = MutableStateFlow(PlaybackSessionState())

    override val state: StateFlow<PlaybackSessionState> = mutableState.asStateFlow()

    override fun activateLiveChannel(sourceId: SourceId, channelId: String) {
        val target = PlaybackTarget(sourceId = sourceId, channelId = channelId)
        mutableState.update { current ->
            PlaybackSessionPolicy.activateLiveChannel(current, target)
        }
    }

    override fun enterPictureInPicture() {
        mutableState.update(PlaybackSessionPolicy::enterPictureInPicture)
    }

    override fun returnToPreview() {
        mutableState.update(PlaybackSessionPolicy::returnToPreview)
    }

    override fun retainSource(sourceId: SourceId) {
        mutableState.update { current -> PlaybackSessionPolicy.retainSource(current, sourceId) }
    }

    override fun clear() {
        mutableState.update(PlaybackSessionPolicy::clear)
    }
}
