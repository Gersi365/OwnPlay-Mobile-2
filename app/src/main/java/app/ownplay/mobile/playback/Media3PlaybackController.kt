package app.ownplay.mobile.playback

import android.content.Context
import android.os.Looper
import android.view.SurfaceView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.ownplay.mobile.playback.domain.PlaybackLoadRequest
import app.ownplay.mobile.playback.domain.PlaybackMedia
import app.ownplay.mobile.playback.domain.PlaybackPhase
import app.ownplay.mobile.playback.domain.PlaybackSnapshot
import app.ownplay.mobile.playback.domain.PlaybackStartPolicy
import app.ownplay.mobile.playback.domain.PlaybackStreamFormat
import app.ownplay.mobile.playback.domain.VideoTarget
import app.ownplay.mobile.playback.domain.VideoTargetEvent
import app.ownplay.mobile.playback.domain.VideoTargetOwnership
import app.ownplay.mobile.playback.domain.VideoTargetOwnershipReducer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class Media3PlaybackController(
    context: Context,
) : PlaybackController {
    private data class BoundSurface(
        val target: VideoTarget,
        val surfaceView: SurfaceView,
    )

    private val player = ExoPlayer.Builder(context.applicationContext)
        .setLooper(Looper.getMainLooper())
        .build()

    private val mutationMutex = Mutex()
    private val mutableState = MutableStateFlow(PlaybackSnapshot())

    override val state: StateFlow<PlaybackSnapshot> = mutableState.asStateFlow()

    private var ownership = VideoTargetOwnership()
    private var boundSurface: BoundSurface? = null
    private var currentMedia: PlaybackMedia? = null
    private var released = false

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            refreshSnapshot()
        }

        override fun onPlayWhenReadyChanged(
            playWhenReady: Boolean,
            reason: Int,
        ) {
            refreshSnapshot()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            refreshSnapshot()
        }

        override fun onPlayerError(error: PlaybackException) {
            refreshSnapshot(errorCode = error.errorCode)
        }
    }

    init {
        player.addListener(listener)
    }

    override suspend fun load(request: PlaybackLoadRequest) {
        mutateOnPlayerThread {
            currentMedia = request.media
            val mediaItemBuilder = MediaItem.Builder()
                .setMediaId(request.media.id)
                .setUri(request.media.uri)

            if (request.media.streamFormat == PlaybackStreamFormat.HLS) {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            }

            player.setMediaItem(mediaItemBuilder.build())

            val startDecision = PlaybackStartPolicy.resolve(request.start)
            startDecision.positionMs?.let { positionMs ->
                player.seekTo(positionMs)
            }

            player.playWhenReady = request.playWhenReady
            player.prepare()
            refreshSnapshot(errorCode = null)
        }
    }

    override suspend fun bindVideoTarget(
        target: VideoTarget,
        surfaceView: SurfaceView,
    ) {
        require(target != VideoTarget.NONE) { "NONE cannot own a video surface." }

        mutateOnPlayerThread {
            val existing = boundSurface
            if (existing != null && existing.target == target && existing.surfaceView === surfaceView) {
                return@mutateOnPlayerThread
            }

            existing?.let { player.clearVideoSurfaceView(it.surfaceView) }

            player.setVideoSurfaceView(surfaceView)
            boundSurface = BoundSurface(target = target, surfaceView = surfaceView)
            ownership = VideoTargetOwnershipReducer.reduce(
                state = ownership,
                event = VideoTargetEvent.Acquire(target),
            )
            refreshSnapshot()
        }
    }

    override suspend fun unbindVideoTarget(
        target: VideoTarget,
        surfaceView: SurfaceView,
    ) {
        require(target != VideoTarget.NONE) { "NONE cannot own a video surface." }

        mutateOnPlayerThread {
            val existing = boundSurface ?: return@mutateOnPlayerThread
            if (existing.target != target || existing.surfaceView !== surfaceView) {
                return@mutateOnPlayerThread
            }

            player.clearVideoSurfaceView(surfaceView)
            boundSurface = null
            ownership = VideoTargetOwnershipReducer.reduce(
                state = ownership,
                event = VideoTargetEvent.Release(target),
            )
            refreshSnapshot()
        }
    }

    override suspend fun setPlayWhenReady(shouldPlay: Boolean) {
        mutateOnPlayerThread {
            player.playWhenReady = shouldPlay
            refreshSnapshot()
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        mutateOnPlayerThread {
            player.seekTo(positionMs.coerceAtLeast(0L))
            refreshSnapshot()
        }
    }

    override suspend fun retry() {
        mutateOnPlayerThread {
            mutableState.value = mutableState.value.copy(errorCode = null)
            player.prepare()
            refreshSnapshot(errorCode = null)
        }
    }

    override suspend fun stop(clearMedia: Boolean) {
        mutateOnPlayerThread {
            player.stop()
            if (clearMedia) {
                clearBoundSurface()
                player.clearMediaItems()
                currentMedia = null
            }
            refreshSnapshot(errorCode = null)
        }
    }

    override suspend fun release() {
        mutationMutex.withLock {
            withContext(Dispatchers.Main.immediate) {
                if (released) {
                    return@withContext
                }

                clearBoundSurface()
                player.removeListener(listener)
                player.release()
                currentMedia = null
                released = true
                mutableState.value = PlaybackSnapshot(phase = PlaybackPhase.RELEASED)
            }
        }
    }

    private suspend fun mutateOnPlayerThread(block: () -> Unit) {
        mutationMutex.withLock {
            withContext(Dispatchers.Main.immediate) {
                check(!released) { "Playback controller has been released." }
                block()
            }
        }
    }

    private fun clearBoundSurface() {
        val existing = boundSurface ?: return
        player.clearVideoSurfaceView(existing.surfaceView)
        boundSurface = null
        ownership = VideoTargetOwnershipReducer.reduce(
            state = ownership,
            event = VideoTargetEvent.Release(existing.target),
        )
    }

    private fun refreshSnapshot(errorCode: Int? = player.playerError?.errorCode) {
        if (released) {
            return
        }

        val media = currentMedia
        mutableState.value = PlaybackSnapshot(
            mediaId = media?.id,
            title = media?.title,
            kind = media?.kind,
            phase = if (errorCode != null) PlaybackPhase.ERROR else player.playbackState.toPlaybackPhase(),
            playWhenReady = player.playWhenReady,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeUnless { it == C.TIME_UNSET || it < 0L },
            activeTarget = ownership.activeTarget,
            errorCode = errorCode,
        )
    }

    private fun Int.toPlaybackPhase(): PlaybackPhase = when (this) {
        Player.STATE_BUFFERING -> PlaybackPhase.BUFFERING
        Player.STATE_READY -> PlaybackPhase.READY
        Player.STATE_ENDED -> PlaybackPhase.ENDED
        else -> PlaybackPhase.IDLE
    }
}
