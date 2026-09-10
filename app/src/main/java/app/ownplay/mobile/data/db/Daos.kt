package app.ownplay.mobile.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

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
