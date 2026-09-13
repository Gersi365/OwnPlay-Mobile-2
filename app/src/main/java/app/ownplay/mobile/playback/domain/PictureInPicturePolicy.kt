package app.ownplay.mobile.playback.domain

object PictureInPicturePolicy {
    fun canEnter(
        enabled: Boolean,
        contentFullscreen: Boolean,
        playback: PlaybackSnapshot,
    ): Boolean {
        if (!enabled || !contentFullscreen || playback.mediaId == null || !playback.playWhenReady) {
            return false
        }
        if (playback.activeTarget != VideoTarget.FULLSCREEN && playback.activeTarget != VideoTarget.PIP) {
            return false
        }
        return playback.phase == PlaybackPhase.BUFFERING || playback.phase == PlaybackPhase.READY
    }
}

data class PictureInPictureAspectRatio(
    val width: Int,
    val height: Int,
)

object PictureInPictureAspectRatioPolicy {
    private const val MAX_PLATFORM_RATIO = 2.39f
    private const val MIN_PLATFORM_RATIO = 1f / MAX_PLATFORM_RATIO

    val fallback = PictureInPictureAspectRatio(width = 16, height = 9)

    fun resolve(videoWidth: Int?, videoHeight: Int?): PictureInPictureAspectRatio {
        val width = videoWidth?.takeIf { it > 0 } ?: return fallback
        val height = videoHeight?.takeIf { it > 0 } ?: return fallback
        val ratio = width.toFloat() / height.toFloat()
        if (ratio !in MIN_PLATFORM_RATIO..MAX_PLATFORM_RATIO) {
            return fallback
        }
        return PictureInPictureAspectRatio(width = width, height = height)
    }
}
