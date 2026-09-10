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
