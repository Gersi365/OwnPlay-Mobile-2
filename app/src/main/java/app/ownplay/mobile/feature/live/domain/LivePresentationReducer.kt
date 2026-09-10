package app.ownplay.mobile.feature.live.domain

enum class LivePresentation {
    BROWSE,
    PREVIEW,
    FULLSCREEN,
}

data class LivePresentationState(
    val presentation: LivePresentation = LivePresentation.BROWSE,
    val selectedChannelId: String? = null,
)

sealed interface LiveIntent {
    data class ChannelTapped(val channelId: String) : LiveIntent
    data object BackPressed : LiveIntent
    data class ChannelUnavailable(val channelId: String) : LiveIntent
}

sealed interface LiveEffect {
    data class LoadChannel(val channelId: String) : LiveEffect
    data object StopPlayback : LiveEffect
}

data class LiveTransition(
    val state: LivePresentationState,
    val effects: List<LiveEffect> = emptyList(),
)

object LivePresentationReducer {
    fun reduce(
        state: LivePresentationState,
        intent: LiveIntent,
    ): LiveTransition = when (intent) {
        is LiveIntent.ChannelTapped -> onChannelTapped(state, intent.channelId)
        LiveIntent.BackPressed -> onBackPressed(state)
        is LiveIntent.ChannelUnavailable -> onChannelUnavailable(state, intent.channelId)
    }

    private fun onChannelTapped(
        state: LivePresentationState,
        channelId: String,
    ): LiveTransition {
        require(channelId.isNotBlank()) { "Channel id must not be blank." }

        return if (
            state.presentation == LivePresentation.PREVIEW &&
            state.selectedChannelId == channelId
        ) {
            LiveTransition(
                state = state.copy(presentation = LivePresentation.FULLSCREEN),
            )
        } else {
            LiveTransition(
                state = LivePresentationState(
                    presentation = LivePresentation.PREVIEW,
                    selectedChannelId = channelId,
                ),
                effects = listOf(LiveEffect.LoadChannel(channelId)),
            )
        }
    }

    private fun onBackPressed(state: LivePresentationState): LiveTransition = when (state.presentation) {
        LivePresentation.FULLSCREEN -> LiveTransition(
            state = state.copy(presentation = LivePresentation.PREVIEW),
        )

        LivePresentation.PREVIEW -> LiveTransition(
            state = LivePresentationState(),
            effects = listOf(LiveEffect.StopPlayback),
        )

        LivePresentation.BROWSE -> LiveTransition(state = state)
    }

    private fun onChannelUnavailable(
        state: LivePresentationState,
        channelId: String,
    ): LiveTransition = if (state.selectedChannelId == channelId) {
        LiveTransition(
            state = LivePresentationState(),
            effects = listOf(LiveEffect.StopPlayback),
        )
    } else {
        LiveTransition(state = state)
    }
}
