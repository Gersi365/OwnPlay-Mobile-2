package app.ownplay.mobile.feature.live.data

import androidx.room.withTransaction
import app.ownplay.mobile.data.db.CatalogDao
import app.ownplay.mobile.data.db.CategoryPersonalizationEntity
import app.ownplay.mobile.data.db.ChannelPersonalizationEntity
import app.ownplay.mobile.data.db.OwnPlayDatabase
import app.ownplay.mobile.data.db.SourceDao
import app.ownplay.mobile.data.security.CredentialStore
import app.ownplay.mobile.feature.live.domain.LiveCatalog
import app.ownplay.mobile.feature.live.domain.LiveGuidePolicy
import app.ownplay.mobile.feature.live.domain.LiveManagementCatalog
import app.ownplay.mobile.feature.live.domain.LiveNowNext
import app.ownplay.mobile.feature.live.domain.LiveProgram
import app.ownplay.mobile.feature.live.domain.LiveCategory
import app.ownplay.mobile.feature.live.domain.LiveChannel
import app.ownplay.mobile.feature.live.domain.LivePlaybackResolution
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
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

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
        val guide: LiveNowNext,
    )

    private val guideCache = ConcurrentHashMap<String, GuideCacheEntry>()
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCatalog(): Flow<LiveCatalog> =
        sourceRepository.observeActiveSource().flatMapLatest { source ->
            if (source == null) {
                flowOf(LiveCatalog())
            } else {
                combine(
                    catalogDao.observeAvailableCategories(source.sourceId, "LIVE"),
                    catalogDao.observeAvailableLiveChannels(source.sourceId),
                ) { categoryRows, channelRows ->
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
                            )
                        },
                    )
                }
            }
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
                ) { categoryRows, channelRows ->
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
                        channels = channelRows
                            .filterNot { ProviderCategoryVisibility.isUtilityLabel(it.name) }
                            .map { row ->
                                ManageableLiveChannel(
                                    channelId = row.channelId,
                                    sourceId = row.sourceId,
                                    categoryKey = row.categoryKey,
                                    name = row.name,
                                    logoUrl = row.logoUrl,
                                    providerOrder = row.providerOrder,
                                    hidden = row.hidden,
                                    manualOrder = row.manualOrder,
                                )
                            },
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

    override suspend fun loadNowNext(channelId: String): LiveNowNext {
        if (channelId.isBlank()) return LiveNowNext()
        val nowMs = System.currentTimeMillis()
        guideCache[channelId]
            ?.takeIf { nowMs - it.loadedAtMs < GUIDE_CACHE_TTL_MS }
            ?.let { return it.guide }

        val guide = try {
            val channel = catalogDao.getLiveChannel(channelId) ?: return LiveNowNext()
            val source = sourceDao.get(channel.sourceId) ?: return LiveNowNext()
            if (!channel.available || !source.enabled || source.type != SourceType.XTREAM.name) {
                return LiveNowNext()
            }
            val streamId = channel.providerStreamId ?: return LiveNowNext()
            val credential = credentialStore.get(source.sourceId) as? SourceCredential.Xtream
                ?: return LiveNowNext()
            when (val result = xtreamClient.shortEpg(source.baseLocator, credential, streamId, limit = 4)) {
                is XtreamResult.Failure -> LiveNowNext()
                is XtreamResult.Success -> LiveGuidePolicy.nowNext(
                    programs = result.value.map { entry ->
                        LiveProgram(
                            title = entry.title.trim(),
                            startEpochSeconds = entry.startEpochSeconds,
                            endEpochSeconds = entry.endEpochSeconds,
                        )
                    },
                    nowEpochSeconds = nowMs / 1_000L,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            LiveNowNext()
        }
        guideCache[channelId] = GuideCacheEntry(nowMs, guide)
        return guide
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

            LivePlaybackResolution.Success(
                ResolvedLivePlayback(
                    channel = LiveChannel(
                        channelId = channel.channelId,
                        sourceId = channel.sourceId,
                        categoryKey = channel.categoryKey,
                        name = channel.name,
                        logoUrl = channel.logoUrl,
                        sortOrder = channel.providerOrder,
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
