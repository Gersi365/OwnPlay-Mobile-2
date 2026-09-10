package app.ownplay.mobile.feature.live.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePresentationReducerTest {
    @Test
    fun firstChannelTapOpensPreviewAndLoadsChannel() {
        val transition = LivePresentationReducer.reduce(
            LivePresentationState(),
            LiveIntent.ChannelTapped("channel-1"),
        )

        assertEquals(LivePresentation.PREVIEW, transition.state.presentation)
        assertEquals("channel-1", transition.state.selectedChannelId)
        assertEquals(listOf(LiveEffect.LoadChannel("channel-1")), transition.effects)
    }

    @Test
    fun differentChannelTapKeepsPreviewAndLoadsNewChannel() {
        val transition = LivePresentationReducer.reduce(
            LivePresentationState(
                presentation = LivePresentation.PREVIEW,
                selectedChannelId = "channel-1",
            ),
            LiveIntent.ChannelTapped("channel-2"),
        )

        assertEquals(LivePresentation.PREVIEW, transition.state.presentation)
        assertEquals("channel-2", transition.state.selectedChannelId)
        assertEquals(listOf(LiveEffect.LoadChannel("channel-2")), transition.effects)
    }

    @Test
    fun samePreviewedChannelTapEntersFullscreenWithoutReload() {
        val transition = LivePresentationReducer.reduce(
            LivePresentationState(
                presentation = LivePresentation.PREVIEW,
                selectedChannelId = "channel-1",
            ),
            LiveIntent.ChannelTapped("channel-1"),
        )

        assertEquals(LivePresentation.FULLSCREEN, transition.state.presentation)
        assertEquals("channel-1", transition.state.selectedChannelId)
        assertTrue(transition.effects.isEmpty())
    }

    @Test
    fun backFromFullscreenReturnsToSamePreviewWithoutReload() {
        val transition = LivePresentationReducer.reduce(
            LivePresentationState(
                presentation = LivePresentation.FULLSCREEN,
                selectedChannelId = "channel-1",
            ),
            LiveIntent.BackPressed,
        )

        assertEquals(LivePresentation.PREVIEW, transition.state.presentation)
        assertEquals("channel-1", transition.state.selectedChannelId)
        assertTrue(transition.effects.isEmpty())
    }

    @Test
    fun backFromPreviewClosesPreviewAndStopsPlayback() {
        val transition = LivePresentationReducer.reduce(
            LivePresentationState(
                presentation = LivePresentation.PREVIEW,
                selectedChannelId = "channel-1",
            ),
            LiveIntent.BackPressed,
        )

        assertEquals(LivePresentationState(), transition.state)
        assertEquals(listOf(LiveEffect.StopPlayback), transition.effects)
    }

    @Test
    fun unavailableSelectedChannelClosesPresentationAndStopsPlayback() {
        val transition = LivePresentationReducer.reduce(
            LivePresentationState(
                presentation = LivePresentation.FULLSCREEN,
                selectedChannelId = "channel-1",
            ),
            LiveIntent.ChannelUnavailable("channel-1"),
        )

        assertEquals(LivePresentationState(), transition.state)
        assertEquals(listOf(LiveEffect.StopPlayback), transition.effects)
    }

    @Test
    fun unavailableDifferentChannelDoesNotChangePresentation() {
        val state = LivePresentationState(
            presentation = LivePresentation.PREVIEW,
            selectedChannelId = "channel-1",
        )
        val transition = LivePresentationReducer.reduce(
            state,
            LiveIntent.ChannelUnavailable("channel-2"),
        )

        assertEquals(state, transition.state)
        assertTrue(transition.effects.isEmpty())
    }
}
