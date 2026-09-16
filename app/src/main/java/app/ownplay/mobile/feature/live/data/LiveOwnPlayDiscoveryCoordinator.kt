package app.ownplay.mobile.feature.live.data

import androidx.room.withTransaction
import app.ownplay.mobile.data.db.CatalogDao
import app.ownplay.mobile.data.db.LiveOrganizationDao
import app.ownplay.mobile.data.db.OwnPlayDatabase
import app.ownplay.mobile.data.db.OwnPlayLiveCategoryEntity
import app.ownplay.mobile.data.db.OwnPlayLiveChannelMembershipEntity
import app.ownplay.mobile.feature.live.domain.LiveOrganizationOrigin
import app.ownplay.mobile.feature.live.domain.LiveOwnPlayDiscoveryChannel
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
                    name = channel.name,
                    tvgName = channel.tvgName,
                    tvgId = channel.tvgId,
                    hasLogo = !channel.logoUrl.isNullOrBlank(),
                )
            }
            .toList()

        val result = LiveOwnPlayDiscoveryPolicy.discover(channels)
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
                evidenceJson = evidenceJson(membership.evidenceKeys),
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
            val autoCategories = categories.filterNot { row -> row.categoryId in manualCategoryIds }
            val autoMemberships = memberships.filterNot { row ->
                (row.categoryId to row.channelId) in manualMembershipKeys
            }

            if (autoCategories.isNotEmpty()) organizationDao.upsertOwnPlayCategories(autoCategories)
            if (autoMemberships.isNotEmpty()) organizationDao.upsertOwnPlayMemberships(autoMemberships)
            organizationDao.markMissingAutoOwnPlayMembershipsUnavailable(sourceId, generation)
            organizationDao.markMissingAutoOwnPlayCategoriesUnavailable(sourceId, generation)
        }
    }

    private fun evidenceJson(values: Set<String>): String = values
        .sorted()
        .joinToString(prefix = "[", postfix = "]") { value ->
            "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        }

    private companion object {
        const val LIVE_KIND = "LIVE"
    }
}
