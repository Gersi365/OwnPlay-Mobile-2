package app.ownplay.mobile.feature.live.data

import app.ownplay.mobile.data.db.LiveChannelEntity
import app.ownplay.mobile.data.db.LiveOrganizationDao
import app.ownplay.mobile.data.db.OwnPlayLiveCategoryEntity
import app.ownplay.mobile.data.db.OwnPlayLiveChannelMembershipEntity
import app.ownplay.mobile.data.db.ProviderCategoryEntity
import app.ownplay.mobile.feature.live.domain.LiveOrganizationChannel
import app.ownplay.mobile.feature.live.domain.LiveOwnPlayClassifier
import app.ownplay.mobile.feature.live.domain.OwnPlayLivePlacement
import app.ownplay.mobile.feature.live.domain.OwnPlayLiveSemanticCategory
import app.ownplay.mobile.feature.live.domain.ProviderLiveCategory
import app.ownplay.mobile.sources.domain.SourceId

interface LiveOrganizationRefreshStore {
    suspend fun reconcileAutomatic(
        sourceId: SourceId,
        generation: Long,
        providerCategories: List<ProviderCategoryEntity>,
        liveChannels: List<LiveChannelEntity>,
    )
}

object OwnPlayLiveStorageContract {
    const val AUTOMATIC_ORIGIN = "AUTO"
    const val LEGACY_MANUAL_ORIGIN = "MANUAL"
    const val MANUAL_OVERRIDE_MODE = "OWNPLAY_OVERRIDE"
    const val COUNTRY_SEMANTIC_KEY = "COUNTRY"

    fun semanticCategoryId(
        countryId: String,
        semanticCategory: OwnPlayLiveSemanticCategory,
    ): String = "$countryId::${semanticCategory.name}"

    fun parseSemanticCategoryId(categoryId: String): OwnPlayLivePlacement? {
        val separatorIndex = categoryId.lastIndexOf("::")
        if (separatorIndex <= 0 || separatorIndex >= categoryId.lastIndex) return null
        val countryId = categoryId.substring(0, separatorIndex)
        val semanticName = categoryId.substring(separatorIndex + 2)
        val semantic = runCatching { OwnPlayLiveSemanticCategory.valueOf(semanticName) }.getOrNull() ?: return null
        return OwnPlayLivePlacement(countryId = countryId, semanticCategory = semantic)
    }
}

data class OwnPlayLiveRefreshPlan(
    val categories: List<OwnPlayLiveCategoryEntity>,
    val memberships: List<OwnPlayLiveChannelMembershipEntity>,
)

object OwnPlayLiveRefreshPlanner {
    fun build(
        sourceId: SourceId,
        generation: Long,
        providerCategories: List<ProviderCategoryEntity>,
        liveChannels: List<LiveChannelEntity>,
        protectedManualChannelIds: Set<String> = emptySet(),
    ): OwnPlayLiveRefreshPlan {
        val liveCategoryRows = providerCategories.filter { row -> row.kind == "LIVE" && row.available }
        val liveChannelRows = liveChannels.filter(LiveChannelEntity::available)
        val automatic = LiveOwnPlayClassifier.buildAutomaticOrganization(
            providerCategories = liveCategoryRows.map { row ->
                ProviderLiveCategory(
                    categoryId = row.categoryKey,
                    displayName = row.name,
                    providerOrder = row.providerOrder,
                )
            },
            channels = liveChannelRows.map { row ->
                LiveOrganizationChannel(
                    channelId = row.channelId,
                    name = row.name,
                    tvgName = row.tvgName,
                    providerCategoryId = row.categoryKey,
                    providerOrder = row.providerOrder,
                )
            },
        )

        val categories = buildList {
            automatic.countries.forEach { country ->
                add(
                    OwnPlayLiveCategoryEntity(
                        sourceId = sourceId.value,
                        categoryId = country.countryId,
                        parentCategoryId = null,
                        displayName = country.displayName,
                        semanticKey = OwnPlayLiveStorageContract.COUNTRY_SEMANTIC_KEY,
                        origin = OwnPlayLiveStorageContract.AUTOMATIC_ORIGIN,
                        available = true,
                        lastSeenGeneration = generation,
                    ),
                )
                OwnPlayLiveSemanticCategory.canonicalOrder.forEach { semanticCategory ->
                    add(
                        OwnPlayLiveCategoryEntity(
                            sourceId = sourceId.value,
                            categoryId = OwnPlayLiveStorageContract.semanticCategoryId(
                                countryId = country.countryId,
                                semanticCategory = semanticCategory,
                            ),
                            parentCategoryId = country.countryId,
                            displayName = semanticCategory.displayName,
                            semanticKey = semanticCategory.name,
                            origin = OwnPlayLiveStorageContract.AUTOMATIC_ORIGIN,
                            available = true,
                            lastSeenGeneration = generation,
                        ),
                    )
                }
            }
        }

        val memberships = automatic.placementByChannelId.mapNotNull { (channelId, placement) ->
            if (channelId in protectedManualChannelIds) {
                null
            } else {
                OwnPlayLiveChannelMembershipEntity(
                    sourceId = sourceId.value,
                    categoryId = OwnPlayLiveStorageContract.semanticCategoryId(
                        countryId = placement.countryId,
                        semanticCategory = placement.semanticCategory,
                    ),
                    channelId = channelId,
                    included = true,
                    origin = OwnPlayLiveStorageContract.AUTOMATIC_ORIGIN,
                    confidence = null,
                    evidenceJson = null,
                    available = true,
                    lastSeenGeneration = generation,
                )
            }
        }

        return OwnPlayLiveRefreshPlan(
            categories = categories,
            memberships = memberships,
        )
    }
}

class RoomLiveOrganizationRefreshStore(
    private val dao: LiveOrganizationDao,
) : LiveOrganizationRefreshStore {
    override suspend fun reconcileAutomatic(
        sourceId: SourceId,
        generation: Long,
        providerCategories: List<ProviderCategoryEntity>,
        liveChannels: List<LiveChannelEntity>,
    ) {
        val protectedManualChannelIds = dao.getLegacyManualChannelIds(sourceId.value).toSet()
        val plan = OwnPlayLiveRefreshPlanner.build(
            sourceId = sourceId,
            generation = generation,
            providerCategories = providerCategories,
            liveChannels = liveChannels,
            protectedManualChannelIds = protectedManualChannelIds,
        )
        if (plan.categories.isNotEmpty()) dao.upsertOwnPlayCategories(plan.categories)
        if (plan.memberships.isNotEmpty()) dao.upsertOwnPlayMemberships(plan.memberships)
        dao.markMissingAutomaticMembershipsUnavailable(sourceId.value, generation)
        dao.markMissingAutomaticCategoriesUnavailable(sourceId.value, generation)
    }
}
