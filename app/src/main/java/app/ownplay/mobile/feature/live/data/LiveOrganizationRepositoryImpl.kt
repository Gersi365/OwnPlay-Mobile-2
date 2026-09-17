package app.ownplay.mobile.feature.live.data

import androidx.room.withTransaction
import app.ownplay.mobile.data.db.LiveChannelMembershipPersonalizationEntity
import app.ownplay.mobile.data.db.LiveOrganizationDao
import app.ownplay.mobile.data.db.LiveOrganizationPreferenceEntity
import app.ownplay.mobile.data.db.OwnPlayDatabase
import app.ownplay.mobile.data.db.OwnPlayLiveCategoryEntity
import app.ownplay.mobile.data.db.OwnPlayLiveChannelMembershipEntity
import app.ownplay.mobile.feature.live.domain.LiveOrganizationChannel
import app.ownplay.mobile.feature.live.domain.LiveOrganizationMode
import app.ownplay.mobile.feature.live.domain.LiveOrganizationOrdering
import app.ownplay.mobile.feature.live.domain.LiveOrganizationRepository
import app.ownplay.mobile.feature.live.domain.LiveOwnPlayClassifier
import app.ownplay.mobile.feature.live.domain.OwnPlayCountryScope
import app.ownplay.mobile.feature.live.domain.OwnPlayLiveCatalogSnapshot
import app.ownplay.mobile.feature.live.domain.OwnPlayLivePlacement
import app.ownplay.mobile.feature.live.domain.OwnPlayLiveSemanticCategory
import app.ownplay.mobile.feature.live.domain.ProviderLiveCatalogSnapshot
import app.ownplay.mobile.feature.live.domain.ProviderLiveCategory
import app.ownplay.mobile.sources.domain.SourceId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class RoomLiveOrganizationRepository(
    private val database: OwnPlayDatabase,
    private val dao: LiveOrganizationDao,
) : LiveOrganizationRepository {
    override fun observeMode(sourceId: SourceId): Flow<LiveOrganizationMode> =
        dao.observePreference(sourceId.value).map { row ->
            row?.activeMode
                ?.let { value -> runCatching { LiveOrganizationMode.valueOf(value) }.getOrNull() }
                ?: LiveOrganizationMode.PROVIDER
        }

    override fun observeProviderCatalog(sourceId: SourceId): Flow<ProviderLiveCatalogSnapshot> =
        combine(
            dao.observeProviderLiveCategories(sourceId.value),
            dao.observeLiveChannels(sourceId.value),
        ) { categoryRows, channelRows ->
            ProviderLiveCatalogSnapshot(
                categories = categoryRows.map { row ->
                    ProviderLiveCategory(
                        categoryId = row.categoryKey,
                        displayName = row.name,
                        providerOrder = row.providerOrder,
                    )
                },
                channels = channelRows.map { row ->
                    LiveOrganizationChannel(
                        channelId = row.channelId,
                        name = row.name,
                        tvgName = row.tvgName,
                        providerCategoryId = row.categoryKey,
                        providerOrder = row.providerOrder,
                    )
                },
            )
        }

    override fun observeOwnPlayCatalog(sourceId: SourceId): Flow<OwnPlayLiveCatalogSnapshot> =
        combine(
            observeProviderCatalog(sourceId),
            dao.observeOwnPlayCategoriesForCompatibility(sourceId.value),
            dao.observeManualPlacementOverrides(sourceId.value),
            dao.observeLegacyManualMemberships(sourceId.value),
        ) { provider, persistedCategories, overrides, legacyManualMemberships ->
            buildOwnPlaySnapshot(
                provider = provider,
                persistedCategories = persistedCategories,
                overrides = overrides,
                legacyManualMemberships = legacyManualMemberships,
            )
        }

    override fun observeFavoriteChannelIds(sourceId: SourceId): Flow<Set<String>> =
        dao.observeFavoriteChannelIds(sourceId.value).map { rows -> rows.toSet() }

    override suspend fun setMode(sourceId: SourceId, mode: LiveOrganizationMode): Boolean = try {
        database.withTransaction {
            if (dao.countSource(sourceId.value) == 0) return@withTransaction false
            dao.upsertPreference(
                LiveOrganizationPreferenceEntity(
                    sourceId = sourceId.value,
                    activeMode = mode.name,
                ),
            )
            true
        }
    } catch (_: Exception) {
        false
    }

    override suspend fun moveChannel(
        sourceId: SourceId,
        channelId: String,
        placement: OwnPlayLivePlacement,
    ): Boolean = try {
        database.withTransaction {
            val channel = dao.getAvailableChannel(sourceId.value, channelId)
                ?: return@withTransaction false
            val targetCategoryId = OwnPlayLiveStorageContract.semanticCategoryId(
                countryId = placement.countryId,
                semanticCategory = placement.semanticCategory,
            )
            val targetCategory = dao.getAvailableOwnPlayCategory(sourceId.value, targetCategoryId)
                ?: return@withTransaction false
            if (targetCategory.parentCategoryId != placement.countryId ||
                targetCategory.semanticKey != placement.semanticCategory.name
            ) {
                return@withTransaction false
            }

            dao.deleteManualPlacementOverrides(sourceId.value, channel.channelId)
            dao.upsertManualPlacementOverride(
                LiveChannelMembershipPersonalizationEntity(
                    sourceId = sourceId.value,
                    organizationMode = OwnPlayLiveStorageContract.MANUAL_OVERRIDE_MODE,
                    categoryId = targetCategoryId,
                    channelId = channel.channelId,
                    hidden = false,
                    manualOrder = null,
                ),
            )
            dao.clearLegacyManualMemberships(sourceId.value, channel.channelId)
            true
        }
    } catch (_: Exception) {
        false
    }

    override suspend fun resetChannelToAutomatic(sourceId: SourceId, channelId: String): Boolean = try {
        database.withTransaction {
            val removedOverrides = dao.deleteManualPlacementOverrides(sourceId.value, channelId)
            val clearedLegacy = dao.clearLegacyManualMemberships(sourceId.value, channelId)
            removedOverrides > 0 || clearedLegacy > 0
        }
    } catch (_: Exception) {
        false
    }

    private fun buildOwnPlaySnapshot(
        provider: ProviderLiveCatalogSnapshot,
        persistedCategories: List<OwnPlayLiveCategoryEntity>,
        overrides: List<LiveChannelMembershipPersonalizationEntity>,
        legacyManualMemberships: List<OwnPlayLiveChannelMembershipEntity>,
    ): OwnPlayLiveCatalogSnapshot {
        val automatic = LiveOwnPlayClassifier.buildAutomaticOrganization(
            providerCategories = provider.categories,
            channels = provider.channels,
        )
        val persistedCategoryById = persistedCategories.associateBy(OwnPlayLiveCategoryEntity::categoryId)
        val overrideByChannelId = overrides
            .mapNotNull { row ->
                OwnPlayLiveStorageContract.parseSemanticCategoryId(row.categoryId)
                    ?.let { placement -> row.channelId to placement }
            }
            .toMap()
        val legacyByChannelId = legacyManualMemberships
            .groupBy(OwnPlayLiveChannelMembershipEntity::channelId)
            .mapValues { (_, rows) ->
                rows.firstNotNullOfOrNull { row ->
                    legacyPlacement(row, persistedCategoryById)
                }
            }
            .mapNotNull { (channelId, placement) -> placement?.let { channelId to it } }
            .toMap()

        val orderedChannels = LiveOrganizationOrdering.providerChannels(
            providerCategories = provider.categories,
            channels = provider.channels,
        )
        val effectivePlacementByChannelId = linkedMapOf<String, OwnPlayLivePlacement>()
        orderedChannels.forEach { channel ->
            val automaticPlacement = automatic.placementByChannelId[channel.channelId] ?: return@forEach
            effectivePlacementByChannelId[channel.channelId] =
                overrideByChannelId[channel.channelId]
                    ?: legacyByChannelId[channel.channelId]
                    ?: automaticPlacement
        }

        val countryById = linkedMapOf<String, OwnPlayCountryScope>()
        automatic.countries.forEach { country -> countryById[country.countryId] = country }
        orderedChannels.forEach { channel ->
            val placement = effectivePlacementByChannelId[channel.channelId] ?: return@forEach
            if (placement.countryId !in countryById) {
                val persistedCountry = persistedCategoryById[placement.countryId]
                countryById[placement.countryId] = OwnPlayCountryScope(
                    countryId = placement.countryId,
                    displayName = when {
                        placement.countryId == LiveOwnPlayClassifier.NEUTRAL_COUNTRY_ID ->
                            LiveOwnPlayClassifier.NEUTRAL_COUNTRY_DISPLAY_NAME
                        persistedCountry != null -> persistedCountry.displayName
                        else -> placement.countryId.removePrefix("country:")
                    },
                    providerOrder = countryById.size,
                    isNeutralScope = placement.countryId == LiveOwnPlayClassifier.NEUTRAL_COUNTRY_ID,
                )
            }
        }

        val channelsByPlacement = linkedMapOf<OwnPlayLivePlacement, MutableList<String>>()
        orderedChannels.forEach { channel ->
            val placement = effectivePlacementByChannelId[channel.channelId] ?: return@forEach
            channelsByPlacement.getOrPut(placement, ::mutableListOf).add(channel.channelId)
        }

        return OwnPlayLiveCatalogSnapshot(
            countries = countryById.values.toList(),
            semanticCategories = OwnPlayLiveSemanticCategory.canonicalOrder,
            channelIdsByPlacement = channelsByPlacement.mapValues { (_, channelIds) -> channelIds.toList() },
            manualPlacementChannelIds = overrideByChannelId.keys + legacyByChannelId.keys,
        )
    }

    private fun legacyPlacement(
        membership: OwnPlayLiveChannelMembershipEntity,
        persistedCategoryById: Map<String, OwnPlayLiveCategoryEntity>,
    ): OwnPlayLivePlacement? {
        OwnPlayLiveStorageContract.parseSemanticCategoryId(membership.categoryId)?.let { return it }
        val target = persistedCategoryById[membership.categoryId] ?: return null
        val semantic = target.semanticKey
            ?.let { key -> runCatching { OwnPlayLiveSemanticCategory.valueOf(key) }.getOrNull() }
            ?: return null
        val countryId = target.parentCategoryId?.takeIf { parentId -> parentId.startsWith("country:") }
            ?: return null
        return OwnPlayLivePlacement(
            countryId = countryId,
            semanticCategory = semantic,
        )
    }
}
