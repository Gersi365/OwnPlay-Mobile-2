package app.ownplay.mobile.feature.settings.data

import android.content.Context
import androidx.room.withTransaction
import app.ownplay.mobile.data.db.BackupDao
import app.ownplay.mobile.data.db.ChannelPersonalizationEntity
import app.ownplay.mobile.data.db.CustomGroupEntity
import app.ownplay.mobile.data.db.CustomGroupMembershipEntity
import app.ownplay.mobile.data.db.MediaFavoriteEntity
import app.ownplay.mobile.data.db.OwnPlayDatabase
import app.ownplay.mobile.data.db.PlaybackProgressEntity
import app.ownplay.mobile.data.db.SourceDao
import app.ownplay.mobile.data.db.SourceEntity
import app.ownplay.mobile.data.prefs.ActiveSourcePreferences
import app.ownplay.mobile.data.prefs.SettingsPreferences
import app.ownplay.mobile.feature.settings.domain.BackupExport
import app.ownplay.mobile.feature.settings.domain.BackupRepository
import app.ownplay.mobile.feature.settings.domain.BackupResult
import app.ownplay.mobile.feature.settings.domain.RestoreSummary
import app.ownplay.mobile.sources.data.SourceLocatorPolicy
import app.ownplay.mobile.sources.domain.SourceType
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BackupRepositoryImpl(
    context: Context,
    private val database: OwnPlayDatabase,
    private val sourceDao: SourceDao,
    private val backupDao: BackupDao,
    private val activeSourcePreferences: ActiveSourcePreferences,
    private val settingsPreferences: SettingsPreferences,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : BackupRepository {
    private val pendingStore = PendingRestoreStore(context.applicationContext.filesDir)
    private val restoreMutex = Mutex()

    override suspend fun createBackup(): BackupResult<BackupExport> = try {
        val sourceRows = sourceDao.getAll()
        val document = BackupDocument(
            generatedAt = nowMillis(),
            activeSourceId = activeSourcePreferences.currentSelectedSourceId(),
            settings = settingsPreferences.settings.first(),
            sources = sourceRows.map { source ->
                BackupSourceRecord(
                    sourceId = source.sourceId,
                    displayName = source.displayName,
                    type = source.type,
                    safeBaseLocator = source.baseLocator.takeIf { source.type == SourceType.XTREAM.name },
                    enabled = source.enabled,
                    createdAt = source.createdAt,
                )
            },
            channelPersonalization = backupDao.getChannelPersonalization().map { row ->
                BackupChannelPersonalizationRecord(
                    sourceId = row.sourceId,
                    channelId = row.channelId,
                    favorite = row.favorite,
                    hidden = row.hidden,
                    localName = row.localName,
                    manualOrder = row.manualOrder,
                )
            },
            groups = backupDao.getCustomGroups().map { row ->
                BackupGroupRecord(
                    groupId = row.groupId,
                    sourceId = row.sourceId,
                    name = row.name,
                    manualOrder = row.manualOrder,
                )
            },
            memberships = backupDao.getCustomGroupMemberships().map { row ->
                BackupMembershipRecord(
                    sourceId = row.sourceId,
                    groupId = row.groupId,
                    channelId = row.channelId,
                    manualOrder = row.manualOrder,
                )
            },
            favorites = backupDao.getMediaFavorites().map { row ->
                BackupFavoriteRecord(
                    sourceId = row.sourceId,
                    mediaKind = row.mediaKind,
                    contentId = row.contentId,
                    addedAt = row.addedAt,
                )
            },
            progress = backupDao.getPlaybackProgress().map { row ->
                BackupProgressRecord(
                    sourceId = row.sourceId,
                    mediaKind = row.mediaKind,
                    contentId = row.contentId,
                    positionMs = row.positionMs,
                    durationMs = row.durationMs,
                    completed = row.completed,
                    updatedAt = row.updatedAt,
                )
            },
        )
        BackupResult.Success(
            BackupExport(
                fileName = "ownplay-backup-v${BackupCodec.VERSION}.json",
                content = BackupCodec.encode(document),
            ),
        )
    } catch (_: Exception) {
        failure("BACKUP_CREATE_FAILED", "Backup could not be created.")
    }

    override suspend fun restoreBackup(content: String): BackupResult<RestoreSummary> = restoreMutex.withLock {
        val document = try {
            BackupCodec.decode(content)
        } catch (_: Exception) {
            return@withLock failure("BACKUP_INVALID", "The selected file is not a supported OwnPlay backup.")
        }

        val preparedSources = try {
            document.sources.associateBy { it.sourceId }.values.map { record ->
                require(record.sourceId.isNotBlank())
                require(record.displayName.isNotBlank())
                require(record.type == SourceType.XTREAM.name || record.type == SourceType.M3U.name)
                val safeLocator = when (record.type) {
                    SourceType.XTREAM.name -> SourceLocatorPolicy.normalizeXtream(
                        requireNotNull(record.safeBaseLocator) { "Xtream base URL missing" },
                    )
                    SourceType.M3U.name -> RECONNECT_REQUIRED_LOCATOR
                    else -> error("Unsupported source type")
                }
                record to safeLocator
            }
        } catch (_: Exception) {
            return@withLock failure("BACKUP_INVALID_SOURCE", "Backup contains an invalid source profile.")
        }

        var sourcesAdded = 0
        var sourcesUpdated = 0
        var groupsRestored = 0
        var favoritesRestored = 0
        var progressRestored = 0
        var personalizationRestored = 0
        val pendingPersonalization = mutableListOf<BackupChannelPersonalizationRecord>()
        val pendingMemberships = mutableListOf<BackupMembershipRecord>()

        try {
            database.withTransaction {
                preparedSources.forEach { (record, safeLocator) ->
                    val existing = sourceDao.get(record.sourceId)
                    when {
                        existing == null -> {
                            sourceDao.insert(
                                SourceEntity(
                                    sourceId = record.sourceId,
                                    displayName = record.displayName.trim(),
                                    type = record.type,
                                    baseLocator = safeLocator,
                                    credentialReference = null,
                                    enabled = false,
                                    createdAt = record.createdAt.coerceAtLeast(0),
                                    updatedAt = nowMillis(),
                                ),
                            )
                            sourcesAdded += 1
                        }
                        existing.type == record.type -> {
                            sourceDao.update(
                                existing.copy(
                                    displayName = record.displayName.trim(),
                                    baseLocator = BackupSourceRestorePolicy.resolveBaseLocator(
                                        sourceType = record.type,
                                        existingBaseLocator = existing.baseLocator,
                                        existingCredentialReference = existing.credentialReference,
                                        importedSafeLocator = safeLocator,
                                    ),
                                    enabled = record.enabled && existing.credentialReference != null,
                                    updatedAt = nowMillis(),
                                ),
                            )
                            sourcesUpdated += 1
                        }
                        else -> error("Source identity collision")
                    }
                }

                val validSourceIds = sourceDao.getAll().mapTo(mutableSetOf()) { it.sourceId }
                val groups = document.groups
                    .filter { it.sourceId in validSourceIds }
                    .map { record ->
                        CustomGroupEntity(
                            groupId = record.groupId,
                            sourceId = record.sourceId,
                            name = record.name,
                            manualOrder = record.manualOrder,
                        )
                    }
                backupDao.upsertCustomGroups(groups)
                groupsRestored = groups.size

                val favorites = document.favorites
                    .filter { it.sourceId in validSourceIds }
                    .map { record ->
                        MediaFavoriteEntity(
                            sourceId = record.sourceId,
                            mediaKind = record.mediaKind,
                            contentId = record.contentId,
                            addedAt = record.addedAt.coerceAtLeast(0),
                        )
                    }
                backupDao.upsertMediaFavorites(favorites)
                favoritesRestored = favorites.size

                val progress = document.progress
                    .filter { it.sourceId in validSourceIds }
                    .map { record ->
                        PlaybackProgressEntity(
                            sourceId = record.sourceId,
                            mediaKind = record.mediaKind,
                            contentId = record.contentId,
                            positionMs = record.positionMs.coerceAtLeast(0),
                            durationMs = record.durationMs.coerceAtLeast(0),
                            completed = record.completed,
                            updatedAt = record.updatedAt.coerceAtLeast(0),
                        )
                    }
                backupDao.upsertPlaybackProgress(progress)
                progressRestored = progress.size

                document.channelPersonalization
                    .filter { it.sourceId in validSourceIds }
                    .forEach { record ->
                        if (backupDao.hasLiveChannel(record.sourceId, record.channelId)) {
                            backupDao.upsertChannelPersonalization(record.toEntity())
                            personalizationRestored += 1
                        } else {
                            pendingPersonalization += record
                        }
                    }

                document.memberships
                    .filter { it.sourceId in validSourceIds }
                    .forEach { record ->
                        if (
                            backupDao.hasLiveChannel(record.sourceId, record.channelId) &&
                            backupDao.hasCustomGroup(record.sourceId, record.groupId)
                        ) {
                            backupDao.upsertCustomGroupMembership(record.toEntity())
                            personalizationRestored += 1
                        } else {
                            pendingMemberships += record
                        }
                    }
            }

            settingsPreferences.replace(document.settings)
            activeSourcePreferences.setSelectedSourceId(document.activeSourceId)
            pendingStore.merge(
                PendingRestoreDocument(
                    channelPersonalization = pendingPersonalization,
                    memberships = pendingMemberships,
                ),
            )

            BackupResult.Success(
                RestoreSummary(
                    sourcesAdded = sourcesAdded,
                    sourcesUpdated = sourcesUpdated,
                    groupsRestored = groupsRestored,
                    favoritesRestored = favoritesRestored,
                    progressRestored = progressRestored,
                    personalizationRestored = personalizationRestored,
                    pendingItems = pendingPersonalization.size + pendingMemberships.size,
                ),
            )
        } catch (_: Exception) {
            failure("BACKUP_RESTORE_FAILED", "Backup could not be restored safely.")
        }
    }

    override suspend fun applyPendingForSource(sourceId: String): BackupResult<Int> = restoreMutex.withLock {
        if (sourceId.isBlank()) return@withLock failure("SOURCE_ID_INVALID", "Source identity is invalid.")
        val pending = try {
            pendingStore.read()
        } catch (_: Exception) {
            return@withLock failure("PENDING_RESTORE_INVALID", "Pending restore data could not be read.")
        }
        if (pending.channelPersonalization.none { it.sourceId == sourceId } &&
            pending.memberships.none { it.sourceId == sourceId }
        ) {
            return@withLock BackupResult.Success(0)
        }

        var applied = 0
        val remainingPersonalization = mutableListOf<BackupChannelPersonalizationRecord>()
        val remainingMemberships = mutableListOf<BackupMembershipRecord>()
        try {
            database.withTransaction {
                pending.channelPersonalization.forEach { record ->
                    if (record.sourceId != sourceId) {
                        remainingPersonalization += record
                    } else if (backupDao.hasLiveChannel(record.sourceId, record.channelId)) {
                        backupDao.upsertChannelPersonalization(record.toEntity())
                        applied += 1
                    } else {
                        remainingPersonalization += record
                    }
                }
                pending.memberships.forEach { record ->
                    if (record.sourceId != sourceId) {
                        remainingMemberships += record
                    } else if (
                        backupDao.hasLiveChannel(record.sourceId, record.channelId) &&
                        backupDao.hasCustomGroup(record.sourceId, record.groupId)
                    ) {
                        backupDao.upsertCustomGroupMembership(record.toEntity())
                        applied += 1
                    } else {
                        remainingMemberships += record
                    }
                }
            }
            pendingStore.write(
                PendingRestoreDocument(
                    channelPersonalization = remainingPersonalization,
                    memberships = remainingMemberships,
                ),
            )
            BackupResult.Success(applied)
        } catch (_: Exception) {
            failure("PENDING_RESTORE_FAILED", "Pending personalization could not be applied.")
        }
    }

    private fun BackupChannelPersonalizationRecord.toEntity() = ChannelPersonalizationEntity(
        channelId = channelId,
        favorite = favorite,
        hidden = hidden,
        localName = localName,
        localLogo = null,
        manualOrder = manualOrder,
    )

    private fun BackupMembershipRecord.toEntity() = CustomGroupMembershipEntity(
        groupId = groupId,
        channelId = channelId,
        manualOrder = manualOrder,
    )

    private fun <T> failure(code: String, message: String): BackupResult<T> =
        BackupResult.Failure(code = code, safeMessage = message)

    private companion object {
        const val RECONNECT_REQUIRED_LOCATOR = "https://reconnect.invalid/ownplay"
    }
}

private class PendingRestoreStore(
    filesDir: File,
) {
    private val directory = File(filesDir, "restore")
    private val file = File(directory, "pending-v1.json")

    fun read(): PendingRestoreDocument {
        if (!file.exists()) return PendingRestoreDocument(emptyList(), emptyList())
        return BackupCodec.decodePending(file.readText())
    }

    fun merge(additional: PendingRestoreDocument) {
        val current = read()
        write(
            PendingRestoreDocument(
                channelPersonalization = (current.channelPersonalization + additional.channelPersonalization)
                    .distinctBy { it.sourceId to it.channelId },
                memberships = (current.memberships + additional.memberships)
                    .distinctBy { Triple(it.sourceId, it.groupId, it.channelId) },
            ),
        )
    }

    fun write(document: PendingRestoreDocument) {
        if (document.channelPersonalization.isEmpty() && document.memberships.isEmpty()) {
            if (file.exists()) file.delete()
            return
        }
        if (!directory.exists() && !directory.mkdirs()) {
            error("Restore directory could not be created")
        }
        val temp = File(directory, "pending-v1.tmp")
        temp.writeText(BackupCodec.encodePending(document))
        if (file.exists() && !file.delete()) {
            temp.delete()
            error("Pending restore file could not be replaced")
        }
        if (!temp.renameTo(file)) {
            temp.delete()
            error("Pending restore file could not be committed")
        }
    }
}
