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
