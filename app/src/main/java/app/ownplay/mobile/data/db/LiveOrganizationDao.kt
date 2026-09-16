package app.ownplay.mobile.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LiveOrganizationDao {
    @Query("SELECT * FROM live_organization_preferences WHERE sourceId = :sourceId LIMIT 1")
    fun observePreference(sourceId: String): Flow<LiveOrganizationPreferenceEntity?>

    @Query("SELECT * FROM live_organization_preferences WHERE sourceId = :sourceId LIMIT 1")
    suspend fun getPreference(sourceId: String): LiveOrganizationPreferenceEntity?

    @Upsert
    suspend fun upsertPreference(row: LiveOrganizationPreferenceEntity)

    @Query(
        """
        SELECT * FROM live_category_scope_personalization
        WHERE sourceId = :sourceId
          AND organizationMode = :organizationMode
          AND categoryId = :categoryId
        LIMIT 1
        """,
    )
    suspend fun getCategoryPersonalization(
        sourceId: String,
        organizationMode: String,
        categoryId: String,
    ): LiveCategoryScopePersonalizationEntity?

    @Upsert
    suspend fun upsertCategoryPersonalization(row: LiveCategoryScopePersonalizationEntity)

    @Query(
        """
        SELECT * FROM live_channel_membership_personalization
        WHERE sourceId = :sourceId
          AND organizationMode = :organizationMode
          AND categoryId = :categoryId
          AND channelId = :channelId
        LIMIT 1
        """,
    )
    suspend fun getChannelMembershipPersonalization(
        sourceId: String,
        organizationMode: String,
        categoryId: String,
        channelId: String,
    ): LiveChannelMembershipPersonalizationEntity?

    @Upsert
    suspend fun upsertChannelMembershipPersonalization(row: LiveChannelMembershipPersonalizationEntity)

    @Query(
        """
        SELECT * FROM ownplay_live_categories
        WHERE sourceId = :sourceId AND available = 1
        ORDER BY parentCategoryId, categoryId
        """,
    )
    fun observeOwnPlayCategories(sourceId: String): Flow<List<OwnPlayLiveCategoryEntity>>

    @Query(
        """
        SELECT * FROM ownplay_live_channel_memberships
        WHERE sourceId = :sourceId AND available = 1
        ORDER BY categoryId, channelId
        """,
    )
    fun observeOwnPlayMemberships(sourceId: String): Flow<List<OwnPlayLiveChannelMembershipEntity>>

    @Upsert
    suspend fun upsertOwnPlayCategories(rows: List<OwnPlayLiveCategoryEntity>)

    @Upsert
    suspend fun upsertOwnPlayMemberships(rows: List<OwnPlayLiveChannelMembershipEntity>)
}
