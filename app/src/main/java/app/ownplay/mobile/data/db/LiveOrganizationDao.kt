package app.ownplay.mobile.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LiveOrganizationDao {
    @Query("SELECT * FROM live_organization_preferences WHERE sourceId = :sourceId LIMIT 1")
    fun observePreference(sourceId: String): Flow<LiveOrganizationPreferenceEntity?>

    @Upsert
    suspend fun upsertPreference(row: LiveOrganizationPreferenceEntity)

    @Query(
        """
        SELECT * FROM provider_categories
        WHERE sourceId = :sourceId
          AND kind = 'LIVE'
          AND available = 1
        ORDER BY providerOrder, categoryKey
        """,
    )
    fun observeProviderLiveCategories(sourceId: String): Flow<List<ProviderCategoryEntity>>

    @Query(
        """
        SELECT * FROM live_channels
        WHERE sourceId = :sourceId
          AND available = 1
        ORDER BY providerOrder, channelId
        """,
    )
    fun observeLiveChannels(sourceId: String): Flow<List<LiveChannelEntity>>

    @Query(
        """
        SELECT p.channelId
        FROM channel_personalization AS p
        INNER JOIN live_channels AS c ON c.channelId = p.channelId
        WHERE c.sourceId = :sourceId
          AND c.available = 1
          AND p.favorite = 1
        ORDER BY c.providerOrder, c.channelId
        """,
    )
    fun observeFavoriteChannelIds(sourceId: String): Flow<List<String>>

    @Query(
        """
        SELECT * FROM ownplay_live_categories
        WHERE sourceId = :sourceId
        ORDER BY categoryId
        """,
    )
    fun observeOwnPlayCategoriesForCompatibility(sourceId: String): Flow<List<OwnPlayLiveCategoryEntity>>

    @Query(
        """
        SELECT * FROM live_channel_membership_personalization
        WHERE sourceId = :sourceId
          AND organizationMode = 'OWNPLAY_OVERRIDE'
        ORDER BY channelId, categoryId
        """,
    )
    fun observeManualPlacementOverrides(sourceId: String): Flow<List<LiveChannelMembershipPersonalizationEntity>>

    @Query(
        """
        SELECT * FROM ownplay_live_channel_memberships
        WHERE sourceId = :sourceId
          AND origin = 'MANUAL'
          AND included = 1
          AND available = 1
        ORDER BY channelId, categoryId
        """,
    )
    fun observeLegacyManualMemberships(sourceId: String): Flow<List<OwnPlayLiveChannelMembershipEntity>>

    @Query(
        """
        SELECT DISTINCT channelId FROM ownplay_live_channel_memberships
        WHERE sourceId = :sourceId
          AND origin = 'MANUAL'
          AND included = 1
          AND available = 1
        """,
    )
    suspend fun getLegacyManualChannelIds(sourceId: String): List<String>

    @Upsert
    suspend fun upsertOwnPlayCategories(rows: List<OwnPlayLiveCategoryEntity>)

    @Upsert
    suspend fun upsertOwnPlayMemberships(rows: List<OwnPlayLiveChannelMembershipEntity>)

    @Query(
        """
        UPDATE ownplay_live_categories
        SET available = 0
        WHERE sourceId = :sourceId
          AND origin = 'AUTO'
          AND lastSeenGeneration != :generation
        """,
    )
    suspend fun markMissingAutomaticCategoriesUnavailable(sourceId: String, generation: Long)

    @Query(
        """
        UPDATE ownplay_live_channel_memberships
        SET available = 0
        WHERE sourceId = :sourceId
          AND origin = 'AUTO'
          AND lastSeenGeneration != :generation
        """,
    )
    suspend fun markMissingAutomaticMembershipsUnavailable(sourceId: String, generation: Long)

    @Query("SELECT COUNT(*) FROM sources WHERE sourceId = :sourceId")
    suspend fun countSource(sourceId: String): Int

    @Query(
        """
        SELECT * FROM live_channels
        WHERE sourceId = :sourceId
          AND channelId = :channelId
          AND available = 1
        LIMIT 1
        """,
    )
    suspend fun getAvailableChannel(sourceId: String, channelId: String): LiveChannelEntity?

    @Query(
        """
        SELECT * FROM ownplay_live_categories
        WHERE sourceId = :sourceId
          AND categoryId = :categoryId
          AND available = 1
        LIMIT 1
        """,
    )
    suspend fun getAvailableOwnPlayCategory(sourceId: String, categoryId: String): OwnPlayLiveCategoryEntity?

    @Query(
        """
        DELETE FROM live_channel_membership_personalization
        WHERE sourceId = :sourceId
          AND organizationMode = 'OWNPLAY_OVERRIDE'
          AND channelId = :channelId
        """,
    )
    suspend fun deleteManualPlacementOverrides(sourceId: String, channelId: String): Int

    @Upsert
    suspend fun upsertManualPlacementOverride(row: LiveChannelMembershipPersonalizationEntity)

    @Query(
        """
        UPDATE ownplay_live_channel_memberships
        SET available = 0
        WHERE sourceId = :sourceId
          AND channelId = :channelId
          AND origin = 'MANUAL'
          AND available = 1
        """,
    )
    suspend fun clearLegacyManualMemberships(sourceId: String, channelId: String): Int
}
