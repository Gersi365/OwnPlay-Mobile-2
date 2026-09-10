package app.ownplay.mobile.feature.live.data

import app.ownplay.mobile.data.db.CatalogDao
import app.ownplay.mobile.data.db.SourceDao
import app.ownplay.mobile.data.security.CredentialStore
import app.ownplay.mobile.feature.live.domain.LiveCatalog
import app.ownplay.mobile.feature.live.domain.LiveChannel
import app.ownplay.mobile.feature.live.domain.LivePlaybackResolution
import app.ownplay.mobile.feature.live.domain.LiveRepository
import app.ownplay.mobile.feature.live.domain.ResolvedLivePlayback
import app.ownplay.mobile.playback.domain.PlaybackStreamFormat
import app.ownplay.mobile.sources.data.xtream.XtreamUrlBuilder
import app.ownplay.mobile.sources.domain.SourceCredential
import app.ownplay.mobile.sources.domain.SourceRepository
import app.ownplay.mobile.sources.domain.SourceType
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class LiveRepositoryImpl(
    private val sourceRepository: SourceRepository,
    private val sourceDao: SourceDao,
    private val catalogDao: CatalogDao,
    private val credentialStore: CredentialStore,
) : LiveRepository {
    override fun observeCatalog(): Flow<LiveCatalog> =
        sourceRepository.observeActiveSource().flatMapLatest { source ->
            if (source == null) {
                flowOf(LiveCatalog())
            } else {
                catalogDao.observeAvailableLiveChannels(source.sourceId).map { rows ->
                    LiveCatalog(
                        activeSourceId = source.sourceId,
                        activeSourceName = source.displayName,
                        channels = rows.map { row ->
                            LiveChannel(
                                channelId = row.channelId,
                                sourceId = row.sourceId,
                                name = row.name,
                                logoUrl = row.logoUrl,
                                sortOrder = row.sortOrder,
                            )
                        },
                    )
                }
            }
        }

    override suspend fun resolvePlayback(channelId: String): LivePlaybackResolution {
        if (channelId.isBlank()) {
            return failure("INVALID_CHANNEL", "This channel cannot be opened.")
        }

        return try {
            val channel = catalogDao.getLiveChannel(channelId)
                ?: return failure("CHANNEL_NOT_FOUND", "This channel is no longer available.")
            if (!channel.available) {
                return failure("CHANNEL_UNAVAILABLE", "This channel is currently unavailable.")
            }

            val source = sourceDao.get(channel.sourceId)
                ?: return failure("SOURCE_NOT_FOUND", "The channel source is no longer available.")
            if (!source.enabled) {
                return failure("SOURCE_DISABLED", "The channel source is disabled.")
            }

            val uri = when (source.type) {
                SourceType.XTREAM.name -> {
                    val providerId = channel.providerStreamId
                        ?: return failure("STREAM_ID_MISSING", "This channel has no playable stream id.")
                    val credential = credentialStore.get(source.sourceId) as? SourceCredential.Xtream
                        ?: return failure("CREDENTIAL_MISSING", "Source credentials are unavailable.")
                    XtreamUrlBuilder.streamUrl(
                        baseUrl = source.baseLocator,
                        credential = credential,
                        kind = "live",
                        providerId = providerId,
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
                        name = channel.name,
                        logoUrl = channel.logoUrl,
                        sortOrder = channel.providerOrder,
                    ),
                    uri = uri,
                    streamFormat = streamFormatFor(uri),
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
        return if (normalizedPath.endsWith(".m3u8")) {
            PlaybackStreamFormat.HLS
        } else {
            PlaybackStreamFormat.AUTO
        }
    }

    private fun failure(code: String, message: String): LivePlaybackResolution.Failure =
        LivePlaybackResolution.Failure(code = code, safeMessage = message)
}
