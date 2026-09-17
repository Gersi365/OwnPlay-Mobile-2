package app.ownplay.mobile.feature.playback.ui

import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import app.ownplay.mobile.feature.playback.data.Media3PlaybackEngine

@Composable
internal fun PlaybackVideoSurface(
    playbackEngine: Media3PlaybackEngine,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val surfaceView = remember(playbackEngine, context) { SurfaceView(context) }

    DisposableEffect(playbackEngine, surfaceView) {
        playbackEngine.attachVideoSurface(surfaceView)
        onDispose {
            playbackEngine.detachVideoSurface(surfaceView)
        }
    }

    AndroidView(
        factory = { surfaceView },
        modifier = modifier,
    )
}
