package app.ownplay.mobile.feature.live.data

import androidx.room.withTransaction
import app.ownplay.mobile.data.db.BackupGroupMembershipView
import app.ownplay.mobile.data.db.CatalogDao
import app.ownplay.mobile.data.db.CategoryPersonalizationEntity
import app.ownplay.mobile.data.db.ChannelPersonalizationEntity
import app.ownplay.mobile.data.db.CustomGroupEntity
import app.ownplay.mobile.data.db.CustomGroupMembershipEntity
import app.ownplay.mobile.data.db.ManageableLiveChannelView
import app.ownplay.mobile.data.db.OwnPlayDatabase
import app.ownplay.mobile.data.db.SourceDao
import app.ownplay.mobile.data.security.CredentialStore
import app.ownplay.mobile.feature.live.domain.LiveCatalog
import app.ownplay.mobile.feature.live.domain.LiveCustomGroup
import app.ownplay.mobile.feature.live.domain.LiveCustomGroupPolicy
import app.ownplay.mobile.feature.live.domain.LiveGuidePolicy
import app.ownplay.mobile.feature.live.domain.LiveManagementCatalog
import app.ownplay.mobile.feature.live.domain.LiveManagementSource
import app.ownplay.mobile.feature.live.domain.LiveNowNext
import app.ownplay.mobile.feature.live.domain.LiveProgram
import app.ownplay.mobile.feature.live.domain.LiveCategory
import app.ownplay.mobile.feature.live.domain.LiveChannel
import app.ownplay.mobile.feature.live.domain.LivePlaybackResolution
import app.ownplay.mobile.feature.live.domain.LivePersonalizationPolicy
import app.ownplay.mobile.feature.live.domain.ManageableLiveCategory
import app.ownplay.mobile.feature.live.domain.ManageableLiveChannel
import app.ownplay.mobile.feature.live.domain.LiveRepository
import app.ownplay.mobile.feature.live.domain.ResolvedLivePlayback
import app.ownplay.mobile.playback.domain.PlaybackStreamFormat
import app.ownplay.mobile.sources.data.xtream.XtreamClient
import app.ownplay.mobile.sources.data.xtream.XtreamResult
import app.ownplay.mobile.sources.data.xtream.XtreamUrlBuilder
import app.ownplay.mobile.sources.domain.ProviderCategoryVisibility
import app.ownplay.mobile.sources.domain.SourceCredential
import app.ownplay.mobile.sources.domain.SourceRepository
import app.ownplay.mobile.sources.domain.SourceType
import java.net.URI
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class LiveRepositoryImpl(
    private val database: OwnPlayDatabase,
    private val sourceRepository: SourceRepository,
    private val sourceDao: SourceDao,
    private val catalogDao: CatalogDao,
    private val credentialStore: CredentialStore,
    private val xtreamClient: XtreamClient,
) : LiveRepository {
    private data class GuideCacheEntry(
        val loadedAtMs: Long,
        val programs: List<LiveProgram>,
    )

    private val guideCache = ConcurrentHashMap<String, GuideCacheEntry>()
    private val backupDao = database.backupDao()

    private fun ManageableLiveChannelView.toManageableLiveChannel() = ManageableLiveChannel(
        channelId = channelId,
        sourceId = sourceId,
        categoryKey = categoryKey,
        name = name,
        logoUrl = logoUrl,
        providerOrder = providerOrder,
        favorite = favorite,
        localName = localName,
        localLogo = localLogo,
        hidden = hidden,
        manualOrder = manualOrder,
    )

    private fun mapManageableChannels(rows: List<ManageableLiveChannelView>): List<ManageableLiveChannel> = rows
        .filterNot { row -> ProviderCategoryVisibility.isUtilityLabel(row.name) }
        .map { row -> row.toManageableLiveChannel() }

    private fun mapCustomGroups(
        groups: List<CustomGroupEntity>,
        memberships: List<BackupGroupMembershipView>,
    ): List<LiveCustomGroup> {
        val channelIdsByGroup = memberships
            .groupBy { row -> row.groupId }
            .mapValues { (_, rows) -> rows.sortedBy { it.manualOrder }.map { it.channelId } }
        return groups.map { group ->
            LiveCustomGroup(
                groupId = group.groupId,
                sourceId = group.sourceId,
                name = group.name,
                manualOrder = group.manualOrder,
                channelIds = channelIdsByGroup[group.groupId].orEmpty(),
            )
        }
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCatalog(): Flow<LiveCatalog> =
        sourceRepository.observeActiveSource().flatMapLatest { source ->
            if (source == null) {
                flowOf(LiveCatalog())
            } else {
                combine(
                    catalogDao.observeAvailableCategories(source.sourceId, "LIVE"),
                    catalogDao.observeAvailableLiveChannels(source.sourceId),
                    backupDao.observeCustomGroups(source.sourceId),
                    backupDao.observeCustomGroupMemberships(source.sourceId),
                ) { categoryRows, channelRows, groupRows, membershipRows ->
                    LiveCatalog(
                        activeSourceId = source.sourceId,
                        activeSourceName = source.displayName,
                        categories = categoryRows.map { row ->
                            LiveCategory(
                                categoryKey = row.categoryKey,
                                name = row.name,
                                providerOrder = row.providerOrder,
                            )
                        },
                        channels = channelRows
                            .filterNot { row -> ProviderCategoryVisibility.isUtilityLabel(row.name) }
                            .map { row ->
                                LiveChannel(
                                    channelId = row.channelId,
                                    sourceId = row.sourceId,
                                    categoryKey = row.categoryKey,
                                    name = row.name,
                                    logoUrl = row.logoUrl,
                                    sortOrder = row.sortOrder,
                                    favorite = row.favorite,
                                )
                            },
                        customGroups = mapCustomGroups(groupRows, membershipRows),
                    )
                }
            }
        }

    override fun observeManagementSource(): Flow<LiveManagementSource> =
        sourceRepository.observeActiveSource().map { source ->
            LiveManagementSource(
                sourceId = source?.sourceId,
                sourceName = source?.displayName,
            )
        }

    override fun observeOwnPlayManageableChannels(
        sourceId: String,
        categoryId: String,
    ): Flow<List<ManageableLiveChannel>> {
        if (sourceId.isBlank() || categoryId.isBlank()) return flowOf(emptyList())
        return catalogDao.observeOwnPlayManageableLiveChannels(sourceId, categoryId)
            .map(::mapManageableChannels)
    }

    override fun searchManageableChannels(
        sourceId: String,
        query: String,
    ): Flow<List<ManageableLiveChannel>> {
        val normalizedQuery = query.trim()
        if (sourceId.isBlank() || normalizedQuery.isBlank()) return flowOf(emptyList())
        return catalogDao.searchManageableLiveChannels(sourceId, normalizedQuery)
            .map(::mapManageableChannels)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeManagementCatalog(): Flow<LiveManagementCatalog> =
        sourceRepository.observeActiveSource().flatMapLatest { source ->
            if (source == null) {
                flowOf(LiveManagementCatalog())
            } else {
                combine(
                    catalogDao.observeManageableLiveCategories(source.sourceId),
                    catalogDao.observeManageableLiveChannels(source.sourceId),
                    backupDao.observeCustomGroups(source.sourceId),
                    backupDao.observeCustomGroupMemberships(source.sourceId),
                ) { categoryRows, channelRows, groupRows, membershipRows ->
                    LiveManagementCatalog(
                        activeSourceId = source.sourceId,
                        activeSourceName = source.displayName,
                        categories = categoryRows
                            .filterNot { ProviderCategoryVisibility.isUtilityLabel(it.name) }
                            .map { row ->
                                ManageableLiveCategory(
                                    sourceId = row.sourceId,
                                    categoryKey = row.categoryKey,
                                    name = row.name,
                                    providerOrder = row.providerOrder,
                                    hidden = row.hidden,
                                    manualOrder = row.manualOrder,
                                )
                            },
                        channels = mapManageableChannels(channelRows),
                        customGroups = mapCustomGroups(groupRows, membershipRows),
                    )
                }
            }
        }

    override suspend fun setCategoryHidden(sourceId: String, categoryKey: String, hidden: Boolean) {
        if (sourceId.isBlank() || categoryKey.isBlank()) return
        database.withTransaction {
            val current = catalogDao.getCategoryPersonalization(sourceId, LIVE_KIND, categoryKey)
            catalogDao.upsertCategoryPersonalization(
                (current ?: CategoryPersonalizationEntity(sourceId, LIVE_KIND, categoryKey)).copy(hidden = hidden),
            )
        }
    }

    override suspend fun setChannelHidden(channelId: String, hidden: Boolean) {
        if (channelId.isBlank()) return
        database.withTransaction {
            if (catalogDao.getLiveChannel(channelId) == null) return@withTransaction
            val current = catalogDao.getChannelPersonalization(channelId)
            catalogDao.upsertChannelPersonalization(
                (current ?: ChannelPersonalizationEntity(channelId = channelId)).copy(hidden = hidden),
            )
        }
    }

    override suspend fun setChannelFavorite(channelId: String, favorite: Boolean) {
        if (channelId.isBlank()) return
        database.withTransaction {
            if (catalogDao.getLiveChannel(channelId) == null) return@withTransaction
            val current = catalogDao.getChannelPersonalization(channelId)
            catalogDao.upsertChannelPersonalization(
                (current ?: ChannelPersonalizationEntity(channelId = channelId)).copy(favorite = favorite),
            )
        }
    }

    override suspend fun setChannelLocalName(channelId: String, localName: String?) {
        if (channelId.isBlank()) return
        val normalized = LivePersonalizationPolicy.normalizeLocalName(localName)
        database.withTransaction {
            if (catalogDao.getLiveChannel(channelId) == null) return@withTransaction
            val current = catalogDao.getChannelPersonalization(channelId)
            catalogDao.upsertChannelPersonalization(
                (current ?: ChannelPersonalizationEntity(channelId = channelId)).copy(localName = normalized),
            )
        }
    }

    override suspend fun setChannelLocalLogo(channelId: String, localLogo: String?) {
        if (channelId.isBlank()) return
        val normalized = LivePersonalizationPolicy.normalizeLocalLogo(localLogo)
        if (!localLogo.isNullOrBlank() && normalized == null) return
        database.withTransaction {
            if (catalogDao.getLiveChannel(channelId) == null) return@withTransaction
            val current = catalogDao.getChannelPersonalization(channelId)
            catalogDao.upsertChannelPersonalization(
                (current ?: ChannelPersonalizationEntity(channelId = channelId)).copy(localLogo = normalized),
            )
        }
    }

    override suspend fun setCategoryOrder(sourceId: String, orderedCategoryKeys: List<String>) {
        if (sourceId.isBlank()) return
        val ordered = orderedCategoryKeys.distinct().filter { it.isNotBlank() }
        database.withTransaction {
            ordered.forEachIndexed { index, categoryKey ->
                val current = catalogDao.getCategoryPersonalization(sourceId, LIVE_KIND, categoryKey)
                catalogDao.upsertCategoryPersonalization(
                    (current ?: CategoryPersonalizationEntity(sourceId, LIVE_KIND, categoryKey))
                        .copy(manualOrder = index),
                )
            }
        }
    }

    override suspend fun setChannelOrder(orderedChannelIds: List<String>) {
        val ordered = orderedChannelIds.distinct().filter { it.isNotBlank() }
        database.withTransaction {
            ordered.forEachIndexed { index, channelId ->
                if (catalogDao.getLiveChannel(channelId) == null) return@forEachIndexed
                val current = catalogDao.getChannelPersonalization(channelId)
                catalogDao.upsertChannelPersonalization(
                    (current ?: ChannelPersonalizationEntity(channelId = channelId)).copy(manualOrder = index),
                )
            }
        }
    }

    override suspend fun resetCategoryOrder(sourceId: String, categoryKeys: List<String>) {
        if (sourceId.isBlank()) return
        val keys = categoryKeys.distinct().filter { it.isNotBlank() }
        database.withTransaction {
            keys.forEach { categoryKey ->
                val current = catalogDao.getCategoryPersonalization(sourceId, LIVE_KIND, categoryKey)
                    ?: return@forEach
                if (current.manualOrder != null) {
                    catalogDao.upsertCategoryPersonalization(current.copy(manualOrder = null))
                }
            }
        }
    }

    override suspend fun resetChannelOrder(channelIds: List<String>) {
        val ids = channelIds.distinct().filter { it.isNotBlank() }
        database.withTransaction {
            ids.forEach { channelId ->
                val current = catalogDao.getChannelPersonalization(channelId) ?: return@forEach
                if (current.manualOrder != null) {
                    catalogDao.upsertChannelPersonalization(current.copy(manualOrder = null))
                }
            }
        }
    }

    override suspend fun createCustomGroup(sourceId: String, name: String) {
        if (sourceId.isBlank()) return
        val normalizedName = LiveCustomGroupPolicy.normalizeName(name) ?: return
        database.withTransaction {
            if (sourceDao.get(sourceId) == null) return@withTransaction
            val nextOrder = backupDao.getCustomGroupsForSource(sourceId)
                .maxOfOrNull { group -> group.manualOrder }
                ?.plus(1)
                ?: 0
            backupDao.upsertCustomGroups(
                listOf(
                    CustomGroupEntity(
                        groupId = "local-group:${UUID.randomUUID()}",
                        sourceId = sourceId,
                        name = normalizedName,
                        manualOrder = nextOrder,
                    ),
                ),
            )
        }
    }

    override suspend fun renameCustomGroup(groupId: String, name: String) {
        if (groupId.isBlank()) return
        val normalizedName = LiveCustomGroupPolicy.normalizeName(name) ?: return
        database.withTransaction {
            val group = backupDao.getCustomGroup(groupId) ?: return@withTransaction
            backupDao.upsertCustomGroups(listOf(group.copy(name = normalizedName)))
        }
    }

    override suspend fun setCustomGroupMembership(groupId: String, channelId: String, included: Boolean) {
        if (groupId.isBlank() || channelId.isBlank()) return
        database.withTransaction {
            val group = backupDao.getCustomGroup(groupId) ?: return@withTransaction
            if (!backupDao.hasLiveChannel(group.sourceId, channelId)) return@withTransaction
            val memberships = backupDao.getCustomGroupMembershipRows(groupId)
            val existing = memberships.firstOrNull { row -> row.channelId == channelId }
            if (included) {
                if (existing != null) return@withTransaction
                val nextOrder = memberships.maxOfOrNull { row -> row.manualOrder }?.plus(1) ?: 0
                backupDao.upsertCustomGroupMembership(
                    CustomGroupMembershipEntity(
                        groupId = groupId,
                        channelId = channelId,
                        manualOrder = nextOrder,
                    ),
                )
            } else if (existing != null) {
                backupDao.deleteCustomGroupMembership(groupId, channelId)
            }
        }
    }

    override suspend fun loadNowNext(channelId: String): LiveNowNext {
        if (channelId.isBlank()) return LiveNowNext()
        val nowMs = System.currentTimeMillis()
        val cachedEntry = guideCache[channelId]
        val cachedPrograms = cachedEntry
            ?.takeIf { nowMs - it.loadedAtMs < GUIDE_CACHE_TTL_MS }
            ?.programs
        if (cachedPrograms != null) {
            return LiveGuidePolicy.nowNext(cachedPrograms, nowMs / 1_000L)
        }

        val loadedPrograms = try {
            val channel = catalogDao.getLiveChannel(channelId) ?: return LiveNowNext()
            val source = sourceDao.get(channel.sourceId) ?: return LiveNowNext()
            if (!channel.available || !source.enabled || source.type != SourceType.XTREAM.name) {
                return LiveNowNext()
            }
            val streamId = channel.providerStreamId ?: return LiveNowNext()
            val credential = credentialStore.get(source.sourceId) as? SourceCredential.Xtream
                ?: return LiveNowNext()
            when (val result = xtreamClient.shortEpg(source.baseLocator, credential, streamId, limit = 4)) {
                is XtreamResult.Failure -> null
                is XtreamResult.Success -> result.value.map { entry ->
                    LiveProgram(
                        title = entry.title.trim(),
                        startEpochSeconds = entry.startEpochSeconds,
                        endEpochSeconds = entry.endEpochSeconds,
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (loadedPrograms != null) {
            guideCache[channelId] = GuideCacheEntry(nowMs, loadedPrograms)
        }
        val programs = loadedPrograms ?: cachedEntry?.programs.orEmpty()
        return LiveGuidePolicy.nowNext(programs, nowMs / 1_000L)
    }

    override suspend fun resolvePlayback(channelId: String): LivePlaybackResolution {
        if (channelId.isBlank()) return failure("INVALID_CHANNEL", "This channel cannot be opened.")

        return try {
            val channel = catalogDao.getLiveChannel(channelId)
                ?: return failure("CHANNEL_NOT_FOUND", "This channel is no longer available.")
            if (!channel.available) return failure("CHANNEL_UNAVAILABLE", "This channel is currently unavailable.")

            val source = sourceDao.get(channel.sourceId)
                ?: return failure("SOURCE_NOT_FOUND", "The channel source is no longer available.")
            if (!source.enabled) return failure("SOURCE_DISABLED", "The channel source is disabled.")

            var fallbackUri: String? = null
            var fallbackFormat: PlaybackStreamFormat? = null
            val uri = when (source.type) {
                SourceType.XTREAM.name -> {
                    val providerId = channel.providerStreamId
                        ?: return failure("STREAM_ID_MISSING", "This channel has no playable stream id.")
                    val credential = credentialStore.get(source.sourceId) as? SourceCredential.Xtream
                        ?: return failure("CREDENTIAL_MISSING", "Source credentials are unavailable.")
                    fallbackUri = XtreamUrlBuilder.streamUrl(
                        baseUrl = source.baseLocator,
                        credential = credential,
                        kind = "live",
                        providerId = providerId,
                        extension = "m3u8",
                    )
                    fallbackFormat = PlaybackStreamFormat.HLS
                    XtreamUrlBuilder.streamUrl(
                        baseUrl = source.baseLocator,
                        credential = credential,
                        kind = "live",
                        providerId = providerId,
                        extension = "ts",
                    )
                }

                SourceType.M3U.name -> channel.streamLocator
                else -> return failure("SOURCE_TYPE_UNSUPPORTED", "This source type is not supported.")
            }

            val personalization = catalogDao.getChannelPersonalization(channel.channelId)
            LivePlaybackResolution.Success(
                ResolvedLivePlayback(
                    channel = LiveChannel(
                        channelId = channel.channelId,
                        sourceId = channel.sourceId,
                        categoryKey = channel.categoryKey,
                        name = personalization?.localName ?: channel.name,
                        logoUrl = personalization?.localLogo ?: channel.logoUrl,
                        sortOrder = personalization?.manualOrder ?: channel.providerOrder,
                        favorite = personalization?.favorite ?: false,
                    ),
                    uri = uri,
                    streamFormat = streamFormatFor(uri),
                    fallbackUri = fallbackUri,
                    fallbackStreamFormat = fallbackFormat,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failure("STREAM_RESOLUTION_FAILED", "The channel stream could not be prepared.")
        }
    }

    private fun streamFormatFor(uri: String): PlaybackStreamFormat {
        val normalizedPath = runCatching { URI(uri).path.orEmpty() }
            .getOrDefault(uri.substringBefore('?'))
            .lowercase(Locale.US)
        return if (normalizedPath.endsWith(".m3u8")) PlaybackStreamFormat.HLS else PlaybackStreamFormat.AUTO
    }

    private fun failure(code: String, message: String): LivePlaybackResolution.Failure =
        LivePlaybackResolution.Failure(code = code, safeMessage = message)

    private companion object {
        const val GUIDE_CACHE_TTL_MS = 120_000L
        const val LIVE_KIND = "LIVE"
    }
}
