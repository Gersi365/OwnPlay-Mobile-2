package app.ownplay.mobile.feature.live.domain

import app.ownplay.mobile.sources.domain.SourceId
import kotlinx.coroutines.flow.Flow

enum class LiveOrganizationMode {
    PROVIDER,
    OWNPLAY,
}

object ProviderLiveOrganizationContract {
    const val UNCATEGORIZED_CATEGORY_ID = "__provider_uncategorized__"
    const val UNCATEGORIZED_DISPLAY_NAME = "Uncategorized"
}

enum class OwnPlayLiveSemanticCategory(val displayName: String) {
    GENERAL("General"),
    NEWS("News"),
    SPORTS("Sports"),
    MOVIES("Movies"),
    SERIES("Series"),
    KIDS("Kids"),
    MUSIC("Music"),
    DOCUMENTARIES("Documentaries"),
    ;

    companion object {
        val canonicalOrder: List<OwnPlayLiveSemanticCategory> = entries.toList()
    }
}

data class ProviderLiveCategory(
    val categoryId: String,
    val displayName: String,
    val providerOrder: Int,
)

data class LiveOrganizationChannel(
    val channelId: String,
    val name: String,
    val tvgName: String?,
    val providerCategoryId: String?,
    val providerOrder: Int,
    val logoUrl: String? = null,
    val localName: String? = null,
)

data class OwnPlayCountryScope(
    val countryId: String,
    val displayName: String,
    val providerOrder: Int,
    val isNeutralScope: Boolean = false,
)

data class OwnPlayLivePlacement(
    val countryId: String,
    val semanticCategory: OwnPlayLiveSemanticCategory,
)

data class AutomaticLiveOrganization(
    val countries: List<OwnPlayCountryScope>,
    val placementByChannelId: Map<String, OwnPlayLivePlacement>,
)

data class ProviderLiveCatalogSnapshot(
    val categories: List<ProviderLiveCategory>,
    val channels: List<LiveOrganizationChannel>,
)

data class ProviderLiveManagementCategory(
    val categoryId: String,
    val displayName: String,
    val providerOrder: Int,
    val hidden: Boolean,
    val manualOrder: Int?,
)

data class ProviderLiveManagementChannel(
    val channelId: String,
    val categoryId: String,
    val name: String,
    val tvgName: String?,
    val logoUrl: String?,
    val providerOrder: Int,
    val hidden: Boolean,
    val manualOrder: Int?,
)

data class ProviderLiveManagementSnapshot(
    val categories: List<ProviderLiveManagementCategory>,
    val channels: List<ProviderLiveManagementChannel>,
)

data class OwnPlayLiveCatalogSnapshot(
    val countries: List<OwnPlayCountryScope>,
    val semanticCategories: List<OwnPlayLiveSemanticCategory>,
    val channelIdsByPlacement: Map<OwnPlayLivePlacement, List<String>>,
    val manualPlacementChannelIds: Set<String> = emptySet(),
    val channels: List<LiveOrganizationChannel> = emptyList(),
)

interface LiveOrganizationRepository {
    fun observeMode(sourceId: SourceId): Flow<LiveOrganizationMode>

    fun observeProviderCatalog(sourceId: SourceId): Flow<ProviderLiveCatalogSnapshot>

    fun observeProviderManagement(sourceId: SourceId): Flow<ProviderLiveManagementSnapshot>

    fun observeOwnPlayCatalog(sourceId: SourceId): Flow<OwnPlayLiveCatalogSnapshot>

    fun observeFavoriteChannelIds(sourceId: SourceId): Flow<Set<String>>

    suspend fun setMode(sourceId: SourceId, mode: LiveOrganizationMode): Boolean

    suspend fun setFavorite(sourceId: SourceId, channelId: String, favorite: Boolean): Boolean

    suspend fun setProviderCategoryHidden(
        sourceId: SourceId,
        categoryId: String,
        hidden: Boolean,
    ): Boolean

    suspend fun setProviderCategoryOrder(
        sourceId: SourceId,
        orderedCategoryIds: List<String>,
    ): Boolean

    suspend fun resetProviderCategoryOrder(sourceId: SourceId): Boolean

    suspend fun setProviderChannelHidden(
        sourceId: SourceId,
        categoryId: String,
        channelId: String,
        hidden: Boolean,
    ): Boolean

    suspend fun setProviderChannelOrder(
        sourceId: SourceId,
        categoryId: String,
        orderedChannelIds: List<String>,
    ): Boolean

    suspend fun resetProviderChannelOrder(
        sourceId: SourceId,
        categoryId: String,
    ): Boolean

    suspend fun moveChannel(
        sourceId: SourceId,
        channelId: String,
        placement: OwnPlayLivePlacement,
    ): Boolean

    suspend fun resetChannelToAutomatic(sourceId: SourceId, channelId: String): Boolean
}
