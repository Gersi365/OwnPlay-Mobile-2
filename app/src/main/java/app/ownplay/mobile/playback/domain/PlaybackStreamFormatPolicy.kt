package app.ownplay.mobile.playback.domain

import java.net.URI
import java.util.Locale

object PlaybackStreamFormatPolicy {
    fun infer(uri: String): PlaybackStreamFormat {
        val normalized = uri.trim()
        if (normalized.isEmpty()) return PlaybackStreamFormat.AUTO

        val parsed = runCatching { URI(normalized) }.getOrNull()
        val path = parsed?.path.orEmpty()
            .ifBlank { normalized.substringBefore('?').substringBefore('#') }
            .lowercase(Locale.US)
        if (path.endsWith(".m3u8")) {
            return PlaybackStreamFormat.HLS
        }

        val rawQuery = parsed?.rawQuery ?: normalized.substringAfter('?', missingDelimiterValue = "")
            .substringBefore('#')
        if (queryDeclaresHls(rawQuery)) {
            return PlaybackStreamFormat.HLS
        }

        return PlaybackStreamFormat.AUTO
    }

    fun isOpaqueNetworkUri(uri: String): Boolean {
        val parsed = runCatching { URI(uri.trim()) }.getOrNull() ?: return false
        if (parsed.scheme?.lowercase(Locale.US) !in setOf("http", "https")) return false
        val lastSegment = parsed.path.orEmpty()
            .trimEnd('/')
            .substringAfterLast('/')
            .trim()
        return lastSegment.isNotEmpty() && '.' !in lastSegment
    }

    private fun queryDeclaresHls(rawQuery: String): Boolean = rawQuery
        .split('&')
        .asSequence()
        .mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0 || separator == part.lastIndex) return@mapNotNull null
            val key = part.substring(0, separator).trim().lowercase(Locale.US)
            val value = part.substring(separator + 1).trim().lowercase(Locale.US)
            key to value
        }
        .any { (key, value) ->
            key in HLS_HINT_KEYS &&
                (
                    value == "hls" ||
                        value == "m3u8" ||
                        value.endsWith(".m3u8") ||
                        value.contains("application/vnd.apple.mpegurl") ||
                        value.contains("application/x-mpegurl")
                    )
        }

    private val HLS_HINT_KEYS = setOf(
        "format",
        "type",
        "output",
        "container",
        "extension",
        "ext",
        "stream",
        "playlist",
        "file",
        "url",
        "src",
        "source",
    )
}
