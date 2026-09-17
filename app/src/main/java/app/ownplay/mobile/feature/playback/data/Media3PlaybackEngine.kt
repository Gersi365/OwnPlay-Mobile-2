package app.ownplay.mobile.feature.playback.data

import android.content.Context
import android.view.SurfaceView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.ownplay.mobile.feature.playback.domain.PlaybackEngine
import app.ownplay.mobile.feature.playback.domain.PlaybackEngineEvent
import app.ownplay.mobile.feature.playback.domain.PlaybackEngineReadiness
import app.ownplay.mobile.feature.playback.domain.PreparedPlaybackMedia

class Media3PlaybackEngine internal constructor(context: Context) {
    private val player = ExoPlayer.Builder(context.applicationContext).build()
    private var attachedSurfaceView: SurfaceView? = null
    private var hasMedia: Boolean = false
    private var nextMediaRevision: Long = 0L
    private var activeMediaRevision: Long? = null
    private var eventListener: ((PlaybackEngineEvent) -> Unit)? = null
    private var released: Boolean = false

    init {
        player.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> emit(PlaybackEngineReadiness.PREPARING)
                        Player.STATE_READY -> emit(PlaybackEngineReadiness.READY)
                        Player.STATE_ENDED -> emit(PlaybackEngineReadiness.FAILED)
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    emit(PlaybackEngineReadiness.FAILED)
                }
            },
        )
    }

    internal fun setEventListener(listener: (PlaybackEngineEvent) -> Unit) {
        eventListener = listener
    }

    internal fun replace(media: PreparedPlaybackMedia): Long {
        check(!released) { "Playback engine has been released" }

        nextMediaRevision += 1L
        val revision = nextMediaRevision
        activeMediaRevision = revision
        hasMedia = true
        emit(PlaybackEngineReadiness.PREPARING)

        val mediaItem = MediaItem.Builder()
            .setUri(media.uri)
            .apply {
                media.mimeType?.let(::setMimeType)
            }
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = attachedSurfaceView != null
        return revision
    }

    internal fun clear() {
        if (released) return
        activeMediaRevision = null
        hasMedia = false
        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()
    }

    internal fun attachVideoSurface(surfaceView: SurfaceView) {
        check(!released) { "Playback engine has been released" }
        if (attachedSurfaceView === surfaceView) return
        attachedSurfaceView?.let(player::clearVideoSurfaceView)
        player.setVideoSurfaceView(surfaceView)
        attachedSurfaceView = surfaceView
        if (hasMedia) {
            player.playWhenReady = true
        }
    }

    internal fun detachVideoSurface(surfaceView: SurfaceView) {
        if (released || attachedSurfaceView !== surfaceView) return
        player.clearVideoSurfaceView(surfaceView)
        attachedSurfaceView = null
        player.playWhenReady = false
    }

    internal fun release() {
        if (released) return
        released = true
        activeMediaRevision = null
        eventListener = null
        attachedSurfaceView?.let(player::clearVideoSurfaceView)
        attachedSurfaceView = null
        hasMedia = false
        player.release()
    }

    private fun emit(readiness: PlaybackEngineReadiness) {
        val revision = activeMediaRevision ?: return
        eventListener?.invoke(
            PlaybackEngineEvent(
                mediaRevision = revision,
                readiness = readiness,
            ),
        )
    }
}

internal class Media3PlaybackEngineAdapter(
    private val engine: Media3PlaybackEngine,
) : PlaybackEngine {
    override fun setEventListener(listener: (PlaybackEngineEvent) -> Unit) {
        engine.setEventListener(listener)
    }

    override fun replace(media: PreparedPlaybackMedia): Long = engine.replace(media)

    override fun clear() {
        engine.clear()
    }

    override fun release() {
        engine.release()
    }
}
