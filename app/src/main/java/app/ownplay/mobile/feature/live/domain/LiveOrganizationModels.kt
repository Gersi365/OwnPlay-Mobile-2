package app.ownplay.mobile.feature.live.domain

import app.ownplay.mobile.sources.domain.SourceId
import kotlinx.coroutines.flow.Flow

enum class LiveOrganizationMode {
    PROVIDER,
    OWNPLAY,
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

data class OwnPlayLiveCatalogSnapshot(
    val countries: List<OwnPlayCountryScope>,
    val semanticCategories: List<OwnPlayLiveSemanticCategory>,
    val channelIdsByPlacement: Map<OwnPlayLivePlacement, List<String>>,
)

interface LiveOrganizationRepository {
    fun observeMode(sourceId: SourceId): Flow<LiveOrganizationMode>

    fun observeProviderCatalog(sourceId: SourceId): Flow<ProviderLiveCatalogSnapshot>

    fun observeOwnPlayCatalog(sourceId: SourceId): Flow<OwnPlayLiveCatalogSnapshot>

    suspend fun setMode(sourceId: SourceId, mode: LiveOrganizationMode): Boolean

    suspend fun moveChannel(
        sourceId: SourceId,
        channelId: String,
        placement: OwnPlayLivePlacement,
    ): Boolean

    suspend fun resetChannelToAutomatic(sourceId: SourceId, channelId: String): Boolean
}
