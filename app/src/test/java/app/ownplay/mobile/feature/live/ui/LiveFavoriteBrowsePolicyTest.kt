package app.ownplay.mobile.feature.live.ui

import app.ownplay.mobile.feature.live.domain.LiveChannel
import kotlin.test.Test
import kotlin.test.assertEquals

class LiveFavoriteBrowsePolicyTest {
    @Test
    fun `favorite filter preserves source channel order`() {
        val channels = listOf(
            channel("1", favorite = false),
            channel("2", favorite = true),
            channel("3", favorite = true),
            channel("4", favorite = false),
        )
        assertEquals(listOf("2", "3"), LiveBrowsePolicy.favoriteChannels(channels).map { it.channelId })
    }

    private fun channel(id: String, favorite: Boolean) = LiveChannel(
        channelId = id,
        sourceId = "source",
        categoryKey = "category",
        name = "Channel $id",
        logoUrl = null,
        sortOrder = id.toInt(),
        favorite = favorite,
    )
}
