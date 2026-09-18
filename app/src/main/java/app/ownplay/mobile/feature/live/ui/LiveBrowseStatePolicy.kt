package app.ownplay.mobile.feature.live.ui

import app.ownplay.mobile.feature.live.domain.OwnPlayCountryScope
import app.ownplay.mobile.feature.live.domain.OwnPlayLiveCatalogSnapshot
import app.ownplay.mobile.feature.live.domain.OwnPlayLivePlacement
import app.ownplay.mobile.feature.live.domain.OwnPlayLiveSemanticCategory
import app.ownplay.mobile.feature.live.domain.ProviderLiveCatalogSnapshot
import app.ownplay.mobile.feature.live.domain.ProviderLiveOrganizationContract

data class ProviderLiveCategoryOption(
    val categoryId: String,
    val displayName: String,
)

object LiveBrowseStatePolicy {
    const val PROVIDER_UNCATEGORIZED_ID = ProviderLiveOrganizationContract.UNCATEGORIZED_CATEGORY_ID
    const val PROVIDER_UNCATEGORIZED_NAME = ProviderLiveOrganizationContract.UNCATEGORIZED_DISPLAY_NAME

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
        val options = catalog.categories.map { category ->
            ProviderLiveCategoryOption(
                categoryId = category.categoryId,
                displayName = category.displayName,
            )
        }.toMutableList()
        if (
            options.none { it.categoryId == PROVIDER_UNCATEGORIZED_ID } &&
            catalog.channels.any { it.providerCategoryId == null }
        ) {
            options += ProviderLiveCategoryOption(
                categoryId = PROVIDER_UNCATEGORIZED_ID,
                displayName = PROVIDER_UNCATEGORIZED_NAME,
            )
        }
        return options
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

    fun searchChannelIds(
        channels: List<app.ownplay.mobile.feature.live.domain.LiveOrganizationChannel>,
        query: String,
        favoritesOnly: Boolean,
        favoriteChannelIds: Set<String>,
    ): List<String> {
        val term = query.trim()
        if (term.isEmpty()) {
            return channels
                .asSequence()
                .filter { !favoritesOnly || it.channelId in favoriteChannelIds }
                .map { it.channelId }
                .toList()
        }
        return channels
            .asSequence()
            .filter { !favoritesOnly || it.channelId in favoriteChannelIds }
            .filter { channel ->
                sequenceOf(channel.name, channel.tvgName, channel.localName)
                    .filterNotNull()
                    .any { value -> value.contains(term, ignoreCase = true) }
            }
            .map { it.channelId }
            .toList()
    }

    fun shouldDismissPreview(
        selectedChannelId: String?,
        visibleChannelIds: Collection<String>,
        isPreview: Boolean,
    ): Boolean =
        isPreview && selectedChannelId != null && selectedChannelId !in visibleChannelIds

    fun visibleProviderChannelIds(
        catalog: ProviderLiveCatalogSnapshot,
        categoryId: String?,
        favoritesOnly: Boolean,
        favoriteChannelIds: Set<String>,
    ): List<String> {
        val selectedCategoryId = categoryId ?: return emptyList()
        return catalog.channels
            .asSequence()
            .filter { channel ->
                if (selectedCategoryId == PROVIDER_UNCATEGORIZED_ID) {
                    channel.providerCategoryId == null ||
                        channel.providerCategoryId == PROVIDER_UNCATEGORIZED_ID
                } else {
                    channel.providerCategoryId == selectedCategoryId
                }
            }
            .filter { channel -> !favoritesOnly || channel.channelId in favoriteChannelIds }
            .map { it.channelId }
            .toList()
    }
}
