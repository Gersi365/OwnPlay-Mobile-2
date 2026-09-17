package app.ownplay.mobile.feature.live.ui

import app.ownplay.mobile.feature.live.domain.LiveOrganizationOrdering
import app.ownplay.mobile.feature.live.domain.OwnPlayCountryScope
import app.ownplay.mobile.feature.live.domain.OwnPlayLiveCatalogSnapshot
import app.ownplay.mobile.feature.live.domain.OwnPlayLivePlacement
import app.ownplay.mobile.feature.live.domain.OwnPlayLiveSemanticCategory
import app.ownplay.mobile.feature.live.domain.ProviderLiveCatalogSnapshot

data class ProviderLiveCategoryOption(
    val categoryId: String,
    val displayName: String,
)

object LiveBrowseStatePolicy {
    const val PROVIDER_UNCATEGORIZED_ID = "__provider_uncategorized__"
    const val PROVIDER_UNCATEGORIZED_NAME = "Uncategorized"

    fun selectedCountryId(
        requestedCountryId: String?,
        countries: List<OwnPlayCountryScope>,
    ): String? = requestedCountryId
        ?.takeIf { requested -> countries.any { it.countryId == requested } }
        ?: countries.firstOrNull()?.countryId

    fun selectedSemanticCategory(requestedName: String?): OwnPlayLiveSemanticCategory =
        requestedName
            ?.let { name -> runCatching { OwnPlayLiveSemanticCategory.valueOf(name) }.getOrNull() }
            ?: OwnPlayLiveSemanticCategory.GENERAL

    fun providerCategoryOptions(catalog: ProviderLiveCatalogSnapshot): List<ProviderLiveCategoryOption> {
        val ordered = LiveOrganizationOrdering.providerCategories(catalog.categories)
            .map { category ->
                ProviderLiveCategoryOption(
                    categoryId = category.categoryId,
                    displayName = category.displayName,
                )
            }
            .toMutableList()
        if (catalog.channels.any { it.providerCategoryId == null }) {
            ordered += ProviderLiveCategoryOption(
                categoryId = PROVIDER_UNCATEGORIZED_ID,
                displayName = PROVIDER_UNCATEGORIZED_NAME,
            )
        }
        return ordered
    }

    fun selectedProviderCategoryId(
        requestedCategoryId: String?,
        catalog: ProviderLiveCatalogSnapshot,
    ): String? {
        val options = providerCategoryOptions(catalog)
        return requestedCategoryId
            ?.takeIf { requested -> options.any { it.categoryId == requested } }
            ?: options.firstOrNull()?.categoryId
    }

    fun visibleOwnPlayChannelIds(
        catalog: OwnPlayLiveCatalogSnapshot,
        countryId: String?,
        semanticCategory: OwnPlayLiveSemanticCategory,
        favoritesOnly: Boolean,
        favoriteChannelIds: Set<String>,
    ): List<String> {
        val selectedCountryId = countryId ?: return emptyList()
        val ids = catalog.channelIdsByPlacement[
            OwnPlayLivePlacement(
                countryId = selectedCountryId,
                semanticCategory = semanticCategory,
            ),
        ].orEmpty()
        return if (favoritesOnly) ids.filter(favoriteChannelIds::contains) else ids
    }

    fun visibleProviderChannelIds(
        catalog: ProviderLiveCatalogSnapshot,
        categoryId: String?,
        favoritesOnly: Boolean,
        favoriteChannelIds: Set<String>,
    ): List<String> {
        val selectedCategoryId = categoryId ?: return emptyList()
        val ordered = LiveOrganizationOrdering.providerChannels(
            providerCategories = catalog.categories,
            channels = catalog.channels,
        )
        return ordered
            .asSequence()
            .filter { channel ->
                if (selectedCategoryId == PROVIDER_UNCATEGORIZED_ID) {
                    channel.providerCategoryId == null
                } else {
                    channel.providerCategoryId == selectedCategoryId
                }
            }
            .filter { channel -> !favoritesOnly || channel.channelId in favoriteChannelIds }
            .map { it.channelId }
            .toList()
    }
}
