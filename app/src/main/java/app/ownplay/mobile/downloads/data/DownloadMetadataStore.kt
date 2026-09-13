package app.ownplay.mobile.downloads.data

import android.content.Context
import app.ownplay.mobile.feature.library.domain.LibraryMediaMetadata
import java.io.File
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal class DownloadMetadataStore(
    context: Context,
    private val httpClient: OkHttpClient,
) {
    private val root = File(context.applicationContext.filesDir, ROOT_DIRECTORY)
    private val memory = ConcurrentHashMap<String, LibraryMediaMetadata>()

    suspend fun persist(downloadId: String, metadata: LibraryMediaMetadata) = withContext(Dispatchers.IO) {
        val directory = directory(downloadId)
        if (!directory.exists() && !directory.mkdirs()) return@withContext

        val poster = persistArtwork(metadata.posterUrl, File(directory, POSTER_FILE))
        val backdrop = persistArtwork(metadata.backdropUrl, File(directory, BACKDROP_FILE))
        val properties = Properties().apply {
            putValue(KEY_TITLE, metadata.title)
            putValue(KEY_PLOT, metadata.plot)
            putValue(KEY_RELEASE_DATE, metadata.releaseDate)
            putValue(KEY_DURATION_MS, metadata.durationMs?.toString())
            putValue(KEY_RATING, metadata.rating)
            putValue(KEY_GENRE, metadata.genre)
            putValue(KEY_DIRECTOR, metadata.director)
            putValue(KEY_CAST, metadata.cast)
        }
        runCatching {
            File(directory, METADATA_FILE).outputStream().buffered().use { output ->
                properties.store(output, null)
            }
        }.onSuccess {
            memory[downloadId] = metadata.copy(
                posterUrl = poster?.toURI()?.toString(),
                backdropUrl = backdrop?.toURI()?.toString(),
            )
        }
    }

    fun read(downloadId: String): LibraryMediaMetadata? {
        memory[downloadId]?.let { return it }
        val directory = directory(downloadId)
        val file = File(directory, METADATA_FILE)
        if (!file.isFile) return null
        val properties = Properties()
        val loaded = runCatching {
            file.inputStream().buffered().use(properties::load)
            val title = properties.getProperty(KEY_TITLE)?.takeIf(String::isNotBlank) ?: return@runCatching null
            LibraryMediaMetadata(
                title = title,
                posterUrl = File(directory, POSTER_FILE).takeIf(File::isFile)?.toURI()?.toString(),
                backdropUrl = File(directory, BACKDROP_FILE).takeIf(File::isFile)?.toURI()?.toString(),
                plot = properties.getProperty(KEY_PLOT)?.takeIf(String::isNotBlank),
                releaseDate = properties.getProperty(KEY_RELEASE_DATE)?.takeIf(String::isNotBlank),
                durationMs = properties.getProperty(KEY_DURATION_MS)?.toLongOrNull(),
                rating = properties.getProperty(KEY_RATING)?.takeIf(String::isNotBlank),
                genre = properties.getProperty(KEY_GENRE)?.takeIf(String::isNotBlank),
                director = properties.getProperty(KEY_DIRECTOR)?.takeIf(String::isNotBlank),
                cast = properties.getProperty(KEY_CAST)?.takeIf(String::isNotBlank),
            )
        }.getOrNull() ?: return null
        memory[downloadId] = loaded
        return loaded
    }

    fun delete(downloadId: String) {
        memory.remove(downloadId)
        directory(downloadId).deleteRecursively()
    }

    private fun persistArtwork(locator: String?, destination: File): File? {
        val normalized = locator?.trim()?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            ?: return destination.takeIf(File::isFile)
        val request = runCatching { Request.Builder().url(normalized).build() }.getOrNull()
            ?: return destination.takeIf(File::isFile)
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return destination.takeIf(File::isFile)
                val body = response.body ?: return destination.takeIf(File::isFile)
                val announced = body.contentLength()
                if (announced > MAX_ARTWORK_BYTES) return destination.takeIf(File::isFile)
                val temporary = File(destination.parentFile, "${destination.name}.part")
                var total = 0L
                body.byteStream().use { input ->
                    temporary.outputStream().buffered().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            total += count
                            if (total > MAX_ARTWORK_BYTES) {
                                temporary.delete()
                                return destination.takeIf(File::isFile)
                            }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                if (total <= 0L) {
                    temporary.delete()
                    return destination.takeIf(File::isFile)
                }
                if (destination.exists()) destination.delete()
                if (!temporary.renameTo(destination)) {
                    temporary.copyTo(destination, overwrite = true)
                    temporary.delete()
                }
                destination.takeIf(File::isFile)
            }
        } catch (_: Exception) {
            destination.takeIf(File::isFile)
        }
    }

    private fun directory(downloadId: String): File {
        val safe = downloadId.map { character ->
            if (character.isLetterOrDigit() || character == '-' || character == '_') character else '_'
        }.joinToString("").take(160).ifBlank { "unknown" }
        return File(root, safe)
    }

    private fun Properties.putValue(key: String, value: String?) {
        value?.takeIf(String::isNotBlank)?.let { setProperty(key, it) }
    }

    private companion object {
        const val ROOT_DIRECTORY = "download-metadata"
        const val METADATA_FILE = "metadata.properties"
        const val POSTER_FILE = "poster.image"
        const val BACKDROP_FILE = "backdrop.image"
        const val KEY_TITLE = "title"
        const val KEY_PLOT = "plot"
        const val KEY_RELEASE_DATE = "releaseDate"
        const val KEY_DURATION_MS = "durationMs"
        const val KEY_RATING = "rating"
        const val KEY_GENRE = "genre"
        const val KEY_DIRECTOR = "director"
        const val KEY_CAST = "cast"
        const val BUFFER_SIZE = 8 * 1024
        const val MAX_ARTWORK_BYTES = 8L * 1024L * 1024L
    }
}
