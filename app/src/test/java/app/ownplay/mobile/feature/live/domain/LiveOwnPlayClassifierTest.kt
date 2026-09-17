package app.ownplay.mobile.feature.live.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveOwnPlayClassifierTest {
    @Test
    fun fixedSemanticTaxonomyHasExactlyCanonicalEightCategories() {
        assertEquals(
            listOf(
                "General",
                "News",
                "Sports",
                "Movies",
                "Series",
                "Kids",
                "Music",
                "Documentaries",
            ),
            OwnPlayLiveSemanticCategory.canonicalOrder.map { it.displayName },
        )
        assertEquals(8, OwnPlayLiveSemanticCategory.canonicalOrder.size)
    }

    @Test
    fun removedOrAmbiguousLabelsFallBackToGeneralWhileStrongSportsEvidenceWins() {
        assertEquals(
            OwnPlayLiveSemanticCategory.GENERAL,
            LiveOwnPlayClassifier.inferSemanticCategory("Premium Entertainment", "Mix One", null),
        )
        assertEquals(
            OwnPlayLiveSemanticCategory.GENERAL,
            LiveOwnPlayClassifier.inferSemanticCategory("Culture", "Culture World", null),
        )
        assertEquals(
            OwnPlayLiveSemanticCategory.SPORTS,
            LiveOwnPlayClassifier.inferSemanticCategory("Serie A", "DAZN Calcio", null),
        )
    }

    @Test
    fun multilingualHintsMapIntoFixedSemantics() {
        assertEquals(
            OwnPlayLiveSemanticCategory.NEWS,
            LiveOwnPlayClassifier.inferSemanticCategory("Shqipëri", "Top Lajme", null),
        )
        assertEquals(
            OwnPlayLiveSemanticCategory.MOVIES,
            LiveOwnPlayClassifier.inferSemanticCategory("Deutschland Film", "Kino Eins", null),
        )
        assertEquals(
            OwnPlayLiveSemanticCategory.KIDS,
            LiveOwnPlayClassifier.inferSemanticCategory("Italia Bambini", "Junior", null),
        )
        assertEquals(
            OwnPlayLiveSemanticCategory.DOCUMENTARIES,
            LiveOwnPlayClassifier.inferSemanticCategory("Dokumentarë", "Nature", null),
        )
    }

    @Test
    fun countryOrderFollowsProviderCatalogBlocksNotAlphabeticalOrder() {
        val categories = listOf(
            ProviderLiveCategory("italy-news", "Italia News", 10),
            ProviderLiveCategory("albania-sport", "Shqipëri Sport", 20),
        )
        val organization = LiveOwnPlayClassifier.buildAutomaticOrganization(
            providerCategories = categories,
            channels = listOf(
                LiveOrganizationChannel("al-1", "Sport One", null, "albania-sport", 0),
                LiveOrganizationChannel("it-1", "News One", null, "italy-news", 0),
            ),
        )

        assertEquals(listOf("Italy", "Albania"), organization.countries.map { it.displayName })
        assertEquals(
            OwnPlayLiveSemanticCategory.NEWS,
            organization.placementByChannelId.getValue("it-1").semanticCategory,
        )
        assertEquals(
            OwnPlayLiveSemanticCategory.SPORTS,
            organization.placementByChannelId.getValue("al-1").semanticCategory,
        )
    }

    @Test
    fun unknownCountryUsesNeutralStableScopeInsteadOfInventingCountry() {
        val organization = LiveOwnPlayClassifier.buildAutomaticOrganization(
            providerCategories = listOf(ProviderLiveCategory("mystery", "Premium Mix", 0)),
            channels = listOf(LiveOrganizationChannel("c1", "Channel One", null, "mystery", 0)),
        )

        val country = organization.countries.single()
        assertEquals(LiveOwnPlayClassifier.NEUTRAL_COUNTRY_ID, country.countryId)
        assertEquals("Unspecified", country.displayName)
        assertTrue(country.isNeutralScope)
        assertEquals(
            OwnPlayLiveSemanticCategory.GENERAL,
            organization.placementByChannelId.getValue("c1").semanticCategory,
        )
    }

    @Test
    fun providerOrderingUsesProviderOrderAndPreservesInputOrderForTies() {
        val categories = listOf(
            ProviderLiveCategory("z", "Zed", 1),
            ProviderLiveCategory("a", "Alpha", 1),
            ProviderLiveCategory("first", "First", 0),
        )

        assertEquals(
            listOf("first", "z", "a"),
            LiveOrganizationOrdering.providerCategories(categories).map { it.categoryId },
        )
    }

    @Test
    fun manualPlacementWinsAndResetRevealsCurrentAutomaticPlacement() {
        val automatic = OwnPlayLivePlacement("country:AL", OwnPlayLiveSemanticCategory.NEWS)
        val manual = OwnPlayLivePlacement("country:IT", OwnPlayLiveSemanticCategory.SPORTS)

        assertEquals(manual, LiveManualPlacementPolicy.effectivePlacement(automatic, manual))
        assertEquals(automatic, LiveManualPlacementPolicy.resetToAutomatic(automatic))
        assertFalse(LiveManualPlacementPolicy.resetToAutomatic(automatic) == manual)
    }
}
