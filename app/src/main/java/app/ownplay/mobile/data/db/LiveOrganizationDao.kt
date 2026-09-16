package app.ownplay.mobile.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class ProviderLiveOrganizationCategoryView(
    val sourceId: String,
    val categoryId: String,
    val displayName: String,
    val hidden: Boolean,
    val manualOrder: Int?,
)

data class ProviderLiveOrganizationMembershipView(
    val sourceId: String,
    val categoryId: String,
    val channelId: String,
    val hidden: Boolean,
    val manualOrder: Int?,
)

data class OwnPlayLiveOrganizationCategoryView(
    val sourceId: String,
    val categoryId: String,
    val parentCategoryId: String?,
    val displayName: String,
    val semanticKey: String?,
    val origin: String,
    val hidden: Boolean,
    val manualOrder: Int?,
)

data class ManualOwnPlayMembershipKeyView(
    val categoryId: String,
    val channelId: String,
)

data class OwnPlayLiveOrganizationMembershipView(
    val sourceId: String,
    val categoryId: String,
    val channelId: String,
    val included: Boolean,
    val origin: String,
    val confidence: String?,
    val evidenceJson: String?,
    val hidden: Boolean,
    val manualOrder: Int?,
)

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
        SELECT
            c.sourceId AS sourceId,
            c.categoryKey AS categoryId,
            c.name AS displayName,
            COALESCE(p.hidden, 0) AS hidden,
            p.manualOrder AS manualOrder
        FROM provider_categories AS c
        LEFT JOIN live_category_scope_personalization AS p
          ON p.sourceId = c.sourceId
         AND p.organizationMode = 'PROVIDER'
         AND p.categoryId = c.categoryKey
        WHERE c.sourceId = :sourceId
          AND c.kind = 'LIVE'
          AND c.available = 1
        ORDER BY
            CASE WHEN p.manualOrder IS NULL THEN 1 ELSE 0 END,
            COALESCE(p.manualOrder, c.providerOrder),
            c.providerOrder,
            c.name COLLATE NOCASE,
            c.categoryKey
        """,
    )
    fun observeProviderCategories(sourceId: String): Flow<List<ProviderLiveOrganizationCategoryView>>

    @Query(
        """
        SELECT
            c.sourceId AS sourceId,
            COALESCE(c.categoryKey, :uncategorizedCategoryId) AS categoryId,
            c.channelId AS channelId,
            COALESCE(p.hidden, 0) AS hidden,
            p.manualOrder AS manualOrder
        FROM live_channels AS c
        LEFT JOIN live_channel_membership_personalization AS p
          ON p.sourceId = c.sourceId
         AND p.organizationMode = 'PROVIDER'
         AND p.categoryId = COALESCE(c.categoryKey, :uncategorizedCategoryId)
         AND p.channelId = c.channelId
        WHERE c.sourceId = :sourceId
          AND c.available = 1
        ORDER BY
            categoryId,
            CASE WHEN p.manualOrder IS NULL THEN 1 ELSE 0 END,
            COALESCE(p.manualOrder, c.providerOrder),
            c.providerOrder,
            c.name COLLATE NOCASE,
            c.channelId
        """,
    )
    fun observeProviderMemberships(
        sourceId: String,
        uncategorizedCategoryId: String,
    ): Flow<List<ProviderLiveOrganizationMembershipView>>

    @Query(
        """
        SELECT
            c.sourceId AS sourceId,
            c.categoryId AS categoryId,
            c.parentCategoryId AS parentCategoryId,
            c.displayName AS displayName,
            c.semanticKey AS semanticKey,
            c.origin AS origin,
            COALESCE(p.hidden, 0) AS hidden,
            p.manualOrder AS manualOrder
        FROM ownplay_live_categories AS c
        LEFT JOIN live_category_scope_personalization AS p
          ON p.sourceId = c.sourceId
         AND p.organizationMode = 'OWNPLAY'
         AND p.categoryId = c.categoryId
        WHERE c.sourceId = :sourceId
          AND c.available = 1
        ORDER BY
            c.parentCategoryId,
            CASE WHEN p.manualOrder IS NULL THEN 1 ELSE 0 END,
            p.manualOrder,
            c.displayName COLLATE NOCASE,
            c.categoryId
        """,
    )
    fun observeOwnPlayCategoryViews(sourceId: String): Flow<List<OwnPlayLiveOrganizationCategoryView>>

    @Query(
        """
        SELECT
            m.sourceId AS sourceId,
            m.categoryId AS categoryId,
            m.channelId AS channelId,
            m.included AS included,
            m.origin AS origin,
            m.confidence AS confidence,
            m.evidenceJson AS evidenceJson,
            COALESCE(p.hidden, 0) AS hidden,
            p.manualOrder AS manualOrder
        FROM ownplay_live_channel_memberships AS m
        INNER JOIN live_channels AS c ON c.channelId = m.channelId
        LEFT JOIN live_channel_membership_personalization AS p
          ON p.sourceId = m.sourceId
         AND p.organizationMode = 'OWNPLAY'
         AND p.categoryId = m.categoryId
         AND p.channelId = m.channelId
        WHERE m.sourceId = :sourceId
          AND m.available = 1
          AND c.available = 1
        ORDER BY
            m.categoryId,
            CASE WHEN p.manualOrder IS NULL THEN 1 ELSE 0 END,
            COALESCE(p.manualOrder, c.providerOrder),
            c.providerOrder,
            c.name COLLATE NOCASE,
            m.channelId
        """,
    )
    fun observeOwnPlayMembershipViews(sourceId: String): Flow<List<OwnPlayLiveOrganizationMembershipView>>

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
        SELECT categoryKey FROM provider_categories
        WHERE sourceId = :sourceId AND kind = 'LIVE' AND available = 1
        ORDER BY providerOrder, name COLLATE NOCASE, categoryKey
        """,
    )
    suspend fun getProviderCategoryIds(sourceId: String): List<String>

    @Query(
        """
        SELECT categoryId FROM ownplay_live_categories
        WHERE sourceId = :sourceId
          AND available = 1
          AND ((:parentCategoryId IS NULL AND parentCategoryId IS NULL) OR parentCategoryId = :parentCategoryId)
        ORDER BY displayName COLLATE NOCASE, categoryId
        """,
    )
    suspend fun getOwnPlaySiblingCategoryIds(sourceId: String, parentCategoryId: String?): List<String>

    @Query(
        """
        SELECT COUNT(*) FROM provider_categories
        WHERE sourceId = :sourceId
          AND kind = 'LIVE'
          AND categoryKey = :categoryId
          AND available = 1
        """,
    )
    suspend fun countProviderCategory(sourceId: String, categoryId: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM ownplay_live_categories
        WHERE sourceId = :sourceId
          AND categoryId = :categoryId
          AND available = 1
        """,
    )
    suspend fun countOwnPlayCategory(sourceId: String, categoryId: String): Int

    @Query("SELECT COUNT(*) FROM ownplay_live_categories WHERE sourceId = :sourceId AND available = 1")
    suspend fun countOwnPlayCategories(sourceId: String): Int

    @Query(
        """
        SELECT channelId FROM live_channels
        WHERE sourceId = :sourceId
          AND available = 1
          AND ((:categoryId = :uncategorizedCategoryId AND categoryKey IS NULL) OR categoryKey = :categoryId)
        ORDER BY providerOrder, name COLLATE NOCASE, channelId
        """,
    )
    suspend fun getProviderChannelIds(
        sourceId: String,
        categoryId: String,
        uncategorizedCategoryId: String,
    ): List<String>

    @Query(
        """
        SELECT m.channelId
        FROM ownplay_live_channel_memberships AS m
        INNER JOIN live_channels AS c ON c.channelId = m.channelId
        WHERE m.sourceId = :sourceId
          AND m.categoryId = :categoryId
          AND m.included = 1
          AND m.available = 1
          AND c.available = 1
        ORDER BY c.providerOrder, c.name COLLATE NOCASE, m.channelId
        """,
    )
    suspend fun getOwnPlayChannelIds(sourceId: String, categoryId: String): List<String>

    @Query(
        """
        SELECT COUNT(*) FROM live_channels
        WHERE channelId = :channelId
          AND sourceId = :sourceId
          AND available = 1
          AND ((:categoryId = :uncategorizedCategoryId AND categoryKey IS NULL) OR categoryKey = :categoryId)
        """,
    )
    suspend fun countProviderChannelMembership(
        sourceId: String,
        categoryId: String,
        channelId: String,
        uncategorizedCategoryId: String,
    ): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM ownplay_live_channel_memberships AS m
        INNER JOIN live_channels AS c ON c.channelId = m.channelId
        WHERE m.sourceId = :sourceId
          AND m.categoryId = :categoryId
          AND m.channelId = :channelId
          AND m.included = 1
          AND m.available = 1
          AND c.available = 1
        """,
    )
    suspend fun countOwnPlayChannelMembership(sourceId: String, categoryId: String, channelId: String): Int

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

    @Query(
        """
        SELECT categoryId FROM ownplay_live_categories
        WHERE sourceId = :sourceId AND origin = 'MANUAL'
        """,
    )
    suspend fun getManualOwnPlayCategoryIds(sourceId: String): List<String>

    @Query(
        """
        SELECT categoryId, channelId FROM ownplay_live_channel_memberships
        WHERE sourceId = :sourceId AND origin = 'MANUAL'
        """,
    )
    suspend fun getManualOwnPlayMembershipKeys(sourceId: String): List<ManualOwnPlayMembershipKeyView>

    @Query(
        """
        UPDATE ownplay_live_channel_memberships
        SET available = 0
        WHERE sourceId = :sourceId
          AND origin = 'AUTO'
          AND lastSeenGeneration != :generation
        """,
    )
    suspend fun markMissingAutoOwnPlayMembershipsUnavailable(sourceId: String, generation: Long)

    @Query(
        """
        UPDATE ownplay_live_categories
        SET available = 0
        WHERE sourceId = :sourceId
          AND origin = 'AUTO'
          AND lastSeenGeneration != :generation
        """,
    )
    suspend fun markMissingAutoOwnPlayCategoriesUnavailable(sourceId: String, generation: Long)
}
