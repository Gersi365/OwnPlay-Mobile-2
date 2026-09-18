package app.ownplay.mobile.feature.settings.backup.data

import androidx.room.withTransaction
import app.ownplay.mobile.data.db.BackupDao
import app.ownplay.mobile.data.db.CategoryPersonalizationEntity
import app.ownplay.mobile.data.db.ChannelPersonalizationEntity
import app.ownplay.mobile.data.db.CustomGroupEntity
import app.ownplay.mobile.data.db.CustomGroupMembershipEntity
import app.ownplay.mobile.data.db.LiveCategoryScopePersonalizationEntity
import app.ownplay.mobile.data.db.LiveChannelMembershipPersonalizationEntity
import app.ownplay.mobile.data.db.LiveOrganizationPreferenceEntity
import app.ownplay.mobile.data.db.MediaFavoriteEntity
import app.ownplay.mobile.data.db.OwnPlayDatabase
import app.ownplay.mobile.data.db.SourceDao
import app.ownplay.mobile.data.db.SourceEntity
import app.ownplay.mobile.feature.live.domain.LiveOrganizationMode
import app.ownplay.mobile.feature.settings.backup.domain.*
import app.ownplay.mobile.feature.settings.domain.SourceRefreshSchedule
import app.ownplay.mobile.sources.domain.SourceId
import app.ownplay.mobile.sources.domain.SourceType
import java.time.Instant

