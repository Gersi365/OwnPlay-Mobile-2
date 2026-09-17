package app.ownplay.mobile.feature.playback.data

import app.ownplay.mobile.data.db.LiveOrganizationDao
import app.ownplay.mobile.data.db.SourceDao
import app.ownplay.mobile.data.security.CredentialStore
import app.ownplay.mobile.data.security.SourceSecret
import app.ownplay.mobile.sources.domain.SourceId
import app.ownplay.mobile.sources.domain.SourceType

internal sealed interface ResolvedLivePlaybackSource {
    val sourceId: SourceId
    val channelId: String

    class M3uDirect(
        override val sourceId: SourceId,
        override val channelId: String,
        private val streamLocator: String,
    ) : ResolvedLivePlaybackSource {
        internal fun <T> useStreamLocator(block: (String) -> T): T = block(streamLocator)

        override fun toString(): String =
            "M3uDirect(sourceId=${sourceId.value}, channelId=$channelId, streamLocator=<redacted>)"
    }

    class XtreamLive(
        override val sourceId: SourceId,
        override val channelId: String,
        private val baseUrl: String,
        private val username: String,
        private val password: String,
        private val streamId: String,
    ) : ResolvedLivePlaybackSource {
        internal fun <T> useConnection(
            block: (baseUrl: String, username: String, password: String, streamId: String) -> T,
        ): T = block(baseUrl, username, password, streamId)

        override fun toString(): String =
            "XtreamLive(sourceId=${sourceId.value}, channelId=$channelId, connection=<redacted>)"
    }
}

internal interface LivePlaybackSourceResolver {
    suspend fun resolve(sourceId: SourceId, channelId: String): ResolvedLivePlaybackSource?
}

internal class RoomLivePlaybackSourceResolver(
    private val sourceDao: SourceDao,
    private val liveOrganizationDao: LiveOrganizationDao,
    private val credentialStore: CredentialStore,
) : LivePlaybackSourceResolver {
    override suspend fun resolve(
        sourceId: SourceId,
        channelId: String,
    ): ResolvedLivePlaybackSource? {
        if (channelId.isBlank()) return null

        val source = sourceDao.get(sourceId.value) ?: return null
        if (!source.enabled) return null
        val channel = liveOrganizationDao.getAvailableChannel(sourceId.value, channelId) ?: return null
        val sourceType = runCatching { SourceType.valueOf(source.type) }.getOrNull() ?: return null

        return when (sourceType) {
            SourceType.M3U -> {
                val locator = channel.streamLocator.takeIf(String::isNotBlank) ?: return null
                ResolvedLivePlaybackSource.M3uDirect(
                    sourceId = sourceId,
                    channelId = channelId,
                    streamLocator = locator,
                )
            }

            SourceType.XTREAM -> {
                val streamId = channel.providerStreamId?.takeIf(String::isNotBlank) ?: return null
                val secret = credentialStore.get(sourceId) as? SourceSecret.Xtream ?: return null
                ResolvedLivePlaybackSource.XtreamLive(
                    sourceId = sourceId,
                    channelId = channelId,
                    baseUrl = source.baseLocator,
                    username = secret.username,
                    password = secret.password,
                    streamId = streamId,
                )
            }
        }
    }
}
