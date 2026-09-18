package app.ownplay.mobile.feature.live.data

import androidx.room.withTransaction
import app.ownplay.mobile.data.db.ChannelPersonalizationEntity
import app.ownplay.mobile.data.db.LiveCategoryScopePersonalizationEntity
import app.ownplay.mobile.data.db.LiveChannelEntity
import app.ownplay.mobile.data.db.LiveChannelMembershipPersonalizationEntity
import app.ownplay.mobile.data.db.LiveOrganizationDao
import app.ownplay.mobile.data.db.LiveOrganizationPreferenceEntity
import app.ownplay.mobile.data.db.OwnPlayDatabase
import app.ownplay.mobile.data.db.OwnPlayLiveCategoryEntity
import app.ownplay.mobile.data.db.OwnPlayLiveChannelMembershipEntity
import app.ownplay.mobile.data.db.ProviderCategoryEntity
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
import app.ownplay.mobile.feature.live.domain.ProviderLiveManagementCategory
import app.ownplay.mobile.feature.live.domain.ProviderLiveManagementChannel
import app.ownplay.mobile.feature.live.domain.ProviderLiveManagementSnapshot
import app.ownplay.mobile.feature.live.domain.ProviderLiveOrganizationContract
import app.ownplay.mobile.sources.domain.SourceId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
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
        observeProviderManagement(sourceId)
            .map(::visibleProviderCatalog)
            .flowOn(Dispatchers.Default)

    override fun observeProviderManagement(
        sourceId: SourceId,
    ): Flow<ProviderLiveManagementSnapshot> =
        combine(
            dao.observeProviderLiveCategories(sourceId.value),
            dao.observeLiveChannels(sourceId.value),
            dao.observeProviderCategoryPersonalization(sourceId.value),
            dao.observeProviderChannelPersonalization(sourceId.value),
        ) { categoryRows, channelRows, categoryPersonalization, channelPersonalization ->
            buildProviderManagementSnapshot(
                categoryRows = categoryRows,
                channelRows = channelRows,
                categoryPersonalization = categoryPersonalization,
                channelPersonalization = channelPersonalization,
            )
        }.flowOn(Dispatchers.Default)

    private fun observeRawProviderCatalog(
        sourceId: SourceId,
    ): Flow<ProviderLiveCatalogSnapshot> =
        combine(
            dao.observeProviderLiveCategories(sourceId.value),
            dao.observeLiveChannels(sourceId.value),
            dao.observeChannelPersonalization(sourceId.value),
        ) { categoryRows, channelRows, channelPersonalization ->
            val personalizationByChannelId = channelPersonalization.associateBy { it.channelId }
            ProviderLiveCatalogSnapshot(
                categories = categoryRows.map { row ->
                    ProviderLiveCategory(
                        categoryId = row.categoryKey,
                        displayName = row.name,
                        providerOrder = row.providerOrder,
                    )
                },
                channels = channelRows.map { row ->
                    val personalization = personalizationByChannelId[row.channelId]
                    LiveOrganizationChannel(
                        channelId = row.channelId,
                        name = row.name,
                        tvgName = row.tvgName,
                        providerCategoryId = row.categoryKey,
                        providerOrder = row.providerOrder,
                        logoUrl = personalization?.localLogo?.trim()?.takeIf(String::isNotEmpty) ?: row.logoUrl,
                        localName = personalization?.localName?.trim()?.takeIf(String::isNotEmpty),
                    )
                },
            )
        }.flowOn(Dispatchers.Default)

    override fun observeOwnPlayCatalog(sourceId: SourceId): Flow<OwnPlayLiveCatalogSnapshot> =
        combine(
            observeRawProviderCatalog(sourceId),
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
        }.flowOn(Dispatchers.Default)

    override fun observeFavoriteChannelIds(sourceId: SourceId): Flow<Set<String>> =
        dao.observeFavoriteChannelIds(sourceId.value).map { rows -> rows.toSet() }

    override suspend fun setFavorite(
        sourceId: SourceId,
        channelId: String,
        favorite: Boolean,
    ): Boolean = try {
        database.withTransaction {
            val channel = dao.getAvailableChannel(sourceId.value, channelId)
                ?: return@withTransaction false
            val current = dao.getChannelPersonalization(channel.channelId)
            dao.upsertChannelPersonalization(
                (current ?: ChannelPersonalizationEntity(channelId = channel.channelId))
                    .copy(favorite = favorite),
            )
            true
        }
    } catch (_: Exception) {
        false
    }

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

    override suspend fun setProviderCategoryHidden(
        sourceId: SourceId,
        categoryId: String,
        hidden: Boolean,
    ): Boolean = try {
        database.withTransaction {
            val categoryIds = providerCategoryIds(sourceId)
            if (categoryId !in categoryIds) return@withTransaction false
            val current = dao.getProviderCategoryPersonalization(sourceId.value, categoryId)
            dao.upsertProviderCategoryPersonalization(
                (current ?: LiveCategoryScopePersonalizationEntity(
                    sourceId = sourceId.value,
                    organizationMode = LiveOrganizationMode.PROVIDER.name,
                    categoryId = categoryId,
                )).copy(hidden = hidden),
            )
            true
        }
    } catch (_: Exception) {
        false
    }

    override suspend fun setProviderCategoryOrder(
        sourceId: SourceId,
        orderedCategoryIds: List<String>,
    ): Boolean = try {
        database.withTransaction {
            val ordered = orderedCategoryIds.filter(String::isNotBlank).distinct()
            val available = providerCategoryIds(sourceId)
            if (ordered.size != available.size || ordered.toSet() != available.toSet()) {
                return@withTransaction false
            }
            ordered.forEachIndexed { index, categoryId ->
                val current = dao.getProviderCategoryPersonalization(sourceId.value, categoryId)
                dao.upsertProviderCategoryPersonalization(
                    (current ?: LiveCategoryScopePersonalizationEntity(
                        sourceId = sourceId.value,
                        organizationMode = LiveOrganizationMode.PROVIDER.name,
                        categoryId = categoryId,
                    )).copy(manualOrder = index),
                )
            }
            true
        }
    } catch (_: Exception) {
        false
    }

    override suspend fun resetProviderCategoryOrder(sourceId: SourceId): Boolean = try {
        database.withTransaction {
            val available = providerCategoryIds(sourceId)
            if (available.isEmpty()) return@withTransaction false
            available.forEach { categoryId ->
                val current = dao.getProviderCategoryPersonalization(sourceId.value, categoryId)
                if (current?.manualOrder != null) {
                    dao.upsertProviderCategoryPersonalization(current.copy(manualOrder = null))
                }
            }
            true
        }
    } catch (_: Exception) {
        false
    }

    override suspend fun setProviderChannelHidden(
        sourceId: SourceId,
        categoryId: String,
        channelId: String,
        hidden: Boolean,
    ): Boolean = try {
        database.withTransaction {
            val channelIds = providerChannelIds(sourceId, categoryId)
            if (channelId !in channelIds) return@withTransaction false
            val current = dao.getProviderChannelPersonalization(
                sourceId.value,
                categoryId,
                channelId,
            )
            dao.upsertProviderChannelPersonalization(
                (current ?: LiveChannelMembershipPersonalizationEntity(
                    sourceId = sourceId.value,
                    organizationMode = LiveOrganizationMode.PROVIDER.name,
                    categoryId = categoryId,
                    channelId = channelId,
                )).copy(hidden = hidden),
            )
            true
        }
    } catch (_: Exception) {
        false
    }

    override suspend fun setProviderChannelOrder(
        sourceId: SourceId,
        categoryId: String,
        orderedChannelIds: List<String>,
    ): Boolean = try {
        database.withTransaction {
            val ordered = orderedChannelIds.filter(String::isNotBlank).distinct()
            val available = providerChannelIds(sourceId, categoryId)
            if (ordered.size != available.size || ordered.toSet() != available.toSet()) {
                return@withTransaction false
            }
            ordered.forEachIndexed { index, channelId ->
                val current = dao.getProviderChannelPersonalization(
                    sourceId.value,
                    categoryId,
                    channelId,
                )
                dao.upsertProviderChannelPersonalization(
                    (current ?: LiveChannelMembershipPersonalizationEntity(
                        sourceId = sourceId.value,
                        organizationMode = LiveOrganizationMode.PROVIDER.name,
                        categoryId = categoryId,
                        channelId = channelId,
                    )).copy(manualOrder = index),
                )
            }
            true
        }
    } catch (_: Exception) {
        false
    }

    override suspend fun resetProviderChannelOrder(
        sourceId: SourceId,
        categoryId: String,
    ): Boolean = try {
        database.withTransaction {
            val available = providerChannelIds(sourceId, categoryId)
            if (available.isEmpty()) return@withTransaction false
            available.forEach { channelId ->
                val current = dao.getProviderChannelPersonalization(
                    sourceId.value,
                    categoryId,
                    channelId,
                )
                if (current?.manualOrder != null) {
                    dao.upsertProviderChannelPersonalization(current.copy(manualOrder = null))
                }
            }
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

    private fun buildProviderManagementSnapshot(
        categoryRows: List<ProviderCategoryEntity>,
        channelRows: List<LiveChannelEntity>,
        categoryPersonalization: List<LiveCategoryScopePersonalizationEntity>,
        channelPersonalization: List<LiveChannelMembershipPersonalizationEntity>,
    ): ProviderLiveManagementSnapshot {
        val categoryPersonalizationById = categoryPersonalization.associateBy { it.categoryId }
        val categories = buildList {
            categoryRows.forEach { row ->
                val personalization = categoryPersonalizationById[row.categoryKey]
                add(
                    ProviderLiveManagementCategory(
                        categoryId = row.categoryKey,
                        displayName = row.name,
                        providerOrder = row.providerOrder,
                        hidden = personalization?.hidden ?: false,
                        manualOrder = personalization?.manualOrder,
                    ),
                )
            }
            if (channelRows.any { it.categoryKey == null }) {
                val uncategorizedId = ProviderLiveOrganizationContract.UNCATEGORIZED_CATEGORY_ID
                val personalization = categoryPersonalizationById[uncategorizedId]
                add(
                    ProviderLiveManagementCategory(
                        categoryId = uncategorizedId,
                        displayName = ProviderLiveOrganizationContract.UNCATEGORIZED_DISPLAY_NAME,
                        providerOrder = Int.MAX_VALUE,
                        hidden = personalization?.hidden ?: false,
                        manualOrder = personalization?.manualOrder,
                    ),
                )
            }
        }.withIndex()
            .sortedWith(
                compareBy<IndexedValue<ProviderLiveManagementCategory>> {
                    if (it.value.manualOrder == null) 1 else 0
                }.thenBy { it.value.manualOrder ?: it.value.providerOrder }
                    .thenBy { it.value.providerOrder }
                    .thenBy { it.index },
            )
            .map(IndexedValue<ProviderLiveManagementCategory>::value)

        val categoryRank = categories.mapIndexed { index, category -> category.categoryId to index }.toMap()
        val channelPersonalizationByKey = channelPersonalization.associateBy { row ->
            row.categoryId to row.channelId
        }
        val channels = channelRows.map { row ->
            val categoryId = row.categoryKey ?: ProviderLiveOrganizationContract.UNCATEGORIZED_CATEGORY_ID
            val personalization = channelPersonalizationByKey[categoryId to row.channelId]
            ProviderLiveManagementChannel(
                channelId = row.channelId,
                categoryId = categoryId,
                name = row.name,
                tvgName = row.tvgName,
                logoUrl = row.logoUrl,
                providerOrder = row.providerOrder,
                hidden = personalization?.hidden ?: false,
                manualOrder = personalization?.manualOrder,
            )
        }.withIndex()
            .sortedWith(
                compareBy<IndexedValue<ProviderLiveManagementChannel>> {
                    categoryRank[it.value.categoryId] ?: Int.MAX_VALUE
                }.thenBy { if (it.value.manualOrder == null) 1 else 0 }
                    .thenBy { it.value.manualOrder ?: it.value.providerOrder }
                    .thenBy { it.value.providerOrder }
                    .thenBy { it.index },
            )
            .map(IndexedValue<ProviderLiveManagementChannel>::value)

        return ProviderLiveManagementSnapshot(
            categories = categories,
            channels = channels,
        )
    }

    private fun visibleProviderCatalog(
        management: ProviderLiveManagementSnapshot,
    ): ProviderLiveCatalogSnapshot {
        val visibleCategories = management.categories.filterNot { it.hidden }
        val visibleCategoryIds = visibleCategories.mapTo(linkedSetOf()) { it.categoryId }
        return ProviderLiveCatalogSnapshot(
            categories = visibleCategories.map { category ->
                ProviderLiveCategory(
                    categoryId = category.categoryId,
                    displayName = category.displayName,
                    providerOrder = category.providerOrder,
                )
            },
            channels = management.channels
                .asSequence()
                .filter { channel -> !channel.hidden && channel.categoryId in visibleCategoryIds }
                .map { channel ->
                    LiveOrganizationChannel(
                        channelId = channel.channelId,
                        name = channel.name,
                        tvgName = channel.tvgName,
                        providerCategoryId = channel.categoryId,
                        providerOrder = channel.providerOrder,
                        logoUrl = channel.logoUrl,
                    )
                }
                .toList(),
        )
    }

    private suspend fun providerCategoryIds(sourceId: SourceId): List<String> = buildList {
        addAll(dao.getProviderLiveCategoryIds(sourceId.value))
        if (dao.countProviderUncategorizedChannels(sourceId.value) > 0) {
            add(ProviderLiveOrganizationContract.UNCATEGORIZED_CATEGORY_ID)
        }
    }

    private suspend fun providerChannelIds(
        sourceId: SourceId,
        categoryId: String,
    ): List<String> = dao.getProviderChannelIds(
        sourceId = sourceId.value,
        categoryId = categoryId,
        uncategorizedCategoryId = ProviderLiveOrganizationContract.UNCATEGORIZED_CATEGORY_ID,
    )

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
            channels = orderedChannels,
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
