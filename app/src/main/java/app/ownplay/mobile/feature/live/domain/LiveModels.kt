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
    val favorite: Boolean = false,
)

data class LiveCustomGroup(
    val groupId: String,
    val sourceId: String,
    val name: String,
    val manualOrder: Int,
    val channelIds: List<String> = emptyList(),
)

data class LiveCatalog(
    val activeSourceId: String? = null,
    val activeSourceName: String? = null,
    val categories: List<LiveCategory> = emptyList(),
    val channels: List<LiveChannel> = emptyList(),
    val customGroups: List<LiveCustomGroup> = emptyList(),
)

data class ManageableLiveCategory(
    val sourceId: String,
    val categoryKey: String,
    val name: String,
    val providerOrder: Int,
    val hidden: Boolean,
    val manualOrder: Int?,
)

data class ManageableLiveChannel(
    val channelId: String,
    val sourceId: String,
    val categoryKey: String?,
    val name: String,
    val logoUrl: String?,
    val providerOrder: Int,
    val favorite: Boolean,
    val localName: String?,
    val localLogo: String?,
    val hidden: Boolean,
    val manualOrder: Int?,
)

data class LiveManagementSource(
    val sourceId: String? = null,
    val sourceName: String? = null,
)

data class LiveManagementCatalog(
    val activeSourceId: String? = null,
    val activeSourceName: String? = null,
    val categories: List<ManageableLiveCategory> = emptyList(),
    val channels: List<ManageableLiveChannel> = emptyList(),
    val customGroups: List<LiveCustomGroup> = emptyList(),
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
    fun observeManagementSource(): Flow<LiveManagementSource>
    fun observeManagementCatalog(): Flow<LiveManagementCatalog>
    fun observeOwnPlayManageableChannels(sourceId: String, categoryId: String): Flow<List<ManageableLiveChannel>>
    fun searchManageableChannels(sourceId: String, query: String): Flow<List<ManageableLiveChannel>>
    suspend fun loadNowNext(channelId: String): LiveNowNext
    suspend fun resolvePlayback(channelId: String): LivePlaybackResolution
    suspend fun setCategoryHidden(sourceId: String, categoryKey: String, hidden: Boolean)
    suspend fun setChannelHidden(channelId: String, hidden: Boolean)
    suspend fun setChannelFavorite(channelId: String, favorite: Boolean)
    suspend fun setChannelLocalName(channelId: String, localName: String?)
    suspend fun setChannelLocalLogo(channelId: String, localLogo: String?)
    suspend fun setCategoryOrder(sourceId: String, orderedCategoryKeys: List<String>)
    suspend fun setChannelOrder(orderedChannelIds: List<String>)
    suspend fun resetCategoryOrder(sourceId: String, categoryKeys: List<String>)
    suspend fun resetChannelOrder(channelIds: List<String>)
    suspend fun createCustomGroup(sourceId: String, name: String)
    suspend fun renameCustomGroup(groupId: String, name: String)
    suspend fun setCustomGroupMembership(groupId: String, channelId: String, included: Boolean)
}
