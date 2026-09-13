package app.ownplay.mobile.feature.live.ui

import app.ownplay.mobile.feature.live.domain.LiveCategory
import app.ownplay.mobile.feature.live.domain.LiveChannel
import app.ownplay.mobile.sources.domain.ProviderCategoryVisibility

internal object LiveBrowsePolicy {
    fun visibleCategories(categories: List<LiveCategory>): List<LiveCategory> =
        categories.filterNot { category -> ProviderCategoryVisibility.isUtilityLabel(category.name) }

    fun favoriteChannels(channels: List<LiveChannel>): List<LiveChannel> =
        channels.filter { channel -> channel.favorite }

    fun customGroupChannels(
        channels: List<LiveChannel>,
        channelIds: List<String>,
    ): List<LiveChannel> {
        if (channelIds.isEmpty()) return emptyList()
        val membership = channelIds.toHashSet()
        return channels.filter { channel -> channel.channelId in membership }
    }

    fun activeCategoryKey(
        categories: List<LiveCategory>,
        requestedCategoryKey: String?,
    ): String? = requestedCategoryKey
        ?.takeIf { key -> categories.any { category -> category.categoryKey == key } }
        ?: categories.firstOrNull()?.categoryKey
}
