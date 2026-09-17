package app.ownplay.mobile.feature.playback.domain

import app.ownplay.mobile.sources.domain.SourceId

enum class PlaybackPresentation {
    NONE,
    PREVIEW,
    FULLSCREEN,
    PICTURE_IN_PICTURE,
}

enum class PlaybackReadiness {
    IDLE,
    PREPARING,
    PREPARED,
    UNAVAILABLE,
}

data class PlaybackTarget(
    val sourceId: SourceId,
    val channelId: String,
) {
    init {
        require(channelId.isNotBlank()) { "Playback channel id must not be blank" }
    }
}

data class PlaybackSessionState(
    val target: PlaybackTarget? = null,
    val presentation: PlaybackPresentation = PlaybackPresentation.NONE,
    val readiness: PlaybackReadiness = PlaybackReadiness.IDLE,
)

object PlaybackSessionPolicy {
    fun activateLiveChannel(
        current: PlaybackSessionState,
        target: PlaybackTarget,
    ): PlaybackSessionState {
        if (current.target == target) {
            return if (current.presentation == PlaybackPresentation.PREVIEW) {
                current.copy(presentation = PlaybackPresentation.FULLSCREEN)
            } else {
                current
            }
        }

        return PlaybackSessionState(
            target = target,
            presentation = PlaybackPresentation.PREVIEW,
            readiness = PlaybackReadiness.PREPARING,
        )
    }

    fun present(
        current: PlaybackSessionState,
        presentation: PlaybackPresentation,
    ): PlaybackSessionState {
        if (current.target == null) return PlaybackSessionState()
        if (presentation == PlaybackPresentation.NONE) return PlaybackSessionState()
        return current.copy(presentation = presentation)
    }
}

internal sealed interface LivePlaybackSource {
    class Direct(
        internal val streamLocator: String,
    ) : LivePlaybackSource {
        init {
            require(streamLocator.isNotBlank()) { "Stream locator must not be blank" }
        }

        override fun toString(): String = "Direct(streamLocator=<redacted>)"
    }

    class Xtream(
        internal val baseUrl: String,
        internal val username: String,
        internal val password: String,
        internal val streamId: String,
        internal val opaqueStreamIdentity: String,
    ) : LivePlaybackSource {
        init {
            require(baseUrl.isNotBlank()) { "Xtream base URL must not be blank" }
            require(username.isNotBlank()) { "Xtream username must not be blank" }
            require(password.isNotBlank()) { "Xtream password must not be blank" }
            require(streamId.isNotBlank()) { "Xtream stream id must not be blank" }
        }

        override fun toString(): String =
            "Xtream(baseUrl=<redacted>, username=<redacted>, password=<redacted>, streamId=<redacted>, opaqueStreamIdentity=<redacted>)"
    }
}

internal data class PreparedPlaybackMedia(
    internal val uri: String,
    internal val mimeType: String? = null,
) {
    init {
        require(uri.isNotBlank()) { "Prepared playback URI must not be blank" }
    }

    override fun toString(): String =
        "PreparedPlaybackMedia(uri=<redacted>, mimeType=${mimeType ?: "<unspecified>"})"
}

internal interface LivePlaybackSourceResolver {
    suspend fun resolve(target: PlaybackTarget): LivePlaybackSource?
}

internal interface LivePlaybackMediaPreparer {
    fun prepare(source: LivePlaybackSource): PreparedPlaybackMedia?
}

internal interface PlaybackEngine {
    fun replace(media: PreparedPlaybackMedia)

    fun clear()
}