internal class RoomBackupRestoreRepository(
    private val database: OwnPlayDatabase,
    private val sourceDao: SourceDao,
    private val backupDao: BackupDao,
    private val preferences: BackupPreferenceGateway,
    private val now: () -> Instant = Instant::now,
) : BackupRestoreRepository {
    override suspend fun exportJson(): BackupExportResult = try {
        val room = database.withTransaction { captureRoomSnapshot() }
        val sourceIds = room.sources.map(SourceEntity::sourceId)
        val preferenceSnapshot = preferences.snapshot(sourceIds)
        val activeSourceId = preferenceSnapshot.activeSourceId?.takeIf { it in sourceIds }
        val liveModes = room.livePreferences.associate { it.sourceId to it.activeMode }
        val sourceSettings = room.sources.map { source ->
            BackupSourceSettings(
                sourceId = source.sourceId,
                refreshSchedule = preferenceSnapshot.refreshSchedules[source.sourceId]
                    ?: SourceRefreshSchedule.MANUAL,
                liveOrganizationMode = liveModes[source.sourceId]
                    ?.let { runCatching { LiveOrganizationMode.valueOf(it) }.getOrNull() }
                    ?: LiveOrganizationMode.PROVIDER,
            )
        }
        val payload = room.toPayload(
            activeSourceId = activeSourceId,
            globalSettings = preferenceSnapshot.globalSettings,
            sourceSettings = sourceSettings,
        )
        BackupExportResult.Success(
            BackupJsonCodec.encode(OwnPlayBackupEnvelope(createdAt = now().toString(), payload = payload)),
        )
    } catch (_: Exception) {
        BackupExportResult.Failure
    }

    override suspend fun previewRestore(json: String): BackupRestorePreview {
        val decoded = BackupJsonCodec.decode(json)
        if (decoded is BackupDecodeResult.Failure) {
            return BackupRestorePreview.Rejected(decoded.issues)
        }
        val envelope = (decoded as BackupDecodeResult.Success).envelope
        val existing = runCatching { existingSources() }.getOrElse {
            return BackupRestorePreview.StorageFailure
        }
        val plan = BackupRestorePlanner.plan(envelope.payload, existing)
        return if (plan.hasConflicts) {
            BackupRestorePreview.Conflicted(plan)
        } else {
            BackupRestorePreview.Ready(plan)
        }
    }

    override suspend fun restore(json: String): BackupRestoreResult {
        val decoded = BackupJsonCodec.decode(json)
        if (decoded is BackupDecodeResult.Failure) {
            return BackupRestoreResult.Rejected(decoded.issues)
        }
        val payload = (decoded as BackupDecodeResult.Success).envelope.payload
        val existingRows = runCatching { sourceDao.getAll() }.getOrElse {
            return BackupRestoreResult.StorageFailure
        }
        val plan = BackupRestorePlanner.plan(payload, existingRows.map { it.toExistingBackupSource() })
        if (plan.hasConflicts) return BackupRestoreResult.Conflicted(plan)
        return applyRestore(payload, plan, existingRows)
    }

    private suspend fun applyRestore(
        payload: OwnPlayBackupPayload,
        plan: BackupRestorePlan,
        existingRows: List<SourceEntity>,
    ): BackupRestoreResult {
        val mapping = plan.sourceResolutions.associate { it.backupSourceId to requireNotNull(it.targetSourceId) }
        val backupSources = payload.sources.associateBy(BackupSourceDefinition::sourceId)
        val existingById = existingRows.associateBy(SourceEntity::sourceId)
        val timestamp = now().toEpochMilli()
        val targetRows = plan.sourceResolutions.map { resolution ->
            val backup = backupSources.getValue(resolution.backupSourceId)
            when (resolution.action) {
                BackupSourceRestoreAction.MERGE_EXISTING -> {
                    val existing = existingById.getValue(requireNotNull(resolution.targetSourceId))
                    existing.copy(displayName = backup.displayName, updatedAt = timestamp)
                }
                BackupSourceRestoreAction.CREATE_DISABLED -> SourceEntity(
                    sourceId = requireNotNull(resolution.targetSourceId),
                    displayName = backup.displayName,
                    type = backup.type.name,
                    baseLocator = backup.baseLocator,
                    credentialReference = null,
                    enabled = false,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                )
                BackupSourceRestoreAction.CONFLICT -> error("Conflicted restore plan cannot be applied")
            }
        }
        val targetById = targetRows.associateBy(SourceEntity::sourceId)
        val refreshSchedules = payload.sourceSettings.associate { setting ->
            mapping.getValue(setting.sourceId) to setting.refreshSchedule
        }
        val activeTarget = payload.activeSourceId
            ?.let(mapping::get)
            ?.takeIf { targetById[it]?.enabled == true }
        val preferenceSnapshot = runCatching { preferences.snapshot(mapping.values) }.getOrElse {
            return BackupRestoreResult.StorageFailure
        }
        val prepared = runCatching { prepareRestoreRows(payload, mapping) }.getOrElse {
            return BackupRestoreResult.StorageFailure
        }

        val applied = runCatching {
            database.withTransaction {
                plan.sourceResolutions.zip(targetRows).forEach { (resolution, row) ->
                    when (resolution.action) {
                        BackupSourceRestoreAction.MERGE_EXISTING -> sourceDao.update(row)
                        BackupSourceRestoreAction.CREATE_DISABLED -> sourceDao.insert(row)
                        BackupSourceRestoreAction.CONFLICT -> error("Conflict")
                    }
                }
                prepared.apply(backupDao)
                preferences.apply(payload.globalSettings, activeTarget, refreshSchedules)
            }
        }
        if (applied.isFailure) {
            runCatching { preferences.restore(preferenceSnapshot) }
            return BackupRestoreResult.StorageFailure
        }

        val enabledSourceIds = targetRows.filter(SourceEntity::enabled).mapTo(mutableSetOf(), SourceEntity::sourceId)
        val scheduleFailures = preferences.syncRefreshSchedules(refreshSchedules, enabledSourceIds)
        return BackupRestoreResult.Success(
            BackupRestoreReport(
                mergedSources = plan.sourceResolutions.count { it.action == BackupSourceRestoreAction.MERGE_EXISTING },
                createdDisabledSources = plan.sourceResolutions.count {
                    it.action == BackupSourceRestoreAction.CREATE_DISABLED
                },
                skippedChannelPersonalization = prepared.skippedChannelPersonalization,
                skippedCustomGroups = prepared.skippedCustomGroups,
                skippedLivePlacements = prepared.skippedLivePlacements,
                skippedCustomGroupMemberships = prepared.skippedCustomGroupMemberships,
                refreshScheduleSyncFailures = scheduleFailures,
            ),
        )
    }

    private suspend fun existingSources(): List<ExistingBackupSource> =
        sourceDao.getAll().map { it.toExistingBackupSource() }

    private fun SourceEntity.toExistingBackupSource() = ExistingBackupSource(
        sourceId = sourceId,
        type = SourceType.valueOf(type),
        baseLocator = baseLocator,
    )

    private suspend fun prepareRestoreRows(
        payload: OwnPlayBackupPayload,
        mapping: Map<String, String>,
    ): PreparedRestoreRows {
        val channelIdsBySource = mapping.values.toSet().associateWith { sourceId ->
            backupDao.getChannelIds(sourceId).toSet()
        }
        val categoryRows = payload.categoryPersonalization.map { row ->
            CategoryPersonalizationEntity(
                sourceId = mapping.getValue(row.sourceId),
                kind = row.kind.name,
                categoryKey = row.categoryKey,
                hidden = row.hidden,
                manualOrder = row.manualOrder,
            )
        }
        val validChannelRows = payload.channelPersonalization.filter { row ->
            row.channelId in channelIdsBySource.getValue(mapping.getValue(row.sourceId))
        }
        val channelRows = validChannelRows.map { row ->
            ChannelPersonalizationEntity(
                channelId = row.channelId,
                favorite = row.favorite,
                hidden = row.hidden,
                localName = row.localName,
                localLogo = row.localLogo,
                manualOrder = row.manualOrder,
            )
        }
        val favoriteRows = payload.mediaFavorites.map { row ->
            MediaFavoriteEntity(
                sourceId = mapping.getValue(row.sourceId),
                mediaKind = row.mediaKind.name,
                contentId = row.contentId,
                addedAt = row.addedAt,
            )
        }
        val livePreferenceRows = payload.sourceSettings.map { row ->
            LiveOrganizationPreferenceEntity(
                sourceId = mapping.getValue(row.sourceId),
                activeMode = row.liveOrganizationMode.name,
            )
        }
        val liveCategoryRows = payload.liveCategoryPersonalization.map { row ->
            LiveCategoryScopePersonalizationEntity(
                sourceId = mapping.getValue(row.sourceId),
                organizationMode = row.organizationMode.name,
                categoryId = row.categoryId,
                hidden = row.hidden,
                manualOrder = row.manualOrder,
            )
        }
        val validPlacementRows = payload.livePlacementOverrides.filter { row ->
            row.channelId in channelIdsBySource.getValue(mapping.getValue(row.sourceId))
        }
        val placementRows = validPlacementRows.map { row ->
            LiveChannelMembershipPersonalizationEntity(
                sourceId = mapping.getValue(row.sourceId),
                organizationMode = "OWNPLAY_OVERRIDE",
                categoryId = row.categoryId,
                channelId = row.channelId,
                hidden = row.hidden,
                manualOrder = row.manualOrder,
            )
        }
        val acceptedGroups = mutableListOf<BackupCustomGroup>()
        var skippedGroups = 0
        payload.customGroups.forEach { row ->
            val targetSourceId = mapping.getValue(row.sourceId)
            val existingSourceId = backupDao.getGroupSourceId(row.groupId)
            if (existingSourceId == null || existingSourceId == targetSourceId) {
                acceptedGroups += row
            } else {
                skippedGroups += 1
            }
        }
        val groupSourceById = acceptedGroups.associate { it.groupId to mapping.getValue(it.sourceId) }
        val groupRows = acceptedGroups.map { row ->
            CustomGroupEntity(
                groupId = row.groupId,
                sourceId = mapping.getValue(row.sourceId),
                name = row.name,
                manualOrder = row.manualOrder,
            )
        }
        val validMemberships = payload.customGroupMemberships.filter { row ->
            val sourceId = groupSourceById[row.groupId] ?: return@filter false
            row.channelId in channelIdsBySource.getValue(sourceId)
        }
        val membershipRows = validMemberships.map { row ->
            CustomGroupMembershipEntity(
                groupId = row.groupId,
                channelId = row.channelId,
                manualOrder = row.manualOrder,
            )
        }
        return PreparedRestoreRows(
            categoryPersonalization = categoryRows,
            channelPersonalization = channelRows,
            mediaFavorites = favoriteRows,
            livePreferences = livePreferenceRows,
            liveCategoryPersonalization = liveCategoryRows,
            livePlacementOverrides = placementRows,
            customGroups = groupRows,
            customGroupMemberships = membershipRows,
            skippedChannelPersonalization = payload.channelPersonalization.size - validChannelRows.size,
            skippedCustomGroups = skippedGroups,
            skippedLivePlacements = payload.livePlacementOverrides.size - validPlacementRows.size,
            skippedCustomGroupMemberships = payload.customGroupMemberships.size - validMemberships.size,
        )
    }

    private data class PreparedRestoreRows(
        val categoryPersonalization: List<CategoryPersonalizationEntity>,
        val channelPersonalization: List<ChannelPersonalizationEntity>,
        val mediaFavorites: List<MediaFavoriteEntity>,
        val livePreferences: List<LiveOrganizationPreferenceEntity>,
        val liveCategoryPersonalization: List<LiveCategoryScopePersonalizationEntity>,
        val livePlacementOverrides: List<LiveChannelMembershipPersonalizationEntity>,
        val customGroups: List<CustomGroupEntity>,
        val customGroupMemberships: List<CustomGroupMembershipEntity>,
        val skippedChannelPersonalization: Int,
        val skippedCustomGroups: Int,
        val skippedLivePlacements: Int,
        val skippedCustomGroupMemberships: Int,
    ) {
        suspend fun apply(dao: BackupDao) {
            if (categoryPersonalization.isNotEmpty()) dao.upsertCategoryPersonalization(categoryPersonalization)
            if (channelPersonalization.isNotEmpty()) dao.upsertChannelPersonalization(channelPersonalization)
            if (mediaFavorites.isNotEmpty()) dao.upsertMediaFavorites(mediaFavorites)
            if (livePreferences.isNotEmpty()) dao.upsertLivePreferences(livePreferences)
            if (liveCategoryPersonalization.isNotEmpty()) {
                dao.upsertLiveCategoryPersonalization(liveCategoryPersonalization)
            }
            if (livePlacementOverrides.isNotEmpty()) dao.upsertManualPlacementOverrides(livePlacementOverrides)
            if (customGroups.isNotEmpty()) dao.upsertCustomGroups(customGroups)
            if (customGroupMemberships.isNotEmpty()) dao.upsertCustomGroupMemberships(customGroupMemberships)
        }
    }

    private suspend fun captureRoomSnapshot(): RoomBackupSnapshot = RoomBackupSnapshot(
        sources = sourceDao.getAll(),
        categoryPersonalization = backupDao.getCategoryPersonalization(),
        channelPersonalization = backupDao.getChannelPersonalization(),
        mediaFavorites = backupDao.getMediaFavorites(),
        livePreferences = backupDao.getLivePreferences(),
        liveCategoryPersonalization = backupDao.getLiveCategoryPersonalization(),
        livePlacementOverrides = backupDao.getManualPlacementOverrides(),
        customGroups = backupDao.getCustomGroups(),
        customGroupMemberships = backupDao.getCustomGroupMemberships(),
    )

    private data class RoomBackupSnapshot(
        val sources: List<SourceEntity>,
        val categoryPersonalization: List<CategoryPersonalizationEntity>,
        val channelPersonalization: List<app.ownplay.mobile.data.db.BackupChannelPersonalizationRow>,
        val mediaFavorites: List<MediaFavoriteEntity>,
        val livePreferences: List<LiveOrganizationPreferenceEntity>,
        val liveCategoryPersonalization: List<LiveCategoryScopePersonalizationEntity>,
        val livePlacementOverrides: List<LiveChannelMembershipPersonalizationEntity>,
        val customGroups: List<CustomGroupEntity>,
        val customGroupMemberships: List<CustomGroupMembershipEntity>,
    )

    private fun RoomBackupSnapshot.toPayload(
        activeSourceId: String?,
        globalSettings: BackupGlobalSettings,
        sourceSettings: List<BackupSourceSettings>,
    ): OwnPlayBackupPayload = OwnPlayBackupPayload(
        sources = sources.map { row ->
            BackupSourceDefinition(
                sourceId = row.sourceId,
                type = SourceType.valueOf(row.type),
                displayName = row.displayName,
                baseLocator = row.baseLocator,
                enabled = row.enabled,
            )
        },
        activeSourceId = activeSourceId,
        globalSettings = globalSettings,
        sourceSettings = sourceSettings,
        categoryPersonalization = categoryPersonalization.map { row ->
            BackupCategoryPersonalization(
                sourceId = row.sourceId,
                kind = BackupCatalogKind.valueOf(row.kind),
                categoryKey = row.categoryKey,
                hidden = row.hidden,
                manualOrder = row.manualOrder,
            )
        },
        channelPersonalization = channelPersonalization.map { row ->
            BackupChannelPersonalization(
                sourceId = row.sourceId,
                channelId = row.channelId,
                favorite = row.favorite,
                hidden = row.hidden,
                localName = row.localName,
                localLogo = row.localLogo,
                manualOrder = row.manualOrder,
            )
        },
        mediaFavorites = mediaFavorites.map { row ->
            BackupMediaFavorite(
                sourceId = row.sourceId,
                mediaKind = BackupMediaKind.valueOf(row.mediaKind),
                contentId = row.contentId,
                addedAt = row.addedAt,
            )
        },
        liveCategoryPersonalization = liveCategoryPersonalization.map { row ->
            BackupLiveCategoryPersonalization(
                sourceId = row.sourceId,
                organizationMode = LiveOrganizationMode.valueOf(row.organizationMode),
                categoryId = row.categoryId,
                hidden = row.hidden,
                manualOrder = row.manualOrder,
            )
        },
        livePlacementOverrides = livePlacementOverrides.map { row ->
            BackupLivePlacementOverride(
                sourceId = row.sourceId,
                categoryId = row.categoryId,
                channelId = row.channelId,
                hidden = row.hidden,
                manualOrder = row.manualOrder,
            )
        },
        customGroups = customGroups.map { row ->
            BackupCustomGroup(
                sourceId = row.sourceId,
                groupId = row.groupId,
                name = row.name,
                manualOrder = row.manualOrder,
            )
        },
        customGroupMemberships = customGroupMemberships.map { row ->
            BackupCustomGroupMembership(
                groupId = row.groupId,
                channelId = row.channelId,
                manualOrder = row.manualOrder,
            )
        },
    )
}
