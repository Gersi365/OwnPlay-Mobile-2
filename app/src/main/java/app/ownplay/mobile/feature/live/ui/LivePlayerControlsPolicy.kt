package app.ownplay.mobile.feature.live.ui

import app.ownplay.mobile.playback.domain.PlaybackPhase
import app.ownplay.mobile.playback.domain.PlaybackSnapshot

internal object LivePlayerControlsPolicy {
    fun shouldAutoHide(snapshot: PlaybackSnapshot): Boolean =
        snapshot.phase == PlaybackPhase.READY && snapshot.isPlaying
}
