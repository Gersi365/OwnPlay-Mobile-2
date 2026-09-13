package app.ownplay.mobile.playback.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import app.ownplay.mobile.playback.PlaybackController

@Stable
class PlayerInteractionState internal constructor() {
    var touchLocked by mutableStateOf(false)
        private set

    fun lockTouch() {
        touchLocked = true
    }

    fun unlockTouch() {
        touchLocked = false
    }
}

@Composable
fun rememberPlayerInteractionState(): PlayerInteractionState = remember { PlayerInteractionState() }

val LocalPlaybackController = staticCompositionLocalOf<PlaybackController> {
    error("PlaybackController is not available in this composition.")
}

val LocalPlayerInteractionState = staticCompositionLocalOf<PlayerInteractionState> {
    error("PlayerInteractionState is not available in this composition.")
}
