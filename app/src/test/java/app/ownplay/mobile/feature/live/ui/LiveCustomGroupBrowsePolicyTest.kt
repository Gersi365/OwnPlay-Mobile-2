package app.ownplay.mobile.feature.live.ui

import app.ownplay.mobile.feature.live.domain.LiveChannel
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveCustomGroupBrowsePolicyTest {
    @Test
    fun `custom group filter preserves source channel order`() {
        val channels = listOf(
            channel("1"),
            channel("2"),
            channel("3"),
            channel("4"),
        )
        val filtered = LiveBrowsePolicy.customGroupChannels(
            channels = channels,
            channelIds = listOf("3", "2"),
        )
        assertEquals(listOf("2", "3"), filtered.map { it.channelId })
    }

    private fun channel(id: String) = LiveChannel(
        channelId = id,
        sourceId = "source",
        categoryKey = "category",
        name = "Channel $id",
        logoUrl = null,
        sortOrder = id.toInt(),
    )
}
