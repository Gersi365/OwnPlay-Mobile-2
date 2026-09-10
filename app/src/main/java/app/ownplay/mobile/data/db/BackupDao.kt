package app.ownplay.mobile.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

data class BackupChannelPersonalizationView(
    val sourceId: String,
    val channelId: String,
    val favorite: Boolean,
    val hidden: Boolean,
    val localName: String?,
    val manualOrder: Int?,
)

data class BackupGroupMembershipView(
    val sourceId: String,
    val groupId: String,
    val channelId: String,
    val manualOrder: Int,
)

@Dao
interface BackupDao {
    @Query(
        """
        SELECT
            c.sourceId AS sourceId,
            p.channelId AS channelId,
            p.favorite AS favorite,
            p.hidden AS hidden,
            p.localName AS localName,
            p.manualOrder AS manualOrder
        FROM channel_personalization AS p
        INNER JOIN live_channels AS c ON c.channelId = p.channelId
        ORDER BY c.sourceId ASC, p.channelId ASC
        """,
    )
    suspend fun getChannelPersonalization(): List<BackupChannelPersonalizationView>

    @Query("SELECT * FROM custom_groups ORDER BY sourceId ASC, manualOrder ASC, groupId ASC")
    suspend fun getCustomGroups(): List<CustomGroupEntity>

    @Query(
        """
        SELECT
            g.sourceId AS sourceId,
            m.groupId AS groupId,
            m.channelId AS channelId,
            m.manualOrder AS manualOrder
        FROM custom_group_memberships AS m
        INNER JOIN custom_groups AS g ON g.groupId = m.groupId
        ORDER BY g.sourceId ASC, m.groupId ASC, m.manualOrder ASC, m.channelId ASC
        """,
    )
    suspend fun getCustomGroupMemberships(): List<BackupGroupMembershipView>

    @Query("SELECT * FROM media_favorites ORDER BY sourceId ASC, mediaKind ASC, contentId ASC")
    suspend fun getMediaFavorites(): List<MediaFavoriteEntity>

    @Query("SELECT * FROM playback_progress ORDER BY sourceId ASC, mediaKind ASC, contentId ASC")
    suspend fun getPlaybackProgress(): List<PlaybackProgressEntity>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM live_channels
            WHERE sourceId = :sourceId AND channelId = :channelId
        )
        """,
    )
    suspend fun hasLiveChannel(sourceId: String, channelId: String): Boolean

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM custom_groups
            WHERE sourceId = :sourceId AND groupId = :groupId
        )
        """,
    )
    suspend fun hasCustomGroup(sourceId: String, groupId: String): Boolean

    @Upsert
    suspend fun upsertCustomGroups(rows: List<CustomGroupEntity>)

    @Upsert
    suspend fun upsertMediaFavorites(rows: List<MediaFavoriteEntity>)

    @Upsert
    suspend fun upsertPlaybackProgress(rows: List<PlaybackProgressEntity>)

    @Upsert
    suspend fun upsertChannelPersonalization(row: ChannelPersonalizationEntity)

    @Upsert
    suspend fun upsertCustomGroupMembership(row: CustomGroupMembershipEntity)
}
