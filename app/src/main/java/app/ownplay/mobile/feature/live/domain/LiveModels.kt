package app.ownplay.mobile.feature.live.domain

import app.ownplay.mobile.playback.domain.PlaybackStreamFormat
import kotlinx.coroutines.flow.Flow

data class LiveCategory(
    val categoryKey: String,
    val name: String,
    val providerOrder: Int,
)

data class LiveChannel(
    val channelId: String,
    val sourceId: String,
    val categoryKey: String?,
    val name: String,
    val logoUrl: String?,
    val sortOrder: Int,
)

data class LiveCatalog(
    val activeSourceId: String? = null,
    val activeSourceName: String? = null,
    val categories: List<LiveCategory> = emptyList(),
    val channels: List<LiveChannel> = emptyList(),
)

data class LiveProgram(
    val title: String,
    val startEpochSeconds: Long?,
    val endEpochSeconds: Long?,
)

data class LiveNowNext(
    val now: LiveProgram? = null,
    val next: LiveProgram? = null,
)

class ResolvedLivePlayback(
    val channel: LiveChannel,
    val uri: String,
    val streamFormat: PlaybackStreamFormat,
    val fallbackUri: String? = null,
    val fallbackStreamFormat: PlaybackStreamFormat? = null,
) {
    override fun toString(): String =
        "ResolvedLivePlayback(channelId=${channel.channelId}, uri=<redacted>, fallbackUri=${if (fallbackUri == null) "none" else "<redacted>"}, streamFormat=$streamFormat)"
}

sealed interface LivePlaybackResolution {
    data class Success(val value: ResolvedLivePlayback) : LivePlaybackResolution

    data class Failure(
        val code: String,
        val safeMessage: String,
    ) : LivePlaybackResolution
}

interface LiveRepository {
    fun observeCatalog(): Flow<LiveCatalog>
    suspend fun loadNowNext(channelId: String): LiveNowNext
    suspend fun resolvePlayback(channelId: String): LivePlaybackResolution
}
