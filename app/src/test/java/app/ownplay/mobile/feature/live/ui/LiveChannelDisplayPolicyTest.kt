package app.ownplay.mobile.feature.live.ui

import app.ownplay.mobile.feature.live.domain.LiveOrganizationChannel
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveChannelDisplayPolicyTest {
    private val channel = LiveOrganizationChannel(
        channelId = "channel-1",
        name = "Provider News",
        tvgName = "Guide News",
        providerCategoryId = "news",
        providerOrder = 0,
    )

    @Test
    fun providerNameRemainsDefault() {
        assertEquals("Provider News", LiveChannelDisplayPolicy.displayName(channel, preferTvgName = false))
    }

    @Test
    fun tvgNameIsUsedOnlyWhenPreferredAndUsable() {
        assertEquals("Guide News", LiveChannelDisplayPolicy.displayName(channel, preferTvgName = true))
        assertEquals(
            "Provider News",
            LiveChannelDisplayPolicy.displayName(channel.copy(tvgName = "  "), preferTvgName = true),
        )
    }
    @Test
    fun prefixCleanupIsOptInAndKeepsMalformedNames() {
        val prefixed = channel.copy(name = "AL |   Top Channel", tvgName = null)
        assertEquals(
            "AL |   Top Channel",
            LiveChannelDisplayPolicy.displayName(prefixed, preferTvgName = false, hideChannelPrefix = false),
        )
        assertEquals(
            "Top Channel",
            LiveChannelDisplayPolicy.displayName(prefixed, preferTvgName = false, hideChannelPrefix = true),
        )
        assertEquals(
            "| Channel",
            LiveChannelDisplayPolicy.displayName(
                prefixed.copy(name = "| Channel"),
                preferTvgName = false,
                hideChannelPrefix = true,
            ),
        )
    }

}
