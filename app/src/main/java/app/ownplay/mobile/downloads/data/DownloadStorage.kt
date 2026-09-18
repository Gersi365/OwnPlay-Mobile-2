package app.ownplay.mobile.downloads.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.BaseColumns
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import app.ownplay.mobile.downloads.domain.DownloadId
import app.ownplay.mobile.downloads.domain.DownloadPendingNamePolicy
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

internal data class PendingDownloadOutput(
    val outputStream: OutputStream,
    internal val token: PendingDownloadToken,
)

internal sealed interface PendingDownloadToken {
    data class MediaStoreEntry(
        val uri: Uri,
        val finalDisplayName: String,
    ) : PendingDownloadToken

    data class PrivateFile(
        val temporaryFile: File,
        val finalFile: File,
        var published: Boolean = false,
    ) : PendingDownloadToken
}

internal interface DownloadStorage {
    suspend fun openPending(
        downloadId: DownloadId,
        media: ResolvedDownloadMedia,
    ): PendingDownloadOutput?

    suspend fun verifiedSize(pending: PendingDownloadOutput): Long?

    suspend fun publish(pending: PendingDownloadOutput): String?

    suspend fun discard(pending: PendingDownloadOutput)

    suspend fun removePublished(localReference: String): Boolean
}

internal class AndroidDownloadStorage(
    context: Context,
) : DownloadStorage {
    private val applicationContext = context.applicationContext
    private val resolver = applicationContext.contentResolver
    private val privateRoot = File(applicationContext.filesDir, "ownplay-downloads")

    override suspend fun openPending(
        downloadId: DownloadId,
        media: ResolvedDownloadMedia,
    ): PendingDownloadOutput? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openMediaStorePending(downloadId, media)
        } else {
            openPrivatePending(downloadId, media)
        }

    override suspend fun verifiedSize(pending: PendingDownloadOutput): Long? =
        when (val token = pending.token) {
            is PendingDownloadToken.MediaStoreEntry -> mediaStoreSize(token.uri)
            is PendingDownloadToken.PrivateFile ->
                token.temporaryFile.takeIf(File::isFile)?.length()?.takeIf { it >= 0L }
        }

    override suspend fun publish(pending: PendingDownloadOutput): String? =
        when (val token = pending.token) {
            is PendingDownloadToken.MediaStoreEntry -> publishMediaStore(token)
            is PendingDownloadToken.PrivateFile -> publishPrivate(token)
        }

    override suspend fun discard(pending: PendingDownloadOutput) {
        when (val token = pending.token) {
            is PendingDownloadToken.MediaStoreEntry -> runCatching {
                resolver.delete(token.uri, null, null)
            }
            is PendingDownloadToken.PrivateFile -> {
                runCatching { token.temporaryFile.delete() }
                if (token.published) runCatching { token.finalFile.delete() }
            }
        }
    }

    override suspend fun removePublished(localReference: String): Boolean {
        val uri = runCatching { Uri.parse(localReference) }.getOrNull() ?: return false
        return when (uri.scheme) {
            "content" -> {
                if (uri.authority != MediaStore.AUTHORITY) return false
                runCatching { resolver.delete(uri, null, null) >= 0 }.getOrDefault(false)
            }
            "file" -> removePrivateReference(uri)
            else -> false
        }
    }

    private fun openMediaStorePending(
        downloadId: DownloadId,
        media: ResolvedDownloadMedia,
    ): PendingDownloadOutput? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val relativePath = (
            listOf(Environment.DIRECTORY_DOWNLOADS, "OwnPlay Downloads") + media.relativeDirectories
        ).joinToString("/") + "/"
        val stagingDisplayName = DownloadPendingNamePolicy.stagingDisplayName(downloadId)
        if (!discardExistingPendingMediaStore(relativePath, stagingDisplayName)) return null

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, stagingDisplayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType(media.extension))
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        val output = resolver.openOutputStream(uri, "w")
        if (output == null) {
            resolver.delete(uri, null, null)
            return null
        }
        return PendingDownloadOutput(
            outputStream = output,
            token = PendingDownloadToken.MediaStoreEntry(
                uri = uri,
                finalDisplayName = media.displayName,
            ),
        )
    }

    private fun discardExistingPendingMediaStore(
        relativePath: String,
        stagingDisplayName: String,
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val ids = runCatching {
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(BaseColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND " +
                    "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND " +
                    "${MediaStore.MediaColumns.IS_PENDING} = 1",
                arrayOf(stagingDisplayName, relativePath),
                null,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(BaseColumns._ID)
                buildList {
                    while (cursor.moveToNext()) add(cursor.getLong(idIndex))
                }
            } ?: emptyList()
        }.getOrElse { return false }

        return ids.all { id ->
            val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
            runCatching { resolver.delete(uri, null, null) >= 0 }.getOrDefault(false)
        }
    }

    private fun openPrivatePending(
        downloadId: DownloadId,
        media: ResolvedDownloadMedia,
    ): PendingDownloadOutput? {
        val pendingDirectory = File(privateRoot, "pending")
        val finalDirectory = media.relativeDirectories.fold(File(privateRoot, "completed")) { parent, segment ->
            File(parent, segment)
        }
        if (!pendingDirectory.mkdirs() && !pendingDirectory.isDirectory) return null
        if (!finalDirectory.mkdirs() && !finalDirectory.isDirectory) return null
        val token = downloadId.value.filter(Char::isLetterOrDigit).takeLast(32).ifBlank { "download" }
        val temporary = File(pendingDirectory, "$token.part")
        runCatching { temporary.delete() }
        val finalFile = File(finalDirectory, media.displayName)
        val output = runCatching { FileOutputStream(temporary, false) }.getOrNull() ?: return null
        return PendingDownloadOutput(
            outputStream = output,
            token = PendingDownloadToken.PrivateFile(temporary, finalFile),
        )
    }

    private fun mediaStoreSize(uri: Uri): Long? {
        val statSize = runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { descriptor -> descriptor.statSize }
        }.getOrNull()?.takeIf { it >= 0L }
        if (statSize != null) return statSize
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index < 0 || cursor.isNull(index)) null else cursor.getLong(index)
            }
        }.getOrNull()
    }

    private fun publishMediaStore(token: PendingDownloadToken.MediaStoreEntry): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val updated = resolver.update(
            token.uri,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, token.finalDisplayName)
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            },
            null,
            null,
        )
        return token.uri.toString().takeIf { updated == 1 }
    }

    private fun publishPrivate(token: PendingDownloadToken.PrivateFile): String? {
        if (!token.temporaryFile.isFile) return null
        if (token.finalFile.exists() && !token.finalFile.delete()) return null
        if (!token.temporaryFile.renameTo(token.finalFile)) return null
        token.published = true
        return Uri.fromFile(token.finalFile).toString()
    }

    private fun removePrivateReference(uri: Uri): Boolean {
        val path = uri.path ?: return false
        val candidate = runCatching { File(path).canonicalFile }.getOrNull() ?: return false
        val completedRoot = runCatching { File(privateRoot, "completed").canonicalFile }.getOrNull() ?: return false
        val allowedPrefix = completedRoot.path + File.separator
        if (!candidate.path.startsWith(allowedPrefix)) return false
        return !candidate.exists() || candidate.delete()
    }

    private fun mimeType(extension: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            ?: "application/octet-stream"
}
