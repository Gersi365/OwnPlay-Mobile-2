package app.ownplay.mobile.downloads.data

import android.content.Context
import androidx.work.WorkManager
import androidx.work.await
import app.ownplay.mobile.data.db.DownloadDao
import app.ownplay.mobile.downloads.domain.DownloadCleanupTarget
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
    private val appContext = context.applicationContext
    private val filesDir = appContext.filesDir
    private val publicFileStore = PublicDownloadFileStore(appContext)
    private val notificationController = DownloadNotificationController(appContext)

    suspend fun captureDownloads(sourceId: String): List<DownloadCleanupTarget> =
        downloadDao.observeForSource(sourceId).first()
            .filter { row -> row.downloadId.isNotBlank() }
            .distinctBy { row -> row.downloadId }
            .map { row ->
                DownloadCleanupTarget(
                    downloadId = row.downloadId,
                    localReference = row.localReference,
                )
            }

    suspend fun cleanup(targets: List<DownloadCleanupTarget>): Boolean {
        var complete = true
        val safeToDelete = mutableListOf<DownloadCleanupTarget>()
        targets.distinctBy { it.downloadId }.forEach { target ->
            val cancelled = runCatching {
                workManager.cancelUniqueWork(workName(target.downloadId)).await()
            }.isSuccess
            if (cancelled) {
                notificationController.cancel(target.downloadId)
                safeToDelete += target
            } else {
                complete = false
            }
        }

        withContext(Dispatchers.IO) {
            safeToDelete.forEach { target ->
                val publicReference = target.localReference?.takeIf(publicFileStore::handles)
                if (publicReference != null && !publicFileStore.delete(publicReference)) {
                    complete = false
                }
                val privateCleanupSucceeded = runCatching {
                    deletePrivateDownloadFiles(target.downloadId)
                    deletePrivateMetadata(target.downloadId)
                }.isSuccess
                if (!privateCleanupSucceeded) complete = false
            }
        }
        return complete
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
