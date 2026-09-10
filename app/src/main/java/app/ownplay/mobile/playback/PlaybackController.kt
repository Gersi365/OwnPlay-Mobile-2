package app.ownplay.mobile.playback

import android.view.SurfaceView
import app.ownplay.mobile.playback.domain.PlaybackLoadRequest
import app.ownplay.mobile.playback.domain.PlaybackSnapshot
import app.ownplay.mobile.playback.domain.VideoTarget
import kotlinx.coroutines.flow.StateFlow

interface PlaybackController {
    val state: StateFlow<PlaybackSnapshot>

    suspend fun load(request: PlaybackLoadRequest)

    suspend fun bindVideoTarget(
        target: VideoTarget,
        surfaceView: SurfaceView,
    )

    suspend fun unbindVideoTarget(
        target: VideoTarget,
        surfaceView: SurfaceView,
    )

    suspend fun setPlayWhenReady(shouldPlay: Boolean)

    suspend fun seekTo(positionMs: Long)

    suspend fun retry()

    suspend fun stop(clearMedia: Boolean = true)

    suspend fun release()
}
