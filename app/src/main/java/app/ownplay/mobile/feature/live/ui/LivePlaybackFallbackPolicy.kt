package app.ownplay.mobile.feature.live.ui

import app.ownplay.mobile.playback.domain.PlaybackPhase
import app.ownplay.mobile.playback.domain.PlaybackSnapshot

internal object LivePlaybackFallbackPolicy {
    fun shouldUseFallback(playback: PlaybackSnapshot): Boolean =
        playback.phase == PlaybackPhase.ERROR ||
            (
                playback.phase == PlaybackPhase.READY &&
                    playback.audioTrackPresent == true &&
                    (playback.audioTrackSupported == false || playback.audioTrackSelected == false)
                )
}
