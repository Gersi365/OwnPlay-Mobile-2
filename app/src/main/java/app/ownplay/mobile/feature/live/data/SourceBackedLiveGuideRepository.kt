package app.ownplay.mobile.feature.live.data

import app.ownplay.mobile.data.db.LiveOrganizationDao
import app.ownplay.mobile.data.db.SourceDao
import app.ownplay.mobile.data.security.CredentialStore
import app.ownplay.mobile.data.security.SourceSecret
import app.ownplay.mobile.feature.live.domain.LiveGuidePolicy
import app.ownplay.mobile.feature.live.domain.LiveGuideRepository
import app.ownplay.mobile.feature.live.domain.LiveNowNext
import app.ownplay.mobile.feature.live.domain.LiveProgram
import app.ownplay.mobile.sources.data.xtream.XtreamClient
import app.ownplay.mobile.sources.data.xtream.XtreamConnection
import app.ownplay.mobile.sources.domain.SourceId
import app.ownplay.mobile.sources.domain.SourceType
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException

class SourceBackedLiveGuideRepository(
    private val sourceDao: SourceDao,
    private val liveOrganizationDao: LiveOrganizationDao,
    private val credentialStore: CredentialStore,
    private val xtreamClient: XtreamClient,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : LiveGuideRepository {
    private data class CacheEntry(val loadedAtMs: Long, val programs: List<LiveProgram>)

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    override suspend fun loadNowNext(sourceId: SourceId, channelId: String): LiveNowNext {
        if (channelId.isBlank()) return LiveNowNext()
        val nowMs = nowMillis()
        val cacheKey = "${sourceId.value}:$channelId"
        cache[cacheKey]
            ?.takeIf { nowMs - it.loadedAtMs < CACHE_TTL_MS }
            ?.let { return LiveGuidePolicy.nowNext(it.programs, nowMs / 1_000L) }

        val programs = try {
            val channel = liveOrganizationDao.getAvailableChannel(sourceId.value, channelId)
                ?: return LiveNowNext()
            val source = sourceDao.get(sourceId.value) ?: return LiveNowNext()
            if (!source.enabled || source.type != SourceType.XTREAM.name) return LiveNowNext()
            val streamId = channel.providerStreamId?.takeIf(String::isNotBlank) ?: return LiveNowNext()
            val secret = credentialStore.get(sourceId) as? SourceSecret.Xtream ?: return LiveNowNext()
            xtreamClient.shortEpg(
                connection = XtreamConnection(source.baseLocator, secret.username, secret.password),
                streamId = streamId,
                limit = 4,
            ).map { entry ->
                LiveProgram(entry.title.trim(), entry.startEpochSeconds, entry.endEpochSeconds)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        cache[cacheKey] = CacheEntry(nowMs, programs)
        return LiveGuidePolicy.nowNext(programs, nowMs / 1_000L)
    }

    private companion object {
        const val CACHE_TTL_MS = 5 * 60 * 1_000L
    }
}
