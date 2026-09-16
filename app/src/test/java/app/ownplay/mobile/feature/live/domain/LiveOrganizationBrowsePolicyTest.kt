package app.ownplay.mobile.feature.live.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveOrganizationBrowsePolicyTest {
    @Test
    fun providerModeKeepsEmptyCategoriesAndSeparatesUncategorizedChannels() {
        val sports = providerCategory("sports", "Sports")
        val news = providerCategory("news", "News")
        val snapshot = LiveOrganizationSnapshot(
            sourceId = SOURCE_ID,
            activeMode = LiveOrganizationMode.PROVIDER,
            categories = listOf(sports, news),
            memberships = listOf(
                providerMembership("sports", "channel-a"),
                providerMembership(
                    LiveOrganizationScopePolicy.PROVIDER_UNCATEGORIZED_CATEGORY_ID,
                    "channel-b",
                ),
            ),
        )

        val result = LiveOrganizationBrowsePolicy.resolve(snapshot)

        assertEquals(listOf("sports", "news"), result.categories.map { it.category.categoryId })
        assertEquals(listOf("channel-a"), result.categories.first().channelIds)
        assertTrue(result.categories[1].channelIds.isEmpty())
        assertEquals(listOf("channel-b"), result.uncategorizedChannelIds)
        assertEquals(listOf("channel-a", "channel-b"), result.allChannelIds)
    }

    @Test
    fun ownPlayModeSupportsMultipleMembershipsWithoutDuplicatingGlobalChannelIds() {
        val snapshot = LiveOrganizationSnapshot(
            sourceId = SOURCE_ID,
            activeMode = LiveOrganizationMode.OWNPLAY,
            categories = listOf(
                ownPlayCategory("sport", "Sport"),
                ownPlayCategory("football", "Football", parentCategoryId = "sport"),
                ownPlayCategory("serie-a", "Serie A", parentCategoryId = "football"),
            ),
            memberships = listOf(
                ownPlayMembership("football", "channel-a"),
                ownPlayMembership("serie-a", "channel-a"),
                ownPlayMembership("serie-a", "channel-b"),
            ),
        )

        val result = LiveOrganizationBrowsePolicy.resolve(snapshot)

        val sport = result.categories.single()
        val football = sport.children.single()
        val serieA = football.children.single()
        assertEquals(listOf("channel-a"), football.channelIds)
        assertEquals(listOf("channel-a", "channel-b"), serieA.channelIds)
        assertEquals(listOf("channel-a", "channel-b"), result.allChannelIds)
    }

    @Test
    fun ownPlayModePrunesEmptyBranchesAndHiddenSubtrees() {
        val snapshot = LiveOrganizationSnapshot(
            sourceId = SOURCE_ID,
            activeMode = LiveOrganizationMode.OWNPLAY,
            categories = listOf(
                ownPlayCategory("empty", "Empty"),
                ownPlayCategory("visible", "Visible"),
                ownPlayCategory("hidden-parent", "Hidden parent", hidden = true),
                ownPlayCategory("hidden-child", "Hidden child", parentCategoryId = "hidden-parent"),
            ),
            memberships = listOf(
                ownPlayMembership("visible", "channel-a"),
                ownPlayMembership("hidden-child", "channel-b"),
            ),
        )

        val result = LiveOrganizationBrowsePolicy.resolve(snapshot)

        assertEquals(listOf("visible"), result.categories.map { it.category.categoryId })
        assertEquals(listOf("channel-a"), result.allChannelIds)
    }

    @Test
    fun hiddenOrExcludedMembershipsDoNotEnterBrowseModel() {
        val snapshot = LiveOrganizationSnapshot(
            sourceId = SOURCE_ID,
            activeMode = LiveOrganizationMode.OWNPLAY,
            categories = listOf(ownPlayCategory("film", "Film")),
            memberships = listOf(
                ownPlayMembership("film", "visible"),
                ownPlayMembership("film", "hidden", hidden = true),
                ownPlayMembership("film", "excluded", included = false),
            ),
        )

        val result = LiveOrganizationBrowsePolicy.resolve(snapshot)

        assertEquals(listOf("visible"), result.categories.single().channelIds)
        assertEquals(listOf("visible"), result.allChannelIds)
    }

    private fun providerCategory(categoryId: String, name: String) = LiveOrganizationCategory(
        sourceId = SOURCE_ID,
        mode = LiveOrganizationMode.PROVIDER,
        categoryId = categoryId,
        displayName = name,
        origin = LiveOrganizationOrigin.PROVIDER,
    )

    private fun ownPlayCategory(
        categoryId: String,
        name: String,
        parentCategoryId: String? = null,
        hidden: Boolean = false,
    ) = LiveOrganizationCategory(
        sourceId = SOURCE_ID,
        mode = LiveOrganizationMode.OWNPLAY,
        categoryId = categoryId,
        parentCategoryId = parentCategoryId,
        displayName = name,
        origin = LiveOrganizationOrigin.AUTO,
        hidden = hidden,
    )

    private fun providerMembership(categoryId: String, channelId: String) = LiveChannelMembership(
        sourceId = SOURCE_ID,
        mode = LiveOrganizationMode.PROVIDER,
        categoryId = categoryId,
        channelId = channelId,
        origin = LiveOrganizationOrigin.PROVIDER,
    )

    private fun ownPlayMembership(
        categoryId: String,
        channelId: String,
        included: Boolean = true,
        hidden: Boolean = false,
    ) = LiveChannelMembership(
        sourceId = SOURCE_ID,
        mode = LiveOrganizationMode.OWNPLAY,
        categoryId = categoryId,
        channelId = channelId,
        included = included,
        origin = LiveOrganizationOrigin.AUTO,
        hidden = hidden,
    )

    private companion object {
        const val SOURCE_ID = "source-a"
    }
}
