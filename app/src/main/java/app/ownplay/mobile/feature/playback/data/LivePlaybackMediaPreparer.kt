package app.ownplay.mobile.feature.playback.data

import app.ownplay.mobile.feature.playback.domain.LivePlaybackMediaPreparer
import app.ownplay.mobile.feature.playback.domain.LivePlaybackSource
import app.ownplay.mobile.feature.playback.domain.PreparedPlaybackMedia
import app.ownplay.mobile.sources.data.xtream.XtreamLiveStreamIdentity
import app.ownplay.mobile.sources.data.xtream.XtreamUrlBuilder
import app.ownplay.mobile.sources.domain.ConnectionValidation
import app.ownplay.mobile.sources.domain.SourceConnectionSecurityPolicy

internal class DefaultLivePlaybackMediaPreparer : LivePlaybackMediaPreparer {
    override fun prepare(source: LivePlaybackSource): PreparedPlaybackMedia? = when (source) {
        is LivePlaybackSource.Direct -> prepareDirect(source)
        is LivePlaybackSource.Xtream -> prepareXtream(source)
    }

    private fun prepareDirect(source: LivePlaybackSource.Direct): PreparedPlaybackMedia? {
        val normalized = when (
            val validation = SourceConnectionSecurityPolicy.normalizeRemoteMediaUrl(source.streamLocator)
        ) {
            is ConnectionValidation.Valid -> validation.normalizedUrl
            is ConnectionValidation.Invalid -> return null
        }

        return PreparedPlaybackMedia(
            uri = normalized,
            mimeType = inferMimeType(normalized),
        )
    }

    private fun prepareXtream(source: LivePlaybackSource.Xtream): PreparedPlaybackMedia? {
        val extension = XtreamLiveStreamIdentity.extension(
            identity = source.opaqueStreamIdentity,
            streamId = source.streamId,
        ) ?: return null

        val uri = runCatching {
            XtreamUrlBuilder.liveStream(
                baseUrl = source.baseUrl,
                username = source.username,
                password = source.password,
                streamId = source.streamId,
                extension = extension,
            )
        }.getOrNull() ?: return null

        return PreparedPlaybackMedia(
            uri = uri,
            mimeType = if (extension == "m3u8") HLS_MIME_TYPE else null,
        )
    }

    private fun inferMimeType(uri: String): String? =
        if (uri.substringBefore('?').lowercase().endsWith(".m3u8")) HLS_MIME_TYPE else null

    private companion object {
        const val HLS_MIME_TYPE = "application/x-mpegURL"
    }
}
