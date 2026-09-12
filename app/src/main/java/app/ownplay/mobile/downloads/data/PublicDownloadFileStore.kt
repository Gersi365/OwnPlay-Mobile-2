package app.ownplay.mobile.downloads.data

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileInputStream
import java.io.IOException

internal class PublicDownloadFileStore(context: Context) {
    private val appContext = context.applicationContext
    private val contentResolver = appContext.contentResolver

    fun canWrite(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun handles(reference: String): Boolean =
        reference.startsWith(CONTENT_SCHEME) || reference.startsWith(FILE_SCHEME)

    @Throws(IOException::class, SecurityException::class)
    fun publish(
        sourceFile: File,
        destination: DownloadDestination,
        integrityMetadata: String,
    ): String {
        if (!sourceFile.isFile || sourceFile.length() <= 0L) {
            throw IOException("Download staging file is unavailable")
        }
        if (!canWrite()) {
            throw SecurityException("Public Downloads permission is unavailable")
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishScoped(sourceFile, destination, integrityMetadata)
        } else {
            publishLegacy(sourceFile, destination, integrityMetadata)
        }
    }

    fun verify(reference: String, integrityMetadata: String): Boolean {
        return try {
            when {
                reference.startsWith(CONTENT_SCHEME) -> {
                    val uri = Uri.parse(reference)
                    contentResolver.openInputStream(uri)?.use { input ->
                        DownloadIntegrity.verify(input, integrityMetadata)
                    } ?: false
                }

                reference.startsWith(FILE_SCHEME) -> {
                    val path = Uri.parse(reference).path ?: return false
                    val file = File(path)
                    if (!file.isFile) return false
                    FileInputStream(file).use { input ->
                        DownloadIntegrity.verify(input, integrityMetadata)
                    }
                }

                else -> false
            }
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    fun delete(reference: String): Boolean {
        return try {
            when {
                reference.startsWith(CONTENT_SCHEME) ->
                    contentResolver.delete(Uri.parse(reference), null, null) > 0

                reference.startsWith(FILE_SCHEME) -> {
                    val path = Uri.parse(reference).path ?: return false
                    val file = File(path)
                    !file.exists() || file.delete()
                }

                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun publishScoped(
        sourceFile: File,
        destination: DownloadDestination,
        integrityMetadata: String,
    ): String {
        val relativePath = buildString {
            append(Environment.DIRECTORY_DOWNLOADS)
            append('/')
            append(destination.relativeDirectory)
            append('/')
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, destination.displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, destination.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values,
        ) ?: throw IOException("Unable to create public Download item")

        try {
            contentResolver.openOutputStream(uri, "w")?.use { output ->
                sourceFile.inputStream().buffered().use { input ->
                    output.buffered().use { bufferedOutput ->
                        input.copyTo(bufferedOutput, COPY_BUFFER_SIZE)
                    }
                }
            } ?: throw IOException("Unable to open public Download output")

            if (!verify(uri.toString(), integrityMetadata)) {
                throw IOException("Public Download integrity verification failed")
            }

            val readyValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            if (contentResolver.update(uri, readyValues, null, null) <= 0) {
                throw IOException("Unable to publish public Download item")
            }
            return uri.toString()
        } catch (error: Exception) {
            runCatching { contentResolver.delete(uri, null, null) }
            throw error
        }
    }

    @Suppress("DEPRECATION")
    private fun publishLegacy(
        sourceFile: File,
        destination: DownloadDestination,
        integrityMetadata: String,
    ): String {
        if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) {
            throw IOException("External storage is unavailable")
        }
        val downloadsRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val destinationDirectory = File(downloadsRoot, destination.relativeDirectory)
        if (!destinationDirectory.exists() && !destinationDirectory.mkdirs()) {
            throw IOException("Unable to create OwnPlay Downloads hierarchy")
        }
        val finalFile = File(destinationDirectory, destination.displayName)
        val temporaryFile = File(destinationDirectory, ".${destination.displayName}.ownplay-part")

        if (temporaryFile.exists() && !temporaryFile.delete()) {
            throw IOException("Unable to replace legacy download staging file")
        }
        sourceFile.copyTo(temporaryFile, overwrite = true)
        if (!DownloadIntegrity.verify(temporaryFile, integrityMetadata)) {
            temporaryFile.delete()
            throw IOException("Legacy public Download integrity verification failed")
        }
        if (finalFile.exists() && !finalFile.delete()) {
            temporaryFile.delete()
            throw IOException("Unable to replace legacy public Download item")
        }
        if (!temporaryFile.renameTo(finalFile)) {
            try {
                temporaryFile.copyTo(finalFile, overwrite = true)
                if (!temporaryFile.delete()) {
                    finalFile.delete()
                    throw IOException("Unable to finalize legacy public Download item")
                }
            } catch (error: IOException) {
                finalFile.delete()
                throw error
            }
        }
        if (!DownloadIntegrity.verify(finalFile, integrityMetadata)) {
            finalFile.delete()
            throw IOException("Legacy public Download integrity verification failed")
        }
        return Uri.fromFile(finalFile).toString()
    }

    private companion object {
        const val CONTENT_SCHEME = "content://"
        const val FILE_SCHEME = "file://"
        const val COPY_BUFFER_SIZE = 64 * 1024
    }
}
