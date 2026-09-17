package app.ownplay.mobile.feature.playback.data

import android.content.Context
import android.view.SurfaceView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import app.ownplay.mobile.feature.playback.domain.PlaybackEngine
import app.ownplay.mobile.feature.playback.domain.PreparedPlaybackMedia

class Media3PlaybackEngine internal constructor(context: Context) : PlaybackEngine {
    private val player = ExoPlayer.Builder(context.applicationContext).build()
    private var attachedSurfaceView: SurfaceView? = null
    private var hasMedia: Boolean = false

    override fun replace(media: PreparedPlaybackMedia) {
        val mediaItem = MediaItem.Builder()
            .setUri(media.uri)
            .apply {
                media.mimeType?.let(::setMimeType)
            }
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
        hasMedia = true
        player.playWhenReady = attachedSurfaceView != null
    }

    override fun clear() {
        hasMedia = false
        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()
    }

    internal fun attachVideoSurface(surfaceView: SurfaceView) {
        if (attachedSurfaceView === surfaceView) return
        attachedSurfaceView?.let(player::clearVideoSurfaceView)
        player.setVideoSurfaceView(surfaceView)
        attachedSurfaceView = surfaceView
        if (hasMedia) {
            player.playWhenReady = true
        }
    }

    internal fun detachVideoSurface(surfaceView: SurfaceView) {
        if (attachedSurfaceView !== surfaceView) return
        player.clearVideoSurfaceView(surfaceView)
        attachedSurfaceView = null
        player.playWhenReady = false
    }

    internal fun release() {
        attachedSurfaceView?.let(player::clearVideoSurfaceView)
        attachedSurfaceView = null
        hasMedia = false
        player.release()
    }
}
