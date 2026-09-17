package app.ownplay.mobile.feature.live.data

import androidx.room.withTransaction
import app.ownplay.mobile.data.db.CatalogDao
import app.ownplay.mobile.data.db.LiveOrganizationDao
import app.ownplay.mobile.data.db.OwnPlayDatabase
import app.ownplay.mobile.data.db.OwnPlayLiveCategoryEntity
import app.ownplay.mobile.data.db.OwnPlayLiveChannelMembershipEntity
import app.ownplay.mobile.feature.live.domain.LiveChannelMembership
import app.ownplay.mobile.feature.live.domain.LiveOrganizationEvidencePolicy
import app.ownplay.mobile.feature.live.domain.LiveOrganizationMode
import app.ownplay.mobile.feature.live.domain.LiveOrganizationOrigin
import app.ownplay.mobile.feature.live.domain.LiveOrganizationSnapshot
import app.ownplay.mobile.feature.live.domain.LiveOwnPlayManualEditPolicy
import app.ownplay.mobile.feature.live.domain.LiveOwnPlayDiscoveryChannel
import app.ownplay.mobile.feature.live.domain.LiveOwnPlayDiscoveryProviderCategory
import app.ownplay.mobile.feature.live.domain.LiveOwnPlayDiscoveryPolicy

internal class LiveOwnPlayDiscoveryCoordinator(
    private val database: OwnPlayDatabase,
    private val catalogDao: CatalogDao = database.catalogDao(),
    private val organizationDao: LiveOrganizationDao = database.liveOrganizationDao(),
) {
    suspend fun refresh(sourceId: String, generation: Long) {
        if (sourceId.isBlank() || generation <= 0L) return

        val categoryRows = catalogDao.getAvailableCategoriesForRefresh(sourceId, LIVE_KIND)
        val categoryById = categoryRows.associateBy { it.categoryKey }
        val channels = catalogDao.getLiveChannelsForRefresh(sourceId)
            .asSequence()
            .filter { channel -> channel.available }
            .sortedWith(
                compareBy(
                    { channel -> categoryById[channel.categoryKey]?.providerOrder ?: Int.MAX_VALUE },
                    { channel -> channel.categoryKey.orEmpty() },
                    { channel -> channel.providerOrder },
                    { channel -> channel.name.lowercase() },
                    { channel -> channel.channelId },
                ),
            )
            .map { channel ->
                LiveOwnPlayDiscoveryChannel(
                    channelId = channel.channelId,
                    providerCategoryName = channel.categoryKey?.let(categoryById::get)?.name,
                    providerCategoryId = channel.categoryKey,
                    name = channel.name,
                    tvgName = channel.tvgName,
                    tvgId = channel.tvgId,
                    hasLogo = !channel.logoUrl.isNullOrBlank(),
                )
            }
            .toList()

        val manualProfileSnapshot = LiveOrganizationSnapshot(
            sourceId = sourceId,
            activeMode = LiveOrganizationMode.OWNPLAY,
            memberships = organizationDao.getManualOwnPlayMemberships(sourceId).map { row ->
                LiveChannelMembership(
                    sourceId = row.sourceId,
                    mode = LiveOrganizationMode.OWNPLAY,
                    categoryId = row.categoryId,
                    channelId = row.channelId,
                    included = row.included,
                    origin = LiveOrganizationOrigin.MANUAL,
                    evidenceKeys = LiveOrganizationEvidencePolicy.decodeJsonArray(row.evidenceJson),
                )
            },
        )
        val profile = LiveOwnPlayManualEditPolicy.discoveryProfile(manualProfileSnapshot)
        val refreshConstraints = LiveOwnPlayManualEditPolicy.refreshConstraints(manualProfileSnapshot)
        val result = LiveOwnPlayDiscoveryPolicy.discover(
            channels = channels,
            profile = profile,
            providerCategoryCatalog = categoryRows.map { category ->
                LiveOwnPlayDiscoveryProviderCategory(
                    categoryId = category.categoryKey,
                    name = category.name,
                    providerOrder = category.providerOrder,
                )
            },
        )
        val categories = result.categories.map { category ->
            OwnPlayLiveCategoryEntity(
                sourceId = sourceId,
                categoryId = category.categoryId,
                parentCategoryId = category.parentCategoryId,
                displayName = category.displayName,
                semanticKey = category.semanticKey,
                origin = LiveOrganizationOrigin.AUTO.name,
                available = true,
                lastSeenGeneration = generation,
            )
        }
        val memberships = result.memberships.map { membership ->
            OwnPlayLiveChannelMembershipEntity(
                sourceId = sourceId,
                categoryId = membership.categoryId,
                channelId = membership.channelId,
                included = true,
                origin = LiveOrganizationOrigin.AUTO.name,
                confidence = membership.confidence.name,
                evidenceJson = LiveOrganizationEvidencePolicy.encodeJsonArray(membership.evidenceKeys),
                available = true,
                lastSeenGeneration = generation,
            )
        }

        database.withTransaction {
            val state = database.refreshStateDao().get(sourceId)
            if (state?.generation != generation || state.state != "SUCCESS") return@withTransaction

            val manualCategoryIds = organizationDao.getManualOwnPlayCategoryIds(sourceId).toSet()
            val manualMembershipKeys = organizationDao.getManualOwnPlayMembershipKeys(sourceId)
                .mapTo(hashSetOf()) { row -> row.categoryId to row.channelId }
            val autoMemberships = memberships
                .filter { row ->
                    LiveOwnPlayManualEditPolicy.allowsAutomaticMembership(
                        channelId = row.channelId,
                        categoryId = row.categoryId,
                        discoveredCategories = result.categories,
                        constraints = refreshConstraints,
                    )
                }
                .filterNot { row -> (row.categoryId to row.channelId) in manualMembershipKeys }
            val autoMembershipCategoryIds = autoMemberships.mapTo(hashSetOf()) { row -> row.categoryId }
            val autoCategories = categories
                .filterNot { row -> row.categoryId in manualCategoryIds }
                .filter { row -> row.categoryId in autoMembershipCategoryIds }

            if (autoCategories.isNotEmpty()) organizationDao.upsertOwnPlayCategories(autoCategories)
            if (autoMemberships.isNotEmpty()) organizationDao.upsertOwnPlayMemberships(autoMemberships)
            organizationDao.markMissingAutoOwnPlayMembershipsUnavailable(sourceId, generation)
            organizationDao.markMissingAutoOwnPlayCategoriesUnavailable(sourceId, generation)
        }
    }


    private companion object {
        const val LIVE_KIND = "LIVE"
    }
}
