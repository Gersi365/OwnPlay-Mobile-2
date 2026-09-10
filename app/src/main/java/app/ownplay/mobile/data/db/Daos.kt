package app.ownplay.mobile.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class LiveChannelView(
    val channelId: String,
    val sourceId: String,
    val providerStreamId: String?,
    val name: String,
    val logoUrl: String?,
    val streamLocator: String,
    val sortOrder: Int,
)

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources ORDER BY updatedAt DESC, createdAt ASC, sourceId ASC")
    fun observeAll(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources WHERE sourceId = :sourceId LIMIT 1")
    suspend fun get(sourceId: String): SourceEntity?

    @Query("SELECT * FROM sources ORDER BY updatedAt DESC, createdAt ASC, sourceId ASC")
    suspend fun getAll(): List<SourceEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: SourceEntity)

    @Update
    suspend fun update(entity: SourceEntity)

    @Query("DELETE FROM sources WHERE sourceId = :sourceId")
    suspend fun delete(sourceId: String): Int
}

@Dao
interface CatalogDao {
    @Upsert
    suspend fun upsertCategories(rows: List<ProviderCategoryEntity>)

    @Upsert
    suspend fun upsertLiveChannels(rows: List<LiveChannelEntity>)

    @Upsert
    suspend fun upsertMovies(rows: List<MovieEntity>)

    @Upsert
    suspend fun upsertSeries(rows: List<SeriesEntity>)

    @Upsert
    suspend fun upsertEpisodes(rows: List<EpisodeEntity>)

    @Query(
        """
        SELECT
            c.channelId AS channelId,
            c.sourceId AS sourceId,
            c.providerStreamId AS providerStreamId,
            COALESCE(p.localName, c.name) AS name,
            COALESCE(p.localLogo, c.logoUrl) AS logoUrl,
            c.streamLocator AS streamLocator,
            COALESCE(p.manualOrder, c.providerOrder) AS sortOrder
        FROM live_channels AS c
        LEFT JOIN channel_personalization AS p ON p.channelId = c.channelId
        WHERE c.sourceId = :sourceId
          AND c.available = 1
          AND COALESCE(p.hidden, 0) = 0
        ORDER BY
            CASE WHEN p.manualOrder IS NULL THEN 1 ELSE 0 END,
            COALESCE(p.manualOrder, c.providerOrder),
            c.providerOrder,
            c.name COLLATE NOCASE,
            c.channelId
        """,
    )
    fun observeAvailableLiveChannels(sourceId: String): Flow<List<LiveChannelView>>

    @Query("SELECT * FROM live_channels WHERE channelId = :channelId LIMIT 1")
    suspend fun getLiveChannel(channelId: String): LiveChannelEntity?

    @Query(
        """
        UPDATE provider_categories
        SET available = 0
        WHERE sourceId = :sourceId
          AND kind = :kind
          AND lastSeenGeneration != :generation
        """,
    )
    suspend fun markMissingCategoriesUnavailable(sourceId: String, kind: String, generation: Long)

    @Query(
        """
        UPDATE live_channels
        SET available = 0
        WHERE sourceId = :sourceId
          AND lastSeenGeneration != :generation
        """,
    )
    suspend fun markMissingLiveUnavailable(sourceId: String, generation: Long)

    @Query(
        """
        UPDATE movies
        SET available = 0
        WHERE sourceId = :sourceId
          AND lastSeenGeneration != :generation
        """,
    )
    suspend fun markMissingMoviesUnavailable(sourceId: String, generation: Long)

    @Query(
        """
        UPDATE series
        SET available = 0
        WHERE sourceId = :sourceId
          AND lastSeenGeneration != :generation
        """,
    )
    suspend fun markMissingSeriesUnavailable(sourceId: String, generation: Long)
}

@Dao
interface RefreshStateDao {
    @Query("SELECT * FROM refresh_state WHERE sourceId = :sourceId LIMIT 1")
    suspend fun get(sourceId: String): RefreshStateEntity?

    @Upsert
    suspend fun upsert(entity: RefreshStateEntity)
}
