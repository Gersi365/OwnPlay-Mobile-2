package app.ownplay.mobile.design

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

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

    private val memoryCache = object : LruCache<String, Bitmap>(MEMORY_CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
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
            if (bitmap != null) {
                memoryCache.put(cacheKey, bitmap)
            }
            bitmap?.asImageBitmap()
        } finally {
            if (deferred.isCompleted) {
                synchronized(inFlightLock) {
                    if (inFlight[cacheKey] === deferred) {
                        inFlight.remove(cacheKey)
                    }
                }
            }
        }
    }

    private fun fetchBitmap(
        locator: String,
        profile: RemoteImageProfile,
    ): Bitmap? {
        val connection = try {
            URL(locator).openConnection() as? HttpURLConnection
        } catch (_: Exception) {
            null
        } ?: return null

        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true

        return try {
            if (connection.responseCode !in 200..299) return null
            val announcedLength = connection.contentLengthLong
            if (announcedLength > profile.maxBytes) return null

            val output = ByteArrayOutputStream(
                announcedLength
                    .takeIf { it in 1..profile.maxBytes.toLong() }
                    ?.toInt()
                    ?: DEFAULT_BUFFER_CAPACITY,
            )
            connection.inputStream.use { input ->
                val buffer = ByteArray(NETWORK_BUFFER_BYTES)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count <= 0) break
                    total += count
                    if (total > profile.maxBytes) return null
                    output.write(buffer, 0, count)
                }
            }
            decodeSampled(output.toByteArray(), profile.maxDimension)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun decodeSampled(
        bytes: ByteArray,
        maxDimension: Int,
    ): Bitmap? {
        if (bytes.isEmpty()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        if (largest <= 0) return null

        var sampleSize = 1
        while (largest / (sampleSize * 2) >= maxDimension) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private const val MEMORY_CACHE_KB = 24 * 1024
    private const val CONNECT_TIMEOUT_MS = 4_000
    private const val READ_TIMEOUT_MS = 5_000
    private const val NETWORK_BUFFER_BYTES = 8 * 1024
    private const val DEFAULT_BUFFER_CAPACITY = 32 * 1024
}
