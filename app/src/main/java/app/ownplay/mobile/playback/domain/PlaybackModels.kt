package app.ownplay.mobile.playback.domain

enum class PlaybackKind {
    LIVE,
    MOVIE,
    EPISODE,
    OFFLINE,
}

enum class PlaybackStreamFormat {
    AUTO,
    HLS,
}

data class PlaybackMedia(
    val id: String,
    val uri: String,
    val title: String,
    val kind: PlaybackKind,
    val streamFormat: PlaybackStreamFormat = PlaybackStreamFormat.AUTO,
) {
    init {
        require(id.isNotBlank()) { "Playback media id must not be blank." }
        require(uri.isNotBlank()) { "Playback media URI must not be blank." }
        require(title.isNotBlank()) { "Playback media title must not be blank." }
    }
}

sealed interface PlaybackStart {
    data object Default : PlaybackStart

    data class Resume(
        val savedPositionMs: Long,
    ) : PlaybackStart

    data object Beginning : PlaybackStart
}

data class PlaybackStartDecision(
    val positionMs: Long?,
    val clearSavedProgressBeforeStart: Boolean,
)

object PlaybackStartPolicy {
    fun resolve(start: PlaybackStart): PlaybackStartDecision = when (start) {
        PlaybackStart.Default -> PlaybackStartDecision(
            positionMs = null,
            clearSavedProgressBeforeStart = false,
        )

        is PlaybackStart.Resume -> PlaybackStartDecision(
            positionMs = start.savedPositionMs.coerceAtLeast(0L),
            clearSavedProgressBeforeStart = false,
        )

        PlaybackStart.Beginning -> PlaybackStartDecision(
            positionMs = 0L,
            clearSavedProgressBeforeStart = false,
        )
    }
}

data class PlaybackLoadRequest(
    val media: PlaybackMedia,
    val start: PlaybackStart = PlaybackStart.Default,
    val playWhenReady: Boolean = true,
)

enum class PlaybackPhase {
    IDLE,
    BUFFERING,
    READY,
    ENDED,
    ERROR,
    RELEASED,
}

data class PlaybackSnapshot(
    val mediaId: String? = null,
    val title: String? = null,
    val kind: PlaybackKind? = null,
    val phase: PlaybackPhase = PlaybackPhase.IDLE,
    val playWhenReady: Boolean = false,
    val isPlaying: Boolean = false,
    val volume: Float = 1f,
    val positionMs: Long = 0L,
    val durationMs: Long? = null,
    val activeTarget: VideoTarget = VideoTarget.NONE,
    val audioTrackPresent: Boolean? = null,
    val audioTrackSupported: Boolean? = null,
    val audioTrackSelected: Boolean? = null,
    val audioMimeType: String? = null,
    val audioCodecs: String? = null,
    val audioChannelCount: Int? = null,
    val audioSampleRate: Int? = null,
    val errorCode: Int? = null,
)
