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

data class EpisodeLibraryView(
    val episodeId: String,
    val seriesId: String,
    val sourceId: String,
    val seriesName: String,
    val providerEpisodeId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val durationMs: Long?,
    val extension: String?,
    val available: Boolean,
    val progressPositionMs: Long?,
    val progressDurationMs: Long?,
    val progressCompleted: Boolean?,
    val progressUpdatedAt: Long?,
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
interface LibraryDao {
    @Query(
        """
        SELECT * FROM movies
        WHERE sourceId = :sourceId AND available = 1
        ORDER BY providerOrder ASC, name COLLATE NOCASE ASC, movieId ASC
        """,
    )
    fun observeAvailableMovies(sourceId: String): Flow<List<MovieEntity>>

    @Query(
        """
        SELECT * FROM series
        WHERE sourceId = :sourceId AND available = 1
        ORDER BY providerOrder ASC, name COLLATE NOCASE ASC, seriesId ASC
        """,
    )
    fun observeAvailableSeries(sourceId: String): Flow<List<SeriesEntity>>

    @Query(
        """
        SELECT
            e.episodeId AS episodeId,
            e.seriesId AS seriesId,
            s.sourceId AS sourceId,
            s.name AS seriesName,
            e.providerEpisodeId AS providerEpisodeId,
            e.seasonNumber AS seasonNumber,
            e.episodeNumber AS episodeNumber,
            e.title AS title,
            e.durationMs AS durationMs,
            e.extension AS extension,
            e.available AS available,
            p.positionMs AS progressPositionMs,
            p.durationMs AS progressDurationMs,
            p.completed AS progressCompleted,
            p.updatedAt AS progressUpdatedAt
        FROM episodes AS e
        INNER JOIN series AS s ON s.seriesId = e.seriesId
        LEFT JOIN playback_progress AS p
          ON p.sourceId = s.sourceId
         AND p.mediaKind = 'EPISODE'
         AND p.contentId = e.episodeId
        WHERE s.sourceId = :sourceId
          AND s.available = 1
          AND e.available = 1
        ORDER BY s.providerOrder ASC, e.seasonNumber ASC, e.episodeNumber ASC, e.episodeId ASC
        """,
    )
    fun observeAvailableEpisodes(sourceId: String): Flow<List<EpisodeLibraryView>>

    @Query(
        """
        SELECT * FROM playback_progress
        WHERE sourceId = :sourceId
          AND completed = 0
          AND positionMs > 0
        ORDER BY updatedAt DESC, contentId ASC
        """,
    )
    fun observeIncompleteProgress(sourceId: String): Flow<List<PlaybackProgressEntity>>

    @Query(
        """
        SELECT * FROM downloads
        WHERE sourceId = :sourceId
          AND state = 'COMPLETED'
          AND localReference IS NOT NULL
          AND integrityMetadata IS NOT NULL
        ORDER BY createdAt DESC, downloadId ASC
        """,
    )
    fun observeCompletedDownloads(sourceId: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM movies WHERE movieId = :movieId LIMIT 1")
    suspend fun getMovie(movieId: String): MovieEntity?

    @Query("SELECT * FROM series WHERE seriesId = :seriesId LIMIT 1")
    suspend fun getSeries(seriesId: String): SeriesEntity?

    @Query(
        """
        SELECT
            e.episodeId AS episodeId,
            e.seriesId AS seriesId,
            s.sourceId AS sourceId,
            s.name AS seriesName,
            e.providerEpisodeId AS providerEpisodeId,
            e.seasonNumber AS seasonNumber,
            e.episodeNumber AS episodeNumber,
            e.title AS title,
            e.durationMs AS durationMs,
            e.extension AS extension,
            e.available AS available,
            p.positionMs AS progressPositionMs,
            p.durationMs AS progressDurationMs,
            p.completed AS progressCompleted,
            p.updatedAt AS progressUpdatedAt
        FROM episodes AS e
        INNER JOIN series AS s ON s.seriesId = e.seriesId
        LEFT JOIN playback_progress AS p
          ON p.sourceId = s.sourceId
         AND p.mediaKind = 'EPISODE'
         AND p.contentId = e.episodeId
        WHERE e.episodeId = :episodeId
        LIMIT 1
        """,
    )
    suspend fun getEpisode(episodeId: String): EpisodeLibraryView?

    @Query(
        """
        SELECT
            e.episodeId AS episodeId,
            e.seriesId AS seriesId,
            s.sourceId AS sourceId,
            s.name AS seriesName,
            e.providerEpisodeId AS providerEpisodeId,
            e.seasonNumber AS seasonNumber,
            e.episodeNumber AS episodeNumber,
            e.title AS title,
            e.durationMs AS durationMs,
            e.extension AS extension,
            e.available AS available,
            p.positionMs AS progressPositionMs,
            p.durationMs AS progressDurationMs,
            p.completed AS progressCompleted,
            p.updatedAt AS progressUpdatedAt
        FROM episodes AS e
        INNER JOIN series AS s ON s.seriesId = e.seriesId
        LEFT JOIN playback_progress AS p
          ON p.sourceId = s.sourceId
         AND p.mediaKind = 'EPISODE'
         AND p.contentId = e.episodeId
        WHERE e.seriesId = :seriesId
          AND e.available = 1
        ORDER BY e.seasonNumber ASC, e.episodeNumber ASC, e.episodeId ASC
        """,
    )
    suspend fun getEpisodesForSeries(seriesId: String): List<EpisodeLibraryView>

    @Query(
        """
        SELECT * FROM playback_progress
        WHERE sourceId = :sourceId
          AND mediaKind = :mediaKind
          AND contentId = :contentId
        LIMIT 1
        """,
    )
    suspend fun getProgress(
        sourceId: String,
        mediaKind: String,
        contentId: String,
    ): PlaybackProgressEntity?

    @Upsert
    suspend fun upsertProgress(entity: PlaybackProgressEntity)

    @Query("UPDATE episodes SET available = 0 WHERE seriesId = :seriesId")
    suspend fun markEpisodesUnavailable(seriesId: String)
}

@Dao
interface RefreshStateDao {
    @Query("SELECT * FROM refresh_state WHERE sourceId = :sourceId LIMIT 1")
    suspend fun get(sourceId: String): RefreshStateEntity?

    @Upsert
    suspend fun upsert(entity: RefreshStateEntity)
}
