package app.ownplay.mobile.feature.live.ui

import app.ownplay.mobile.feature.live.domain.LiveOrganizationChannel
import app.ownplay.mobile.feature.live.domain.OwnPlayCountryScope
import app.ownplay.mobile.feature.live.domain.OwnPlayLiveCatalogSnapshot
import app.ownplay.mobile.feature.live.domain.OwnPlayLivePlacement
import app.ownplay.mobile.feature.live.domain.OwnPlayLiveSemanticCategory
import app.ownplay.mobile.feature.live.domain.ProviderLiveCatalogSnapshot
import app.ownplay.mobile.feature.live.domain.ProviderLiveCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveBrowseStatePolicyTest {
    @Test
    fun countrySelectionFallsBackToFirstProviderOrderedCountry() {
        val countries = listOf(
            OwnPlayCountryScope("country:AL", "Albania", 0),
            OwnPlayCountryScope("country:IT", "Italy", 1),
        )

        assertEquals("country:AL", LiveBrowseStatePolicy.selectedCountryId("missing", countries))
        assertEquals("country:IT", LiveBrowseStatePolicy.selectedCountryId("country:IT", countries))
    }

    @Test
    fun ownPlayBrowseUsesCanonicalCategoryAndFavoritesAsSeparateFilter() {
        val placement = OwnPlayLivePlacement("country:AL", OwnPlayLiveSemanticCategory.NEWS)
        val catalog = OwnPlayLiveCatalogSnapshot(
            countries = listOf(OwnPlayCountryScope("country:AL", "Albania", 0)),
            semanticCategories = OwnPlayLiveSemanticCategory.canonicalOrder,
            channelIdsByPlacement = mapOf(placement to listOf("a", "b")),
        )

        assertEquals(
            listOf("a", "b"),
            LiveBrowseStatePolicy.visibleOwnPlayChannelIds(
                catalog = catalog,
                countryId = "country:AL",
                semanticCategory = OwnPlayLiveSemanticCategory.NEWS,
                favoritesOnly = false,
                favoriteChannelIds = setOf("b"),
            ),
        )
        assertEquals(
            listOf("b"),
            LiveBrowseStatePolicy.visibleOwnPlayChannelIds(
                catalog = catalog,
                countryId = "country:AL",
                semanticCategory = OwnPlayLiveSemanticCategory.NEWS,
                favoritesOnly = true,
                favoriteChannelIds = setOf("b"),
            ),
        )
        assertFalse(OwnPlayLiveSemanticCategory.canonicalOrder.any { it.displayName == "Favorites" })
    }

    @Test
    fun providerBrowsePreservesSnapshotOrderAndAddsOnlyUncategorizedWhenNeeded() {
        val catalog = ProviderLiveCatalogSnapshot(
            categories = listOf(
                ProviderLiveCategory("late", "Late", 9),
                ProviderLiveCategory("first", "First", 1),
            ),
            channels = listOf(
                LiveOrganizationChannel("uncategorized", "Loose", null, null, 3),
                LiveOrganizationChannel("first-channel", "One", null, "first", 2),
            ),
        )

        assertEquals(
            listOf("late", "first", LiveBrowseStatePolicy.PROVIDER_UNCATEGORIZED_ID),
            LiveBrowseStatePolicy.providerCategoryOptions(catalog).map { it.categoryId },
        )
        assertEquals(
            listOf("uncategorized"),
            LiveBrowseStatePolicy.visibleProviderChannelIds(
                catalog = catalog,
                categoryId = LiveBrowseStatePolicy.PROVIDER_UNCATEGORIZED_ID,
                favoritesOnly = false,
                favoriteChannelIds = emptySet(),
            ),
        )
    }
    @Test
    fun previewIsDismissedWhenCurrentFiltersHideSelectedChannel() {
        assertTrue(
            LiveBrowseStatePolicy.shouldDismissPreview(
                selectedChannelId = "channel-a",
                visibleChannelIds = listOf("channel-b"),
                isPreview = true,
            ),
        )
        assertFalse(
            LiveBrowseStatePolicy.shouldDismissPreview(
                selectedChannelId = "channel-a",
                visibleChannelIds = listOf("channel-a", "channel-b"),
                isPreview = true,
            ),
        )
        assertFalse(
            LiveBrowseStatePolicy.shouldDismissPreview(
                selectedChannelId = "channel-a",
                visibleChannelIds = emptyList(),
                isPreview = false,
            ),
        )
    }

    @Test
    fun searchMatchesProviderTvgAndLocalNamesWithoutChangingOrder() {
        val channels = listOf(
            LiveOrganizationChannel("a", "Provider News", "EPG News", null, 0, localName = "Local One"),
            LiveOrganizationChannel("b", "Provider Sport", null, null, 1),
        )
        assertEquals(
            listOf("a"),
            LiveBrowseStatePolicy.searchChannelIds(channels, "local", false, emptySet()),
        )
        assertEquals(
            listOf("a"),
            LiveBrowseStatePolicy.searchChannelIds(channels, "epg", false, emptySet()),
        )
        assertEquals(
            listOf("b"),
            LiveBrowseStatePolicy.searchChannelIds(channels, "provider", true, setOf("b")),
        )
    }

}
