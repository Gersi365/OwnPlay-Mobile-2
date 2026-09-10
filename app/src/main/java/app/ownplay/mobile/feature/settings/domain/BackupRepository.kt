package app.ownplay.mobile.feature.settings.domain

data class BackupExport(
    val fileName: String,
    val content: String,
)

data class RestoreSummary(
    val sourcesAdded: Int,
    val sourcesUpdated: Int,
    val groupsRestored: Int,
    val favoritesRestored: Int,
    val progressRestored: Int,
    val personalizationRestored: Int,
    val pendingItems: Int,
)

sealed interface BackupResult<out T> {
    data class Success<T>(val value: T) : BackupResult<T>
    data class Failure(
        val code: String,
        val safeMessage: String,
    ) : BackupResult<Nothing>
}

interface BackupRepository {
    suspend fun createBackup(): BackupResult<BackupExport>

    suspend fun restoreBackup(content: String): BackupResult<RestoreSummary>

    suspend fun applyPendingForSource(sourceId: String): BackupResult<Int>
}
