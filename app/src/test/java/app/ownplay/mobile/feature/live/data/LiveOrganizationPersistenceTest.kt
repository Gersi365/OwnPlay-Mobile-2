package app.ownplay.mobile.feature.live.data

import app.ownplay.mobile.data.db.LiveChannelEntity
import app.ownplay.mobile.data.db.ProviderCategoryEntity
import app.ownplay.mobile.feature.live.domain.OwnPlayLivePlacement
import app.ownplay.mobile.feature.live.domain.OwnPlayLiveSemanticCategory
import app.ownplay.mobile.sources.domain.SourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveOrganizationPersistenceTest {
    @Test
    fun automaticPlanCreatesCountryPlusExactlyEightCanonicalCategories() {
        val plan = OwnPlayLiveRefreshPlanner.build(
            sourceId = SourceId("source-1"),
            generation = 7,
            providerCategories = listOf(
                providerCategory("italy-sports", "IT Sports", 2),
            ),
            liveChannels = listOf(
                liveChannel("channel-1", "italy-sports", "DAZN 1", 4),
            ),
        )

        val country = plan.categories.single { it.parentCategoryId == null }
        val children = plan.categories.filter { it.parentCategoryId == country.categoryId }
        assertEquals("country:IT", country.categoryId)
        assertEquals(8, children.size)
        assertEquals(
            OwnPlayLiveSemanticCategory.canonicalOrder.map(OwnPlayLiveSemanticCategory::name),
            children.mapNotNull { it.semanticKey },
        )
        assertEquals(
            OwnPlayLiveStorageContract.semanticCategoryId(
                "country:IT",
                OwnPlayLiveSemanticCategory.SPORTS,
            ),
            plan.memberships.single().categoryId,
        )
    }

    @Test
    fun protectedLegacyManualChannelIsNotOverwrittenByAutomaticMembership() {
        val plan = OwnPlayLiveRefreshPlanner.build(
            sourceId = SourceId("source-1"),
            generation = 8,
            providerCategories = listOf(providerCategory("news", "AL News", 1)),
            liveChannels = listOf(liveChannel("channel-1", "news", "Top News", 1)),
            protectedManualChannelIds = setOf("channel-1"),
        )

        assertTrue(plan.categories.isNotEmpty())
        assertTrue(plan.memberships.isEmpty())
    }

    @Test
    fun manualOverrideCategoryIdentityRoundTripsWithoutProviderMutation() {
        val placement = OwnPlayLivePlacement(
            countryId = "country:AL",
            semanticCategory = OwnPlayLiveSemanticCategory.NEWS,
        )
        val categoryId = OwnPlayLiveStorageContract.semanticCategoryId(
            placement.countryId,
            placement.semanticCategory,
        )

        assertEquals(placement, OwnPlayLiveStorageContract.parseSemanticCategoryId(categoryId))
        assertFalse(categoryId.contains("provider-category"))
    }

    private fun providerCategory(providerKey: String, name: String, providerOrder: Int) =
        ProviderCategoryEntity(
            sourceId = "source-1",
            kind = "LIVE",
            categoryKey = providerKey,
            providerKey = providerKey,
            name = name,
            providerOrder = providerOrder,
            available = true,
            lastSeenGeneration = 1,
        )

    private fun liveChannel(
        channelId: String,
        categoryKey: String,
        name: String,
        providerOrder: Int,
    ) = LiveChannelEntity(
        channelId = channelId,
        sourceId = "source-1",
        providerKey = channelId,
        providerStreamId = channelId,
        categoryKey = categoryKey,
        name = name,
        tvgId = null,
        tvgName = null,
        logoUrl = null,
        streamLocator = "opaque://$channelId",
        providerOrder = providerOrder,
        available = true,
        lastSeenGeneration = 1,
    )
}
