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
    val categoryKey: String?,
    val name: String,
    val logoUrl: String?,
    val streamLocator: String,
    val sortOrder: Int,
)

data class ManageableLiveCategoryView(
    val sourceId: String,
    val categoryKey: String,
    val name: String,
    val providerOrder: Int,
    val hidden: Boolean,
    val manualOrder: Int?,
)

data class ManageableLiveChannelView(
    val channelId: String,
    val sourceId: String,
    val categoryKey: String?,
    val name: String,
    val logoUrl: String?,
    val providerOrder: Int,
    val hidden: Boolean,
    val manualOrder: Int?,
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
        SELECT c.*
        FROM provider_categories AS c
        LEFT JOIN category_personalization AS p
          ON p.sourceId = c.sourceId
         AND p.kind = c.kind
         AND p.categoryKey = c.categoryKey
        WHERE c.sourceId = :sourceId
          AND c.kind = :kind
          AND c.available = 1
          AND COALESCE(p.hidden, 0) = 0
        ORDER BY
            CASE WHEN p.manualOrder IS NULL THEN 1 ELSE 0 END,
            COALESCE(p.manualOrder, c.providerOrder),
            c.providerOrder,
            c.name COLLATE NOCASE,
            c.categoryKey
        """,
    )
    fun observeAvailableCategories(sourceId: String, kind: String): Flow<List<ProviderCategoryEntity>>

    @Query(
        """
        SELECT
            c.sourceId AS sourceId,
            c.categoryKey AS categoryKey,
            c.name AS name,
            c.providerOrder AS providerOrder,
            COALESCE(p.hidden, 0) AS hidden,
            p.manualOrder AS manualOrder
        FROM provider_categories AS c
        LEFT JOIN category_personalization AS p
          ON p.sourceId = c.sourceId
         AND p.kind = c.kind
         AND p.categoryKey = c.categoryKey
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
    fun observeManageableLiveCategories(sourceId: String): Flow<List<ManageableLiveCategoryView>>

    @Query(
        """
        SELECT
            c.channelId AS channelId,
            c.sourceId AS sourceId,
            c.providerStreamId AS providerStreamId,
            c.categoryKey AS categoryKey,
            COALESCE(p.localName, c.name) AS name,
            COALESCE(p.localLogo, c.logoUrl) AS logoUrl,
            c.streamLocator AS streamLocator,
            COALESCE(p.manualOrder, c.providerOrder) AS sortOrder
        FROM live_channels AS c
        LEFT JOIN channel_personalization AS p ON p.channelId = c.channelId
        LEFT JOIN category_personalization AS cp
          ON cp.sourceId = c.sourceId
         AND cp.kind = 'LIVE'
         AND cp.categoryKey = c.categoryKey
        WHERE c.sourceId = :sourceId
          AND c.available = 1
          AND COALESCE(p.hidden, 0) = 0
          AND COALESCE(cp.hidden, 0) = 0
        ORDER BY
            CASE WHEN p.manualOrder IS NULL THEN 1 ELSE 0 END,
            COALESCE(p.manualOrder, c.providerOrder),
            c.providerOrder,
            c.name COLLATE NOCASE,
            c.channelId
        """,
    )
    fun observeAvailableLiveChannels(sourceId: String): Flow<List<LiveChannelView>>

    @Query(
        """
        SELECT
            c.channelId AS channelId,
            c.sourceId AS sourceId,
            c.categoryKey AS categoryKey,
            COALESCE(p.localName, c.name) AS name,
            COALESCE(p.localLogo, c.logoUrl) AS logoUrl,
            c.providerOrder AS providerOrder,
            COALESCE(p.hidden, 0) AS hidden,
            p.manualOrder AS manualOrder
        FROM live_channels AS c
        LEFT JOIN channel_personalization AS p ON p.channelId = c.channelId
        WHERE c.sourceId = :sourceId
          AND c.available = 1
        ORDER BY
            CASE WHEN p.manualOrder IS NULL THEN 1 ELSE 0 END,
            COALESCE(p.manualOrder, c.providerOrder),
            c.providerOrder,
            c.name COLLATE NOCASE,
            c.channelId
        """,
    )
    fun observeManageableLiveChannels(sourceId: String): Flow<List<ManageableLiveChannelView>>

    @Query("SELECT * FROM live_channels WHERE channelId = :channelId LIMIT 1")
    suspend fun getLiveChannel(channelId: String): LiveChannelEntity?

    @Query("SELECT * FROM channel_personalization WHERE channelId = :channelId LIMIT 1")
    suspend fun getChannelPersonalization(channelId: String): ChannelPersonalizationEntity?

    @Upsert
    suspend fun upsertChannelPersonalization(row: ChannelPersonalizationEntity)

    @Query(
        """
        SELECT * FROM category_personalization
        WHERE sourceId = :sourceId AND kind = :kind AND categoryKey = :categoryKey
        LIMIT 1
        """,
    )
    suspend fun getCategoryPersonalization(
        sourceId: String,
        kind: String,
        categoryKey: String,
    ): CategoryPersonalizationEntity?

    @Upsert
    suspend fun upsertCategoryPersonalization(row: CategoryPersonalizationEntity)

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
        SELECT * FROM playback_progress
        WHERE completed = 0
          AND positionMs > 0
        ORDER BY updatedAt DESC, sourceId ASC, mediaKind ASC, contentId ASC
        """,
    )
    fun observeAllIncompleteProgress(): Flow<List<PlaybackProgressEntity>>

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
interface DownloadDao {
    @Query(
        """
        SELECT * FROM downloads
        ORDER BY createdAt DESC, downloadId ASC
        """,
    )
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query(
        """
        SELECT * FROM downloads
        WHERE sourceId = :sourceId
        ORDER BY createdAt DESC, downloadId ASC
        """,
    )
    fun observeForSource(sourceId: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE downloadId = :downloadId LIMIT 1")
    suspend fun get(downloadId: String): DownloadEntity?

    @Query(
        """
        SELECT * FROM downloads
        WHERE sourceId = :sourceId
          AND mediaKind = :mediaKind
          AND contentId = :contentId
        LIMIT 1
        """,
    )
    suspend fun getForContent(
        sourceId: String,
        mediaKind: String,
        contentId: String,
    ): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: DownloadEntity)

    @Query(
        """
        UPDATE downloads
        SET state = 'DOWNLOADING', failureReason = NULL, updatedAt = :updatedAt
        WHERE downloadId = :downloadId
          AND state IN ('QUEUED', 'DOWNLOADING')
        """,
    )
    suspend fun markDownloadingIfRunnable(downloadId: String, updatedAt: Long): Int

    @Query(
        """
        UPDATE downloads
        SET state = 'PAUSED', updatedAt = :updatedAt
        WHERE downloadId = :downloadId
          AND state IN ('QUEUED', 'DOWNLOADING')
        """,
    )
    suspend fun pauseIfActive(downloadId: String, updatedAt: Long): Int

    @Query(
        """
        UPDATE downloads
        SET state = 'QUEUED', failureReason = NULL, updatedAt = :updatedAt
        WHERE downloadId = :downloadId
          AND state = 'PAUSED'
        """,
    )
    suspend fun queueIfPaused(downloadId: String, updatedAt: Long): Int

    @Query(
        """
        UPDATE downloads
        SET state = 'QUEUED', failureReason = NULL, updatedAt = :updatedAt
        WHERE downloadId = :downloadId
          AND state = 'FAILED'
        """,
    )
    suspend fun queueIfFailed(downloadId: String, updatedAt: Long): Int

    @Query(
        """
        UPDATE downloads
        SET bytesDownloaded = :bytesDownloaded,
            totalBytes = :totalBytes,
            updatedAt = :updatedAt
        WHERE downloadId = :downloadId
          AND state = 'DOWNLOADING'
        """,
    )
    suspend fun updateProgressIfDownloading(
        downloadId: String,
        bytesDownloaded: Long,
        totalBytes: Long?,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE downloads
        SET state = 'COMPLETED',
            bytesDownloaded = :bytesDownloaded,
            totalBytes = :totalBytes,
            localReference = :localReference,
            integrityMetadata = :integrityMetadata,
            failureReason = NULL,
            updatedAt = :updatedAt
        WHERE downloadId = :downloadId
          AND state = 'DOWNLOADING'
        """,
    )
    suspend fun completeIfDownloading(
        downloadId: String,
        bytesDownloaded: Long,
        totalBytes: Long,
        localReference: String,
        integrityMetadata: String,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE downloads
        SET state = 'FAILED', failureReason = :failureCode, updatedAt = :updatedAt
        WHERE downloadId = :downloadId
          AND state IN ('QUEUED', 'DOWNLOADING')
        """,
    )
    suspend fun failIfRunnable(downloadId: String, failureCode: String, updatedAt: Long): Int

    @Query(
        """
        UPDATE downloads
        SET state = 'FAILED',
            localReference = NULL,
            integrityMetadata = NULL,
            failureReason = 'INTEGRITY',
            updatedAt = :updatedAt
        WHERE downloadId = :downloadId
          AND state = 'COMPLETED'
        """,
    )
    suspend fun markCompletedIntegrityFailure(downloadId: String, updatedAt: Long): Int

    @Query("DELETE FROM downloads WHERE downloadId = :downloadId")
    suspend fun delete(downloadId: String): Int
}

@Dao
interface RefreshStateDao {
    @Query("SELECT * FROM refresh_state WHERE sourceId = :sourceId LIMIT 1")
    suspend fun get(sourceId: String): RefreshStateEntity?

    @Upsert
    suspend fun upsert(entity: RefreshStateEntity)
}
