package app.ownplay.mobile.feature.playback.ui

import android.view.SurfaceView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import app.ownplay.mobile.OwnPlayApplication
import app.ownplay.mobile.feature.playback.data.Media3PlaybackEngine
import app.ownplay.mobile.feature.playback.domain.PlaybackPresentation

@Composable
internal fun PlaybackVideoSurface(
    playbackEngine: Media3PlaybackEngine,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val application = context.applicationContext as OwnPlayApplication
    val playbackSessionController = remember(application) {
        application.services.playbackSessionController
    }
    val playbackState by playbackSessionController.state.collectAsState()
    val surfaceView = remember(playbackEngine, context) { SurfaceView(context) }

    DisposableEffect(playbackEngine, surfaceView) {
        playbackEngine.attachVideoSurface(surfaceView)
        onDispose {
            playbackEngine.detachVideoSurface(surfaceView)
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { surfaceView },
            modifier = Modifier.fillMaxSize(),
        )

        if (playbackState.presentation == PlaybackPresentation.FULLSCREEN) {
            PlaybackTrackControlsOverlay(
                state = playbackState,
                controller = playbackSessionController,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
