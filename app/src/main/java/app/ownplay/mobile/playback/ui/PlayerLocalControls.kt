package app.ownplay.mobile.playback.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.provider.Settings
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import app.ownplay.mobile.playback.PlaybackController
import app.ownplay.mobile.playback.domain.PlayerLocalControlPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun Modifier.playerLocalVerticalControls(
    playbackController: PlaybackController,
    controllerScope: CoroutineScope,
): Modifier = composed {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    this.pointerInput(playbackController, activity) {
        var mode: PlayerGestureMode? = null
        var localVolume = playbackController.state.value.volume

        detectVerticalDragGestures(
            onDragStart = { start ->
                mode = if (start.x < size.width / 2f) {
                    PlayerGestureMode.BRIGHTNESS
                } else {
                    PlayerGestureMode.VOLUME
                }
                localVolume = playbackController.state.value.volume
            },
            onDragEnd = { mode = null },
            onDragCancel = { mode = null },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                val delta = PlayerLocalControlPolicy.normalizedGestureDelta(
                    deltaY = dragAmount,
                    surfaceHeight = size.height.toFloat(),
                )
                when (mode) {
                    PlayerGestureMode.BRIGHTNESS -> activity?.let { host ->
                        val brightness = PlayerLocalControlPolicy.clampBrightness(
                            host.currentOwnPlayBrightness() + delta,
                        )
                        host.setOwnPlayBrightness(brightness)
                        PlayerLocalControlHud.showBrightness(brightness)
                    }
                    PlayerGestureMode.VOLUME -> {
                        localVolume = PlayerLocalControlPolicy.clampVolume(localVolume + delta)
                        PlayerLocalControlHud.showVolume(localVolume)
                        controllerScope.launch {
                            playbackController.setVolume(localVolume)
                        }
                    }
                    null -> Unit
                }
            },
        )
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

private fun Activity.currentOwnPlayBrightness(): Float {
    val override = window.attributes.screenBrightness
    if (override >= 0f) return PlayerLocalControlPolicy.clampBrightness(override)

    val systemBrightness = runCatching {
        Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    }.getOrDefault(128)
    return PlayerLocalControlPolicy.clampBrightness(systemBrightness / 255f)
}

private fun Activity.setOwnPlayBrightness(value: Float) {
    val attributes = window.attributes
    attributes.screenBrightness = PlayerLocalControlPolicy.clampBrightness(value)
    window.attributes = attributes
}

private enum class PlayerGestureMode {
    BRIGHTNESS,
    VOLUME,
}
