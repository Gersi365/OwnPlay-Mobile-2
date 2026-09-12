package app.ownplay.mobile.feature.library.data

import app.ownplay.mobile.playback.domain.PlaybackStreamFormat
import app.ownplay.mobile.sources.data.xtream.XtreamUrlBuilder
import app.ownplay.mobile.sources.domain.SourceCredential
import java.net.URI
import java.util.Locale

object LibraryPlaybackLocator {
    fun movieUri(
        baseUrl: String,
        credential: SourceCredential.Xtream,
        providerStreamId: String,
        extension: String?,
    ): String = XtreamUrlBuilder.streamUrl(
        baseUrl = baseUrl,
        credential = credential,
        kind = "movie",
        providerId = providerStreamId,
        extension = extension,
    )

    fun episodeUri(
        baseUrl: String,
        credential: SourceCredential.Xtream,
        providerEpisodeId: String,
        extension: String?,
    ): String = XtreamUrlBuilder.streamUrl(
        baseUrl = baseUrl,
        credential = credential,
        kind = "series",
        providerId = providerEpisodeId,
        extension = extension,
    )

    fun movieFallbackUri(
        baseUrl: String,
        credential: SourceCredential.Xtream,
        providerStreamId: String,
        extension: String?,
    ): String = XtreamUrlBuilder.streamUrl(
        baseUrl = baseUrl,
        credential = credential,
        kind = "movie",
        providerId = providerStreamId,
        extension = alternateExtension(extension),
    )

    fun episodeFallbackUri(
        baseUrl: String,
        credential: SourceCredential.Xtream,
        providerEpisodeId: String,
        extension: String?,
    ): String = XtreamUrlBuilder.streamUrl(
        baseUrl = baseUrl,
        credential = credential,
        kind = "series",
        providerId = providerEpisodeId,
        extension = alternateExtension(extension),
    )

    private fun alternateExtension(extension: String?): String? {
        val normalized = extension
            ?.trim()
            ?.removePrefix(".")
            ?.lowercase(Locale.US)
            ?.takeIf(String::isNotBlank)
        return if (normalized == "m3u8") null else "m3u8"
    }

    fun streamFormatFor(uri: String): PlaybackStreamFormat {
        val normalizedPath = runCatching { URI(uri).path.orEmpty() }
            .getOrDefault(uri.substringBefore('?'))
            .lowercase(Locale.US)
        return if (normalizedPath.endsWith(".m3u8")) {
            PlaybackStreamFormat.HLS
        } else {
            PlaybackStreamFormat.AUTO
        }
    }
}
