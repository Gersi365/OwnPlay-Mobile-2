package app.ownplay.mobile.feature.library.ui

import app.ownplay.mobile.playback.domain.PlaybackPhase
import app.ownplay.mobile.playback.domain.PlaybackSnapshot

internal object LibraryPlayerControlsPolicy {
    fun shouldAutoHide(snapshot: PlaybackSnapshot): Boolean =
        snapshot.phase == PlaybackPhase.READY && snapshot.isPlaying
}
