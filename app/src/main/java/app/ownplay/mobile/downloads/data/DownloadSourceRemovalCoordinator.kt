package app.ownplay.mobile.downloads.data

import android.content.Context
import androidx.work.WorkManager
import androidx.work.await
import app.ownplay.mobile.data.db.DownloadDao
import app.ownplay.mobile.downloads.domain.DownloadIdentity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal class DownloadSourceRemovalCoordinator(
    context: Context,
    private val downloadDao: DownloadDao,
    private val workManager: WorkManager,
) {
    private val filesDir = context.applicationContext.filesDir

    suspend fun captureDownloadIds(sourceId: String): List<String> =
        downloadDao.observeForSource(sourceId).first()
            .map { row -> row.downloadId }
            .filter(String::isNotBlank)
            .distinct()

    suspend fun cleanup(downloadIds: List<String>) {
        val safeToDelete = mutableListOf<String>()
        downloadIds.filter(String::isNotBlank).distinct().forEach { downloadId ->
            val cancelled = runCatching {
                workManager.cancelUniqueWork(workName(downloadId)).await()
            }.isSuccess
            if (cancelled) safeToDelete += downloadId
        }

        withContext(Dispatchers.IO) {
            safeToDelete.forEach { downloadId ->
                runCatching {
                    deletePrivateDownloadFiles(downloadId)
                    deletePrivateMetadata(downloadId)
                }
            }
        }
    }

    private fun deletePrivateDownloadFiles(downloadId: String) {
        val root = File(filesDir, DOWNLOAD_DIRECTORY)
        val safe = DownloadIdentity.idFor("file", "download", downloadId)
        deleteIfPresent(File(root, "$safe.part"))
        deleteIfPresent(File(root, "$safe.media"))
    }

    private fun deletePrivateMetadata(downloadId: String) {
        val safe = downloadId.map { character ->
            if (character.isLetterOrDigit() || character == '-' || character == '_') character else '_'
        }.joinToString("").take(160).ifBlank { "unknown" }
        val directory = File(File(filesDir, METADATA_DIRECTORY), safe)
        if (directory.exists()) directory.deleteRecursively()
    }

    private fun deleteIfPresent(file: File) {
        if (file.exists()) file.delete()
    }

    private companion object {
        const val DOWNLOAD_DIRECTORY = "ownplay-downloads"
        const val METADATA_DIRECTORY = "download-metadata"
        fun workName(downloadId: String): String = "ownplay-download-$downloadId"
    }
}
