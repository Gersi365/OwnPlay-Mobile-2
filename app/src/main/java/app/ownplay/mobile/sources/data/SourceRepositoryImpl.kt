package app.ownplay.mobile.sources.data

import app.ownplay.mobile.data.db.RefreshStateDao
import app.ownplay.mobile.data.db.RefreshStateEntity
import app.ownplay.mobile.data.db.SourceDao
import app.ownplay.mobile.data.db.SourceEntity
import app.ownplay.mobile.data.prefs.ActiveSourceSelectionStore
import app.ownplay.mobile.data.security.CredentialInputPolicy
import app.ownplay.mobile.data.security.CredentialStore
import app.ownplay.mobile.data.security.SourceSecret
import app.ownplay.mobile.sources.domain.ConnectionValidation
import app.ownplay.mobile.sources.domain.SourceConnectionSecurityPolicy
import app.ownplay.mobile.sources.domain.SourceId
import app.ownplay.mobile.sources.domain.SourceInput
import app.ownplay.mobile.sources.domain.SourceMutationRejection
import app.ownplay.mobile.sources.domain.SourceMutationResult
import app.ownplay.mobile.sources.domain.SourceRefreshFailureCategory
import app.ownplay.mobile.sources.domain.SourceRefreshResult
import app.ownplay.mobile.sources.domain.SourceRepository
import app.ownplay.mobile.sources.domain.SourceSummary
import app.ownplay.mobile.sources.domain.SourceType
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SourceRepositoryImpl(
    private val sourceDao: SourceDao,
    private val refreshStateDao: RefreshStateDao,
    private val activeSourceStore: ActiveSourceSelectionStore,
    private val credentialStore: CredentialStore,
    private val catalogLoader: SourceCatalogLoader,
    private val catalogRefreshStore: CatalogRefreshStore,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val newSourceId: () -> SourceId = { SourceId(UUID.randomUUID().toString()) },
) : SourceRepository {
    private val refreshMutex = Mutex()

    override fun observeSources(): Flow<List<SourceSummary>> = combine(
        sourceDao.observeAll(),
        refreshStateDao.observeAll(),
    ) { sources, refreshStates ->
        val lastSuccessBySource = refreshStates.associate { it.sourceId to it.lastSuccess }
        sources.map { source ->
            source.toSummary(lastSuccessBySource[source.sourceId])
        }
    }

    override fun observeActiveSource(): Flow<SourceSummary?> = combine(
        observeSources(),
        activeSourceStore.selectedSourceId,
    ) { sources, selectedSourceId ->
        sources.firstOrNull { source ->
            source.enabled && source.sourceId.value == selectedSourceId
        } ?: sources.firstOrNull { source -> source.enabled }
    }

    override suspend fun addSource(input: SourceInput): SourceMutationResult {
        val displayName = CredentialInputPolicy.normalizeDisplayName(input.displayName)
            ?: return SourceMutationResult.Rejected(SourceMutationRejection.INVALID_NAME)
        val prepared = prepare(input)
            ?: return when (input) {
                is SourceInput.Xtream -> if (
                    !CredentialInputPolicy.isValidCredential(input.username) ||
                    !CredentialInputPolicy.isValidCredential(input.password)
                ) {
                    SourceMutationResult.Rejected(SourceMutationRejection.INVALID_CREDENTIALS)
                } else {
                    SourceMutationResult.Rejected(SourceMutationRejection.INVALID_CONNECTION)
                }

                is SourceInput.M3u -> SourceMutationResult.Rejected(SourceMutationRejection.INVALID_CONNECTION)
            }

        val existingSources = try {
            sourceDao.getAll()
        } catch (_: Exception) {
            return SourceMutationResult.Rejected(SourceMutationRejection.STORAGE_FAILURE)
        }
        if (existingSources.any { row ->
                row.type == prepared.type.name && row.baseLocator == prepared.safeLocator
            }
        ) {
            return SourceMutationResult.Rejected(SourceMutationRejection.DUPLICATE_SOURCE)
        }

        val priorSelectedSourceId = try {
            activeSourceStore.currentSelectedSourceId()
        } catch (_: Exception) {
            return SourceMutationResult.Rejected(SourceMutationRejection.STORAGE_FAILURE)
        }
        val sourceId = newSourceId()
        val timestamp = nowMillis()
        val entity = SourceEntity(
            sourceId = sourceId.value,
            displayName = displayName,
            type = prepared.type.name,
            baseLocator = prepared.safeLocator,
            credentialReference = sourceId.value,
            enabled = true,
            createdAt = timestamp,
            updatedAt = timestamp,
        )

        return try {
            credentialStore.put(sourceId, prepared.secret)
            try {
                sourceDao.insert(entity)
            } catch (exception: Exception) {
                runCatching { credentialStore.delete(sourceId) }
                throw exception
            }

            if (existingSources.isEmpty() && priorSelectedSourceId == null) {
                try {
                    activeSourceStore.setSelectedSourceId(sourceId.value)
                } catch (exception: Exception) {
                    runCatching { sourceDao.delete(sourceId.value) }
                    runCatching { credentialStore.delete(sourceId) }
                    throw exception
                }
            }
            SourceMutationResult.Success(sourceId)
        } catch (_: Exception) {
            SourceMutationResult.Rejected(SourceMutationRejection.STORAGE_FAILURE)
        }
    }

    override suspend fun setActiveSource(sourceId: SourceId): Boolean {
        val source = try {
            sourceDao.get(sourceId.value)
        } catch (_: Exception) {
            return false
        } ?: return false
        if (!source.enabled) return false

        return try {
            activeSourceStore.setSelectedSourceId(sourceId.value)
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun refreshSource(sourceId: SourceId): SourceRefreshResult =
        refreshMutex.withLock {
            val source = try {
                sourceDao.get(sourceId.value)
            } catch (_: Exception) {
                return@withLock storageFailure()
            } ?: return@withLock SourceRefreshResult.Failure(
                category = SourceRefreshFailureCategory.UNKNOWN,
                safeMessage = "Source is unavailable.",
            )
            if (!source.enabled) {
                return@withLock SourceRefreshResult.Failure(
                    category = SourceRefreshFailureCategory.UNKNOWN,
                    safeMessage = "Source is disabled.",
                )
            }

            val sourceType = try {
                SourceType.valueOf(source.type)
            } catch (_: Exception) {
                return@withLock SourceRefreshResult.Failure(
                    category = SourceRefreshFailureCategory.UNKNOWN,
                    safeMessage = "Source type is unavailable.",
                )
            }

            val secret = try {
                credentialStore.get(sourceId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return@withLock storageFailure()
            } ?: return@withLock SourceRefreshResult.Failure(
                category = SourceRefreshFailureCategory.AUTHENTICATION,
                safeMessage = "Source credentials are unavailable.",
            )

            val previous = try {
                refreshStateDao.get(sourceId.value)
            } catch (_: Exception) {
                return@withLock storageFailure()
            }
            val attemptAt = nowMillis()
            val generation = (previous?.generation ?: 0L) + 1L

            val snapshot = try {
                catalogLoader.load(
                    sourceId = sourceId,
                    sourceType = sourceType,
                    baseLocator = source.baseLocator,
                    secret = secret,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: CatalogLoadException) {
                return@withLock recordRefreshFailure(
                    sourceId = sourceId,
                    previous = previous,
                    attemptAt = attemptAt,
                    category = error.category,
                )
            } catch (_: Exception) {
                return@withLock recordRefreshFailure(
                    sourceId = sourceId,
                    previous = previous,
                    attemptAt = attemptAt,
                    category = SourceRefreshFailureCategory.UNKNOWN,
                )
            }

            return@withLock try {
                catalogRefreshStore.commitSuccessfulRefresh(
                    sourceId = sourceId,
                    sourceType = sourceType,
                    generation = generation,
                    snapshot = snapshot,
                    attemptAtEpochMs = attemptAt,
                    completedAtEpochMs = nowMillis(),
                )
                SourceRefreshResult.Success
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                recordRefreshFailure(
                    sourceId = sourceId,
                    previous = previous,
                    attemptAt = attemptAt,
                    category = SourceRefreshFailureCategory.STORAGE,
                )
            }
        }

    override suspend fun removeSource(sourceId: SourceId): Boolean {
        val existing = try {
            sourceDao.get(sourceId.value)
        } catch (_: Exception) {
            return false
        } ?: return false
        val priorSelectedSourceId = try {
            activeSourceStore.currentSelectedSourceId()
        } catch (_: Exception) {
            return false
        }
        val sources = try {
            sourceDao.getAll()
        } catch (_: Exception) {
            return false
        }
        val priorSecret = try {
            credentialStore.get(sourceId)
        } catch (_: Exception) {
            return false
        }

        val removingPersistedActiveSource = priorSelectedSourceId == existing.sourceId
        val fallbackSourceId = if (removingPersistedActiveSource) {
            sources.firstOrNull { row ->
                row.sourceId != existing.sourceId && row.enabled
            }?.sourceId
        } else {
            priorSelectedSourceId
        }
        var activeSelectionChanged = false

        return try {
            if (removingPersistedActiveSource) {
                activeSourceStore.setSelectedSourceId(fallbackSourceId)
                activeSelectionChanged = true
            }
            credentialStore.delete(sourceId)
            check(sourceDao.delete(sourceId.value) == 1) {
                "Source row was not deleted"
            }
            true
        } catch (_: Exception) {
            priorSecret?.let { secret ->
                runCatching { credentialStore.put(sourceId, secret) }
            }
            if (activeSelectionChanged) {
                runCatching { activeSourceStore.setSelectedSourceId(priorSelectedSourceId) }
            }
            false
        }
    }

    private suspend fun recordRefreshFailure(
        sourceId: SourceId,
        previous: RefreshStateEntity?,
        attemptAt: Long,
        category: SourceRefreshFailureCategory,
    ): SourceRefreshResult {
        return try {
            refreshStateDao.upsert(
                RefreshStateEntity(
                    sourceId = sourceId.value,
                    generation = previous?.generation ?: 0L,
                    state = "FAILED",
                    lastAttempt = attemptAt,
                    lastSuccess = previous?.lastSuccess,
                    errorCode = category.name,
                ),
            )
            SourceRefreshResult.Failure(
                category = category,
                safeMessage = category.safeMessage(),
            )
        } catch (_: Exception) {
            storageFailure()
        }
    }

    private fun SourceRefreshFailureCategory.safeMessage(): String = when (this) {
        SourceRefreshFailureCategory.AUTHENTICATION -> "Source authentication failed."
        SourceRefreshFailureCategory.NETWORK -> "Source network request failed."
        SourceRefreshFailureCategory.TIMEOUT -> "Source request timed out."
        SourceRefreshFailureCategory.INVALID_PAYLOAD -> "Source returned invalid catalog data."
        SourceRefreshFailureCategory.PROVIDER -> "Source provider rejected the refresh."
        SourceRefreshFailureCategory.STORAGE -> "Source refresh could not be saved."
        SourceRefreshFailureCategory.UNKNOWN -> "Source refresh failed."
    }

    private fun storageFailure(): SourceRefreshResult.Failure = SourceRefreshResult.Failure(
        category = SourceRefreshFailureCategory.STORAGE,
        safeMessage = SourceRefreshFailureCategory.STORAGE.safeMessage(),
    )

    private fun prepare(input: SourceInput): PreparedSource? {
        return when (input) {
            is SourceInput.Xtream -> {
                if (
                    !CredentialInputPolicy.isValidCredential(input.username) ||
                    !CredentialInputPolicy.isValidCredential(input.password)
                ) {
                    null
                } else {
                    val normalizedBaseUrl = SourceConnectionSecurityPolicy
                        .normalizeXtreamBaseUrl(input.serverUrl)
                        .normalizedUrlOrNull()
                        ?: return null
                    PreparedSource(
                        type = SourceType.XTREAM,
                        safeLocator = normalizedBaseUrl,
                        secret = SourceSecret.Xtream(
                            username = input.username,
                            password = input.password,
                        ),
                    )
                }
            }

            is SourceInput.M3u -> {
                val playlistUrl = SourceConnectionSecurityPolicy
                    .normalizeRemoteMediaUrl(input.playlistUrl)
                    .normalizedUrlOrNull()
                    ?: return null
                val epgUrl = input.epgUrl
                    ?.takeIf(String::isNotBlank)
                    ?.let { raw ->
                        SourceConnectionSecurityPolicy
                            .normalizeRemoteMediaUrl(raw)
                            .normalizedUrlOrNull()
                            ?: return null
                    }
                PreparedSource(
                    type = SourceType.M3U,
                    safeLocator = SourceLocatorPolicy.redact(playlistUrl),
                    secret = SourceSecret.M3uRemote(
                        playlistUrl = playlistUrl,
                        epgUrl = epgUrl,
                    ),
                )
            }
        }
    }

    private fun SourceEntity.toSummary(lastSuccessfulRefreshAtEpochMs: Long?): SourceSummary =
        SourceSummary(
            sourceId = SourceId(sourceId),
            type = SourceType.valueOf(type),
            displayName = displayName,
            connectionLabel = SourceLocatorPolicy.connectionLabel(baseLocator),
            enabled = enabled,
            lastSuccessfulRefreshAtEpochMs = lastSuccessfulRefreshAtEpochMs,
        )

    private fun ConnectionValidation.normalizedUrlOrNull(): String? =
        (this as? ConnectionValidation.Valid)?.normalizedUrl

    private data class PreparedSource(
        val type: SourceType,
        val safeLocator: String,
        val secret: SourceSecret,
    )
}
