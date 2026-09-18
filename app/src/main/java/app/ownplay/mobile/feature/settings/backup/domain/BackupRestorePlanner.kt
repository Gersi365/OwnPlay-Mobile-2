package app.ownplay.mobile.feature.settings.backup.domain

import app.ownplay.mobile.sources.domain.SourceType

data class ExistingBackupSource(
    val sourceId: String,
    val type: SourceType,
    val baseLocator: String,
)

enum class BackupSourceRestoreAction {
    MERGE_EXISTING,
    CREATE_DISABLED,
    CONFLICT,
}

data class BackupSourceResolution(
    val backupSourceId: String,
    val targetSourceId: String?,
    val action: BackupSourceRestoreAction,
)

data class BackupRestorePlan(
    val sourceResolutions: List<BackupSourceResolution>,
) {
    val hasConflicts: Boolean
        get() = sourceResolutions.any { it.action == BackupSourceRestoreAction.CONFLICT }
}

object BackupRestorePlanner {
    fun plan(
        backup: OwnPlayBackupPayload,
        existingSources: List<ExistingBackupSource>,
    ): BackupRestorePlan {
        val byId = existingSources.associateBy(ExistingBackupSource::sourceId)
        val byConnection = existingSources.groupBy { it.type to it.baseLocator }

        return BackupRestorePlan(
            sourceResolutions = backup.sources.map { source ->
                val sameId = byId[source.sourceId]
                when {
                    sameId != null && sameId.type == source.type && sameId.baseLocator == source.baseLocator ->
                        BackupSourceResolution(
                            backupSourceId = source.sourceId,
                            targetSourceId = sameId.sourceId,
                            action = BackupSourceRestoreAction.MERGE_EXISTING,
                        )

                    sameId != null -> BackupSourceResolution(
                        backupSourceId = source.sourceId,
                        targetSourceId = null,
                        action = BackupSourceRestoreAction.CONFLICT,
                    )

                    else -> {
                        val sameConnection = byConnection[source.type to source.baseLocator].orEmpty()
                        when (sameConnection.size) {
                            0 -> BackupSourceResolution(
                                backupSourceId = source.sourceId,
                                targetSourceId = source.sourceId,
                                action = BackupSourceRestoreAction.CREATE_DISABLED,
                            )

                            1 -> BackupSourceResolution(
                                backupSourceId = source.sourceId,
                                targetSourceId = sameConnection.single().sourceId,
                                action = BackupSourceRestoreAction.MERGE_EXISTING,
                            )

                            else -> BackupSourceResolution(
                                backupSourceId = source.sourceId,
                                targetSourceId = null,
                                action = BackupSourceRestoreAction.CONFLICT,
                            )
                        }
                    }
                }
            },
        )
    }
}
