package app.ownplay.mobile.design

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import okhttp3.OkHttpClient
import okhttp3.Request

internal enum class RemoteImageProfile(
    internal val maxBytes: Int,
    internal val maxDimension: Int,
) {
    ARTWORK(maxBytes = 4 * 1024 * 1024, maxDimension = 1440),
    LOGO(maxBytes = 2 * 1024 * 1024, maxDimension = 192),
}

internal object OwnPlayRemoteImageLoader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightLock = Any()
    private val inFlight = mutableMapOf<String, Deferred<Bitmap?>>()

    @Volatile
    private var httpClient: OkHttpClient? = null

    private val memoryCache = object : LruCache<String, Bitmap>(MEMORY_CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    fun configure(client: OkHttpClient) {
        httpClient = client
    }

    suspend fun load(
        locator: String?,
        profile: RemoteImageProfile,
    ): ImageBitmap? {
        val normalized = locator?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val cacheKey = "${profile.name}|$normalized"
        memoryCache.get(cacheKey)?.let { return it.asImageBitmap() }

        val deferred: Deferred<Bitmap?> = synchronized(inFlightLock) {
            memoryCache.get(cacheKey)?.let { cached ->
                return@synchronized CompletableDeferred(cached)
            }
            inFlight.getOrPut(cacheKey) {
                scope.async { fetchBitmap(normalized, profile) }
            }
        }

        return try {
            val bitmap = deferred.await()
            if (bitmap != null) memoryCache.put(cacheKey, bitmap)
            bitmap?.asImageBitmap()
        } finally {
            if (deferred.isCompleted) {
                synchronized(inFlightLock) {
                    if (inFlight[cacheKey] === deferred) inFlight.remove(cacheKey)
                }
            }
        }
    }

    private fun fetchBitmap(locator: String, profile: RemoteImageProfile): Bitmap? = when {
        locator.startsWith(FILE_SCHEME, ignoreCase = true) -> fetchFileBitmap(locator, profile)
        locator.startsWith("https://", ignoreCase = true) || locator.startsWith("http://", ignoreCase = true) ->
            fetchHttpBitmap(locator, profile)
        else -> null
    }

    private fun fetchFileBitmap(locator: String, profile: RemoteImageProfile): Bitmap? {
        val path = runCatching { Uri.parse(locator).path }.getOrNull() ?: return null
        val file = File(path)
        if (!file.isFile || file.length() <= 0L || file.length() > profile.maxBytes) return null
        return try {
            FileInputStream(file).use { input ->
                readAndDecode(input.readBytesLimited(profile.maxBytes), profile.maxDimension)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchHttpBitmap(locator: String, profile: RemoteImageProfile): Bitmap? {
        val client = httpClient ?: return null
        val request = try {
            Request.Builder().url(locator).get().build()
        } catch (_: IllegalArgumentException) {
            return null
        }

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body
                val announcedLength = body.contentLength()
                if (announcedLength > profile.maxBytes) return null
                body.byteStream().use { input ->
                    readAndDecode(input.readBytesLimited(profile.maxBytes), profile.maxDimension)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun java.io.InputStream.readBytesLimited(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(DEFAULT_BUFFER_CAPACITY)
        val buffer = ByteArray(NETWORK_BUFFER_BYTES)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count <= 0) break
            total += count
            if (total > maxBytes) return ByteArray(0)
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun readAndDecode(bytes: ByteArray, maxDimension: Int): Bitmap? =
        if (bytes.isEmpty()) null else decodeSampled(bytes, maxDimension)

    private fun decodeSampled(bytes: ByteArray, maxDimension: Int): Bitmap? {
        if (bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        if (largest <= 0) return null

        var sampleSize = 1
        while (largest / (sampleSize * 2) >= maxDimension) sampleSize *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private const val FILE_SCHEME = "file://"
    private const val MEMORY_CACHE_KB = 24 * 1024
    private const val NETWORK_BUFFER_BYTES = 8 * 1024
    private const val DEFAULT_BUFFER_CAPACITY = 32 * 1024
}
