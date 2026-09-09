package app.ownplay.mobile.sources.data

import androidx.room.withTransaction
import app.ownplay.mobile.data.db.CatalogDao
import app.ownplay.mobile.data.db.LiveChannelEntity
import app.ownplay.mobile.data.db.MovieEntity
import app.ownplay.mobile.data.db.OwnPlayDatabase
import app.ownplay.mobile.data.db.ProviderCategoryEntity
import app.ownplay.mobile.data.db.RefreshStateEntity
import app.ownplay.mobile.data.db.SeriesEntity
import app.ownplay.mobile.data.db.SourceDao
import app.ownplay.mobile.data.db.SourceEntity
import app.ownplay.mobile.data.prefs.ActiveSourcePreferences
import app.ownplay.mobile.data.security.CredentialStore
import app.ownplay.mobile.sources.domain.NewSource
import app.ownplay.mobile.sources.domain.RefreshStatus
import app.ownplay.mobile.sources.domain.RefreshSummary
import app.ownplay.mobile.sources.domain.Source
import app.ownplay.mobile.sources.domain.SourceConnectionUpdate
import app.ownplay.mobile.sources.domain.SourceCredential
import app.ownplay.mobile.sources.domain.SourceError
import app.ownplay.mobile.sources.domain.SourceRepository
import app.ownplay.mobile.sources.domain.SourceResult
import app.ownplay.mobile.sources.domain.SourceSelectionPolicy
import app.ownplay.mobile.sources.domain.SourceType
import app.ownplay.mobile.sources.domain.SourceUpdate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class SourceRepositoryImpl(
    private val database: OwnPlayDatabase,
    private val sourceDao: SourceDao,
    private val catalogDao: CatalogDao,
    private val activeSourcePreferences: ActiveSourcePreferences,
    private val credentialStore: CredentialStore,
    private val catalogLoader: SourceCatalogLoader,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val newSourceId: () -> String = { UUID.randomUUID().toString() },
) : SourceRepository {
    override fun observeSources(): Flow<List<Source>> = sourceDao.observeAll().map { rows -> rows.map(SourceEntity::toDomain) }

    override fun observeActiveSource(): Flow<Source?> = combine(
        observeSources(),
        activeSourcePreferences.selectedSourceId,
    ) { sources, persistedId ->
        SourceSelectionPolicy.resolve(persistedId, sources)
    }

    override suspend fun addSource(input: NewSource): SourceResult<Source> {
        val displayName = input.displayName.trim()
        if (displayName.isBlank()) return failure("INVALID_NAME", "Source name is required.")

        val sourceId = newSourceId()
        val timestamp = nowMillis()
        val prepared = try {
            when (input) {
                is NewSource.Xtream -> PreparedSource(
                    entity = SourceEntity(
                        sourceId = sourceId,
                        displayName = displayName,
                        type = SourceType.XTREAM.name,
                        baseLocator = SourceLocatorPolicy.normalizeXtream(input.baseUrl),
                        credentialReference = sourceId,
                        enabled = true,
                        createdAt = timestamp,
                        updatedAt = timestamp,
                    ),
                    credential = input.credential,
                )
                is NewSource.M3u -> {
                    val remote = SourceLocatorPolicy.validateM3uRemote(input.credential.locator)
                    PreparedSource(
                        entity = SourceEntity(
                            sourceId = sourceId,
                            displayName = displayName,
                            type = SourceType.M3U.name,
                            baseLocator = SourceLocatorPolicy.redactRemoteLocator(remote),
                            credentialReference = sourceId,
                            enabled = true,
                            createdAt = timestamp,
                            updatedAt = timestamp,
                        ),
                        credential = SourceCredential.M3uRemoteLocator(remote),
                    )
                }
            }
        } catch (_: Exception) {
            return failure("INVALID_LOCATOR", "Source address is invalid.")
        }

        return try {
            credentialStore.put(sourceId, prepared.credential)
            try {
                sourceDao.insert(prepared.entity)
            } catch (exception: Exception) {
                runCatching { credentialStore.remove(sourceId) }
                throw exception
            }
            if (activeSourcePreferences.currentSelectedSourceId() == null) {
                activeSourcePreferences.setSelectedSourceId(sourceId)
            }
            SourceResult.Success(prepared.entity.toDomain())
        } catch (_: Exception) {
            failure("SOURCE_ADD_FAILED", "Source could not be saved.")
        }
    }

    override suspend fun updateSource(input: SourceUpdate): SourceResult<Unit> {
        val existing = sourceDao.get(input.sourceId)
            ?: return failure("SOURCE_NOT_FOUND", "Source was not found.")
        val priorCredential = try {
            credentialStore.get(existing.sourceId)
        } catch (_: Exception) {
            return failure("CREDENTIAL_READ_FAILED", "Secure source credentials could not be read.")
        }

        var replacementCredential: SourceCredential? = null
        val updated = try {
            var baseLocator = existing.baseLocator
            when (val connection = input.connection) {
                null -> Unit
                is SourceConnectionUpdate.Xtream -> {
                    if (existing.type != SourceType.XTREAM.name) return failure("SOURCE_TYPE_MISMATCH", "Source type cannot be changed.")
                    connection.baseUrl?.let { baseLocator = SourceLocatorPolicy.normalizeXtream(it) }
                    replacementCredential = connection.credential
                }
                is SourceConnectionUpdate.M3u -> {
                    if (existing.type != SourceType.M3U.name) return failure("SOURCE_TYPE_MISMATCH", "Source type cannot be changed.")
                    val remote = SourceLocatorPolicy.validateM3uRemote(connection.credential.locator)
                    baseLocator = SourceLocatorPolicy.redactRemoteLocator(remote)
                    replacementCredential = SourceCredential.M3uRemoteLocator(remote)
                }
            }
            existing.copy(
                displayName = input.displayName?.trim()?.takeIf(String::isNotBlank) ?: existing.displayName,
                enabled = input.enabled ?: existing.enabled,
                baseLocator = baseLocator,
                updatedAt = nowMillis(),
            )
        } catch (_: Exception) {
            return failure("INVALID_SOURCE_UPDATE", "Source changes are invalid.")
        }

        return try {
            replacementCredential?.let { credentialStore.put(existing.sourceId, it) }
            try {
                sourceDao.update(updated)
            } catch (exception: Exception) {
                if (replacementCredential != null && priorCredential != null) {
                    runCatching { credentialStore.put(existing.sourceId, priorCredential) }
                }
                throw exception
            }
            SourceResult.Success(Unit)
        } catch (_: Exception) {
            failure("SOURCE_UPDATE_FAILED", "Source changes could not be saved.")
        }
    }

    override suspend fun removeSource(sourceId: String): SourceResult<Unit> {
        val existing = sourceDao.get(sourceId)
            ?: return failure("SOURCE_NOT_FOUND", "Source was not found.")
        val priorCredential = try {
            credentialStore.get(sourceId)
        } catch (_: Exception) {
            return failure("CREDENTIAL_READ_FAILED", "Secure source credentials could not be read.")
        }

        return try {
            credentialStore.remove(sourceId)
            try {
                sourceDao.delete(sourceId)
            } catch (exception: Exception) {
                if (priorCredential != null) runCatching { credentialStore.put(sourceId, priorCredential) }
                throw exception
            }

            if (activeSourcePreferences.currentSelectedSourceId() == sourceId) {
                val remaining = sourceDao.getAll().map(SourceEntity::toDomain)
                val fallback = SourceSelectionPolicy.resolve(null, remaining)
                activeSourcePreferences.setSelectedSourceId(fallback?.sourceId)
            }
            SourceResult.Success(Unit)
        } catch (_: Exception) {
            failure("SOURCE_REMOVE_FAILED", "Source could not be removed.")
        }
    }

    override suspend fun selectSource(sourceId: String?): SourceResult<Unit> {
        if (sourceId != null) {
            val source = sourceDao.get(sourceId)?.toDomain()
                ?: return failure("SOURCE_NOT_FOUND", "Source was not found.")
            if (!source.enabled) return failure("SOURCE_DISABLED", "Disabled sources cannot be selected.")
        }
        return try {
            activeSourcePreferences.setSelectedSourceId(sourceId)
            SourceResult.Success(Unit)
        } catch (_: Exception) {
            failure("SOURCE_SELECTION_FAILED", "Source selection could not be saved.")
        }
    }

    override suspend fun refresh(sourceId: String): SourceResult<RefreshSummary> {
        val source = sourceDao.get(sourceId)?.toDomain()
            ?: return failure("SOURCE_NOT_FOUND", "Source was not found.")
        val credential = try {
            credentialStore.get(sourceId)
        } catch (_: Exception) {
            return failure("CREDENTIAL_READ_FAILED", "Secure source credentials could not be read.")
        } ?: return failure("CREDENTIAL_MISSING", "Source credentials are unavailable.")

        val attemptAt = nowMillis()
        val payload = try {
            catalogLoader.load(source, credential)
        } catch (_: Exception) {
            return recordFailedRefresh(sourceId, attemptAt, "REFRESH_UNEXPECTED")
        }
        val previous = database.refreshStateDao().get(sourceId)
        val plan = RefreshPolicy.plan(previous?.generation ?: 0, payload)

        if (plan.successfulSections.isEmpty()) {
            database.refreshStateDao().upsert(
                RefreshStateEntity(
                    sourceId = sourceId,
                    generation = plan.generation,
                    state = "FAILED",
                    lastAttempt = attemptAt,
                    lastSuccess = previous?.lastSuccess,
                    errorCode = plan.errorCode ?: "REFRESH_FAILED",
                ),
            )
            return failure("REFRESH_FAILED", "Source refresh failed; the last known catalog was preserved.")
        }

        return try {
            val completedAt = nowMillis()
            database.withTransaction {
                persistSuccessfulSections(sourceId, plan.generation, payload, plan.successfulSections)
                database.refreshStateDao().upsert(
                    RefreshStateEntity(
                        sourceId = sourceId,
                        generation = plan.generation,
                        state = plan.state,
                        lastAttempt = attemptAt,
                        lastSuccess = completedAt,
                        errorCode = plan.errorCode,
                    ),
                )
            }
            SourceResult.Success(
                RefreshSummary(
                    sourceId = sourceId,
                    status = if (plan.state == "PARTIAL") RefreshStatus.PARTIAL else RefreshStatus.SUCCESS,
                    generation = plan.generation,
                    liveCategories = payload.liveCategories.successSize(),
                    liveChannels = payload.liveChannels.successSize(),
                    vodCategories = payload.vodCategories.successSize(),
                    movies = payload.movies.successSize(),
                    seriesCategories = payload.seriesCategories.successSize(),
                    series = payload.series.successSize(),
                    warnings = payload.errorCodes(),
                ),
            )
        } catch (_: Exception) {
            failure("REFRESH_PERSIST_FAILED", "Source refresh data could not be committed.")
        }
    }

    private suspend fun persistSuccessfulSections(
        sourceId: String,
        generation: Long,
        payload: ProviderRefreshPayload,
        successful: Set<CatalogSection>,
    ) {
        if (CatalogSection.LIVE_CATEGORIES in successful) {
            val rows = payload.liveCategories.value.orEmpty().map { it.toEntity(sourceId, "LIVE", generation) }
            catalogDao.upsertCategories(rows)
            catalogDao.markMissingCategoriesUnavailable(sourceId, "LIVE", generation)
        }
        if (CatalogSection.LIVE_CHANNELS in successful) {
            val rows = payload.liveChannels.value.orEmpty().map { it.toEntity(sourceId, generation) }
            catalogDao.upsertLiveChannels(rows)
            catalogDao.markMissingLiveUnavailable(sourceId, generation)
        }
        if (CatalogSection.VOD_CATEGORIES in successful) {
            val rows = payload.vodCategories.value.orEmpty().map { it.toEntity(sourceId, "MOVIE", generation) }
            catalogDao.upsertCategories(rows)
            catalogDao.markMissingCategoriesUnavailable(sourceId, "MOVIE", generation)
        }
        if (CatalogSection.MOVIES in successful) {
            val rows = payload.movies.value.orEmpty().map { it.toEntity(sourceId, generation) }
            catalogDao.upsertMovies(rows)
            catalogDao.markMissingMoviesUnavailable(sourceId, generation)
        }
        if (CatalogSection.SERIES_CATEGORIES in successful) {
            val rows = payload.seriesCategories.value.orEmpty().map { it.toEntity(sourceId, "SERIES", generation) }
            catalogDao.upsertCategories(rows)
            catalogDao.markMissingCategoriesUnavailable(sourceId, "SERIES", generation)
        }
        if (CatalogSection.SERIES in successful) {
            val rows = payload.series.value.orEmpty().map { it.toEntity(sourceId, generation) }
            catalogDao.upsertSeries(rows)
            catalogDao.markMissingSeriesUnavailable(sourceId, generation)
        }
    }

    private suspend fun recordFailedRefresh(
        sourceId: String,
        attemptAt: Long,
        code: String,
    ): SourceResult<RefreshSummary> {
        val previous = database.refreshStateDao().get(sourceId)
        runCatching {
            database.refreshStateDao().upsert(
                RefreshStateEntity(
                    sourceId = sourceId,
                    generation = previous?.generation ?: 0,
                    state = "FAILED",
                    lastAttempt = attemptAt,
                    lastSuccess = previous?.lastSuccess,
                    errorCode = code,
                ),
            )
        }
        return failure("REFRESH_FAILED", "Source refresh failed; the last known catalog was preserved.")
    }

    private fun ProviderCategoryRecord.toEntity(sourceId: String, kind: String, generation: Long) = ProviderCategoryEntity(
        sourceId = sourceId,
        kind = kind,
        categoryKey = categoryId,
        providerKey = providerKey,
        name = name,
        providerOrder = providerOrder,
        available = true,
        lastSeenGeneration = generation,
    )

    private fun ProviderLiveChannelRecord.toEntity(sourceId: String, generation: Long) = LiveChannelEntity(
        channelId = channelId,
        sourceId = sourceId,
        providerKey = providerKey,
        providerStreamId = providerStreamId,
        categoryKey = categoryKey,
        name = name,
        tvgId = tvgId,
        tvgName = tvgName,
        logoUrl = logoUrl,
        streamLocator = streamLocator,
        providerOrder = providerOrder,
        available = true,
        lastSeenGeneration = generation,
    )

    private fun ProviderMovieRecord.toEntity(sourceId: String, generation: Long) = MovieEntity(
        movieId = movieId,
        sourceId = sourceId,
        providerStreamId = providerStreamId,
        categoryKey = categoryKey,
        name = name,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        extension = extension,
        rating = rating,
        providerOrder = providerOrder,
        available = true,
        lastSeenGeneration = generation,
    )

    private fun ProviderSeriesRecord.toEntity(sourceId: String, generation: Long) = SeriesEntity(
        seriesId = seriesId,
        sourceId = sourceId,
        providerSeriesId = providerSeriesId,
        categoryKey = categoryKey,
        name = name,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        description = description,
        rating = rating,
        providerOrder = providerOrder,
        available = true,
        lastSeenGeneration = generation,
    )

    private fun SourceEntity.toDomain() = Source(
        sourceId = sourceId,
        displayName = displayName,
        type = SourceType.valueOf(type),
        baseLocator = baseLocator,
        enabled = enabled,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun <T> RemoteSection<List<T>>.successSize(): Int =
        if (status == SectionStatus.SUCCESS) value?.size ?: 0 else 0

    private fun <T> failure(code: String, message: String): SourceResult<T> =
        SourceResult.Failure(SourceError(code = code, safeMessage = message))

    private data class PreparedSource(
        val entity: SourceEntity,
        val credential: SourceCredential,
    )
}
