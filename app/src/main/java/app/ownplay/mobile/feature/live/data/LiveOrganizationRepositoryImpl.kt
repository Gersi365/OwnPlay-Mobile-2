package app.ownplay.mobile.feature.live.data

import androidx.room.withTransaction
import app.ownplay.mobile.data.db.CatalogDao
import app.ownplay.mobile.data.db.CategoryPersonalizationEntity
import app.ownplay.mobile.data.db.ChannelPersonalizationEntity
import app.ownplay.mobile.data.db.LiveCategoryScopePersonalizationEntity
import app.ownplay.mobile.data.db.LiveChannelMembershipPersonalizationEntity
import app.ownplay.mobile.data.db.LiveOrganizationDao
import app.ownplay.mobile.data.db.LiveOrganizationPreferenceEntity
import app.ownplay.mobile.data.db.OwnPlayDatabase
import app.ownplay.mobile.data.db.SourceDao
import app.ownplay.mobile.feature.live.domain.LiveCategoryPersonalizationKey
import app.ownplay.mobile.feature.live.domain.LiveCategoryScope
import app.ownplay.mobile.feature.live.domain.LiveChannelMembership
import app.ownplay.mobile.feature.live.domain.LiveChannelMembershipPersonalizationKey
import app.ownplay.mobile.feature.live.domain.LiveChannelMembershipScope
import app.ownplay.mobile.feature.live.domain.LiveClassificationConfidence
import app.ownplay.mobile.feature.live.domain.LiveOrganizationCategory
import app.ownplay.mobile.feature.live.domain.LiveOrganizationEvidencePolicy
import app.ownplay.mobile.feature.live.domain.LiveOrganizationMode
import app.ownplay.mobile.feature.live.domain.LiveOrganizationOrigin
import app.ownplay.mobile.feature.live.domain.LiveOrganizationRepository
import app.ownplay.mobile.feature.live.domain.LiveOrganizationScopePolicy
import app.ownplay.mobile.feature.live.domain.LiveOrganizationSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class LiveOrganizationRepositoryImpl(
    private val database: OwnPlayDatabase,
    private val sourceDao: SourceDao = database.sourceDao(),
    private val catalogDao: CatalogDao = database.catalogDao(),
    private val organizationDao: LiveOrganizationDao = database.liveOrganizationDao(),
) : LiveOrganizationRepository {
    override fun observeOrganization(sourceId: String): Flow<LiveOrganizationSnapshot> {
        require(sourceId.isNotBlank()) { "sourceId must not be blank" }
        return combine(
            organizationDao.observePreference(sourceId),
            organizationDao.observeProviderCategories(sourceId),
            organizationDao.observeProviderMemberships(
                sourceId,
                LiveOrganizationScopePolicy.PROVIDER_UNCATEGORIZED_CATEGORY_ID,
            ),
            organizationDao.observeOwnPlayCategoryViews(sourceId),
            organizationDao.observeOwnPlayMembershipViews(sourceId),
        ) { preference, providerCategories, providerMemberships, ownPlayCategories, ownPlayMemberships ->
            LiveOrganizationSnapshot(
                sourceId = sourceId,
                activeMode = preference?.activeMode.toOrganizationMode(),
                categories = buildList {
                    providerCategories.forEach { row ->
                        add(
                            LiveOrganizationCategory(
                                sourceId = row.sourceId,
                                mode = LiveOrganizationMode.PROVIDER,
                                categoryId = row.categoryId,
                                displayName = row.displayName,
                                origin = LiveOrganizationOrigin.PROVIDER,
                                hidden = row.hidden,
                                manualOrder = row.manualOrder,
                            ),
                        )
                    }
                    ownPlayCategories.forEach { row ->
                        add(
                            LiveOrganizationCategory(
                                sourceId = row.sourceId,
                                mode = LiveOrganizationMode.OWNPLAY,
                                categoryId = row.categoryId,
                                parentCategoryId = row.parentCategoryId,
                                displayName = row.displayName,
                                semanticKey = row.semanticKey,
                                origin = row.origin.toOwnPlayOrigin(),
                                hidden = row.hidden,
                                manualOrder = row.manualOrder,
                            ),
                        )
                    }
                },
                memberships = buildList {
                    providerMemberships.forEach { row ->
                        add(
                            LiveChannelMembership(
                                sourceId = row.sourceId,
                                mode = LiveOrganizationMode.PROVIDER,
                                categoryId = row.categoryId,
                                channelId = row.channelId,
                                origin = LiveOrganizationOrigin.PROVIDER,
                                hidden = row.hidden,
                                manualOrder = row.manualOrder,
                            ),
                        )
                    }
                    ownPlayMemberships.forEach { row ->
                        add(
                            LiveChannelMembership(
                                sourceId = row.sourceId,
                                mode = LiveOrganizationMode.OWNPLAY,
                                categoryId = row.categoryId,
                                channelId = row.channelId,
                                included = row.included,
                                origin = row.origin.toOwnPlayOrigin(),
                                confidence = row.confidence.toClassificationConfidence(),
                                evidenceKeys = LiveOrganizationEvidencePolicy.decodeJsonArray(row.evidenceJson),
                                hidden = row.hidden,
                                manualOrder = row.manualOrder,
                            ),
                        )
                    }
                },
            )
        }
    }

    override suspend fun setActiveMode(sourceId: String, mode: LiveOrganizationMode) {
        if (sourceId.isBlank()) return
        database.withTransaction {
            if (sourceDao.get(sourceId) == null) return@withTransaction
            if (
                mode == LiveOrganizationMode.OWNPLAY &&
                organizationDao.countOwnPlayCategories(sourceId) == 0
            ) {
                return@withTransaction
            }
            organizationDao.upsertPreference(
                LiveOrganizationPreferenceEntity(
                    sourceId = sourceId,
                    activeMode = mode.name,
                ),
            )
        }
    }

    override suspend fun setCategoryHidden(key: LiveCategoryPersonalizationKey, hidden: Boolean) {
        database.withTransaction {
            if (!categoryExists(key)) return@withTransaction
            val current = organizationDao.getCategoryPersonalization(
                key.sourceId,
                key.mode.name,
                key.categoryId,
            )
            organizationDao.upsertCategoryPersonalization(
                (current ?: LiveCategoryScopePersonalizationEntity(
                    sourceId = key.sourceId,
                    organizationMode = key.mode.name,
                    categoryId = key.categoryId,
                )).copy(hidden = hidden),
            )
            if (key.mode == LiveOrganizationMode.PROVIDER) {
                mirrorProviderCategoryHidden(key.sourceId, key.categoryId, hidden)
            }
        }
    }

    override suspend fun setCategoryOrder(scope: LiveCategoryScope, orderedCategoryIds: List<String>) {
        val ordered = validatedOrder(orderedCategoryIds) ?: return
        database.withTransaction {
            val allowedIds = categoryIds(scope).toSet()
            if (!allowedIds.containsAll(ordered)) return@withTransaction
            ordered.forEachIndexed { index, categoryId ->
                val current = organizationDao.getCategoryPersonalization(
                    scope.sourceId,
                    scope.mode.name,
                    categoryId,
                )
                organizationDao.upsertCategoryPersonalization(
                    (current ?: LiveCategoryScopePersonalizationEntity(
                        sourceId = scope.sourceId,
                        organizationMode = scope.mode.name,
                        categoryId = categoryId,
                    )).copy(manualOrder = index),
                )
                if (scope.mode == LiveOrganizationMode.PROVIDER) {
                    mirrorProviderCategoryOrder(scope.sourceId, categoryId, index)
                }
            }
        }
    }

    override suspend fun resetCategoryOrder(scope: LiveCategoryScope) {
        database.withTransaction {
            categoryIds(scope).forEach { categoryId ->
                val current = organizationDao.getCategoryPersonalization(
                    scope.sourceId,
                    scope.mode.name,
                    categoryId,
                )
                if (current?.manualOrder != null) {
                    organizationDao.upsertCategoryPersonalization(current.copy(manualOrder = null))
                }
                if (scope.mode == LiveOrganizationMode.PROVIDER) {
                    mirrorProviderCategoryOrder(scope.sourceId, categoryId, null)
                }
            }
        }
    }

    override suspend fun setChannelHidden(
        key: LiveChannelMembershipPersonalizationKey,
        hidden: Boolean,
    ) {
        database.withTransaction {
            if (!channelMembershipExists(key)) return@withTransaction
            val current = organizationDao.getChannelMembershipPersonalization(
                key.sourceId,
                key.mode.name,
                key.categoryId,
                key.channelId,
            )
            organizationDao.upsertChannelMembershipPersonalization(
                (current ?: LiveChannelMembershipPersonalizationEntity(
                    sourceId = key.sourceId,
                    organizationMode = key.mode.name,
                    categoryId = key.categoryId,
                    channelId = key.channelId,
                )).copy(hidden = hidden),
            )
            if (key.mode == LiveOrganizationMode.PROVIDER) {
                mirrorProviderChannelHidden(key.channelId, hidden)
            }
        }
    }

    override suspend fun setChannelOrder(
        scope: LiveChannelMembershipScope,
        orderedChannelIds: List<String>,
    ) {
        val ordered = validatedOrder(orderedChannelIds) ?: return
        database.withTransaction {
            val allowedIds = channelIds(scope).toSet()
            if (!allowedIds.containsAll(ordered)) return@withTransaction
            ordered.forEachIndexed { index, channelId ->
                val current = organizationDao.getChannelMembershipPersonalization(
                    scope.sourceId,
                    scope.mode.name,
                    scope.categoryId,
                    channelId,
                )
                organizationDao.upsertChannelMembershipPersonalization(
                    (current ?: LiveChannelMembershipPersonalizationEntity(
                        sourceId = scope.sourceId,
                        organizationMode = scope.mode.name,
                        categoryId = scope.categoryId,
                        channelId = channelId,
                    )).copy(manualOrder = index),
                )
                if (scope.mode == LiveOrganizationMode.PROVIDER) {
                    mirrorProviderChannelOrder(channelId, index)
                }
            }
        }
    }

    override suspend fun resetChannelOrder(scope: LiveChannelMembershipScope) {
        database.withTransaction {
            channelIds(scope).forEach { channelId ->
                val current = organizationDao.getChannelMembershipPersonalization(
                    scope.sourceId,
                    scope.mode.name,
                    scope.categoryId,
                    channelId,
                )
                if (current?.manualOrder != null) {
                    organizationDao.upsertChannelMembershipPersonalization(current.copy(manualOrder = null))
                }
                if (scope.mode == LiveOrganizationMode.PROVIDER) {
                    mirrorProviderChannelOrder(channelId, null)
                }
            }
        }
    }

    private suspend fun categoryExists(key: LiveCategoryPersonalizationKey): Boolean = when (key.mode) {
        LiveOrganizationMode.PROVIDER ->
            organizationDao.countProviderCategory(key.sourceId, key.categoryId) > 0
        LiveOrganizationMode.OWNPLAY ->
            organizationDao.countOwnPlayCategory(key.sourceId, key.categoryId) > 0
    }

    private suspend fun categoryIds(scope: LiveCategoryScope): List<String> = when (scope.mode) {
        LiveOrganizationMode.PROVIDER -> {
            if (scope.parentCategoryId != null) emptyList()
            else organizationDao.getProviderCategoryIds(scope.sourceId)
        }
        LiveOrganizationMode.OWNPLAY ->
            organizationDao.getOwnPlaySiblingCategoryIds(scope.sourceId, scope.parentCategoryId)
    }

    private suspend fun channelMembershipExists(
        key: LiveChannelMembershipPersonalizationKey,
    ): Boolean = when (key.mode) {
        LiveOrganizationMode.PROVIDER -> organizationDao.countProviderChannelMembership(
            key.sourceId,
            key.categoryId,
            key.channelId,
            LiveOrganizationScopePolicy.PROVIDER_UNCATEGORIZED_CATEGORY_ID,
        ) > 0
        LiveOrganizationMode.OWNPLAY -> organizationDao.countOwnPlayChannelMembership(
            key.sourceId,
            key.categoryId,
            key.channelId,
        ) > 0
    }

    private suspend fun channelIds(scope: LiveChannelMembershipScope): List<String> = when (scope.mode) {
        LiveOrganizationMode.PROVIDER -> organizationDao.getProviderChannelIds(
            scope.sourceId,
            scope.categoryId,
            LiveOrganizationScopePolicy.PROVIDER_UNCATEGORIZED_CATEGORY_ID,
        )
        LiveOrganizationMode.OWNPLAY ->
            organizationDao.getOwnPlayChannelIds(scope.sourceId, scope.categoryId)
    }

    private suspend fun mirrorProviderCategoryHidden(
        sourceId: String,
        categoryId: String,
        hidden: Boolean,
    ) {
        val current = catalogDao.getCategoryPersonalization(sourceId, LIVE_KIND, categoryId)
        catalogDao.upsertCategoryPersonalization(
            (current ?: CategoryPersonalizationEntity(sourceId, LIVE_KIND, categoryId)).copy(hidden = hidden),
        )
    }

    private suspend fun mirrorProviderCategoryOrder(
        sourceId: String,
        categoryId: String,
        manualOrder: Int?,
    ) {
        val current = catalogDao.getCategoryPersonalization(sourceId, LIVE_KIND, categoryId)
        if (current == null && manualOrder == null) return
        catalogDao.upsertCategoryPersonalization(
            (current ?: CategoryPersonalizationEntity(sourceId, LIVE_KIND, categoryId))
                .copy(manualOrder = manualOrder),
        )
    }

    private suspend fun mirrorProviderChannelHidden(channelId: String, hidden: Boolean) {
        val current = catalogDao.getChannelPersonalization(channelId)
        catalogDao.upsertChannelPersonalization(
            (current ?: ChannelPersonalizationEntity(channelId = channelId)).copy(hidden = hidden),
        )
    }

    private suspend fun mirrorProviderChannelOrder(channelId: String, manualOrder: Int?) {
        val current = catalogDao.getChannelPersonalization(channelId)
        if (current == null && manualOrder == null) return
        catalogDao.upsertChannelPersonalization(
            (current ?: ChannelPersonalizationEntity(channelId = channelId))
                .copy(manualOrder = manualOrder),
        )
    }

    private fun validatedOrder(ids: List<String>): List<String>? {
        if (ids.isEmpty() || ids.any { it.isBlank() }) return null
        if (ids.distinct().size != ids.size) return null
        return ids
    }

    private fun String?.toOrganizationMode(): LiveOrganizationMode =
        runCatching { LiveOrganizationMode.valueOf(this.orEmpty()) }
            .getOrDefault(LiveOrganizationMode.PROVIDER)

    private fun String.toOwnPlayOrigin(): LiveOrganizationOrigin =
        runCatching { LiveOrganizationOrigin.valueOf(this) }
            .getOrNull()
            ?.takeUnless { it == LiveOrganizationOrigin.PROVIDER }
            ?: LiveOrganizationOrigin.AUTO

    private fun String?.toClassificationConfidence(): LiveClassificationConfidence? =
        runCatching { LiveClassificationConfidence.valueOf(this.orEmpty()) }.getOrNull()

    private companion object {
        const val LIVE_KIND = "LIVE"
    }
}
