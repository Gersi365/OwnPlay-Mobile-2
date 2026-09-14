package app.ownplay.mobile.feature.library.ui

import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import app.ownplay.mobile.design.OwnPlaySpacing
import app.ownplay.mobile.feature.library.domain.LibraryMediaKind
import app.ownplay.mobile.feature.library.domain.LibraryRepository
import app.ownplay.mobile.feature.library.domain.PlaybackProgressUpdate
import app.ownplay.mobile.feature.library.domain.ResolvedLibraryPlayback
import app.ownplay.mobile.playback.PlaybackController
import app.ownplay.mobile.playback.domain.PlaybackKind
import app.ownplay.mobile.playback.domain.PlaybackLoadRequest
import app.ownplay.mobile.playback.domain.PlaybackMedia
import app.ownplay.mobile.playback.domain.PlaybackPhase
import app.ownplay.mobile.playback.domain.PlaybackSnapshot
import app.ownplay.mobile.playback.domain.VideoTarget
import app.ownplay.mobile.playback.ui.PlaybackOptionsPanel
import app.ownplay.mobile.playback.ui.PlaybackSubtitleOverlay
import app.ownplay.mobile.playback.ui.PlayerGlassGlyph
import app.ownplay.mobile.playback.ui.PlayerGlassIconAction
import app.ownplay.mobile.playback.ui.PlayerGlassPillAction
import app.ownplay.mobile.playback.ui.PlayerGlassScrims
import app.ownplay.mobile.playback.ui.PlayerGlassSeekBar
import app.ownplay.mobile.playback.ui.PlayerLocalControlHudOverlay
import app.ownplay.mobile.playback.ui.playerLocalVerticalControls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun LibraryFullscreenPlayerStage33(
    playback: ResolvedLibraryPlayback,
    libraryRepository: LibraryRepository,
    playbackController: PlaybackController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerState by playbackController.state.collectAsState()
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    var overlayVisible by remember(playback.contentId, playback.offline) { mutableStateOf(false) }
    var optionsVisible by remember(playback.contentId, playback.offline) { mutableStateOf(false) }
    var pendingSeekMs by remember(playback.contentId, playback.offline) { mutableStateOf<Long?>(null) }

    suspend fun persistSnapshot(snapshot: PlaybackSnapshot) {
        if (snapshot.mediaId != playback.contentId) return
        val duration = snapshot.durationMs ?: playback.knownDurationMs ?: return
        if (duration <= 0L) return
        libraryRepository.saveProgress(
            PlaybackProgressUpdate(
                sourceId = playback.sourceId,
                mediaKind = playback.mediaKind,
                contentId = playback.contentId,
                positionMs = snapshot.positionMs,
                durationMs = duration,
                ended = snapshot.phase == PlaybackPhase.ENDED,
            ),
        )
    }

    suspend fun persistCurrent() {
        persistSnapshot(playbackController.currentSnapshot())
    }

    fun closePlayer() {
        scope.launch {
            persistCurrent()
            playbackController.stop(clearMedia = true)
            onClose()
        }
    }

    BackHandler(onBack = ::closePlayer)

    LaunchedEffect(
        playback.contentId,
        playback.offline,
        overlayVisible,
        optionsVisible,
        playerState.isPlaying,
        playerState.phase,
    ) {
        if (
            overlayVisible &&
            !optionsVisible &&
            LibraryPlayerControlsPolicy.shouldAutoHide(playerState)
        ) {
            delay(4_000)
            if (
                !optionsVisible &&
                LibraryPlayerControlsPolicy.shouldAutoHide(playbackController.currentSnapshot())
            ) {
                overlayVisible = false
            }
        }
    }

    LaunchedEffect(overlayVisible) {
        if (!overlayVisible) optionsVisible = false
    }

    LaunchedEffect(playback.contentId, playback.offline) {
        var persistCountdown = 0
        while (true) {
            delay(2_000)
            val snapshot = playbackController.currentSnapshot()
            persistCountdown += 1
            if (persistCountdown >= 3) {
                persistSnapshot(snapshot)
                persistCountdown = 0
            }
        }
    }

    LaunchedEffect(playback.contentId, playback.offline, playerState.phase) {
        if (
            playerState.mediaId == playback.contentId &&
            (playerState.phase == PlaybackPhase.READY || playerState.phase == PlaybackPhase.ENDED)
        ) {
            persistSnapshot(playerState)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        LibraryPlaybackSurfaceStage33(
            playbackController = playbackController,
            controllerScope = scope,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .playerLocalVerticalControls(playbackController, scope)
                .clickable(interactionSource = interactionSource, indication = null) {
                    overlayVisible = !overlayVisible
                },
        )

        PlayerLocalControlHudOverlay(modifier = Modifier.align(Alignment.Center))

        PlaybackSubtitleOverlay(
            cues = playerState.subtitleCues,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.86f)
                .padding(bottom = if (overlayVisible) 104.dp else 24.dp),
        )

        if (overlayVisible) {
            PlayerGlassScrims()

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
            ) {
                PlayerGlassIconAction(
                    glyph = PlayerGlassGlyph.BACK,
                    contentDescription = "Back",
                    onClick = ::closePlayer,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = playback.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                    )
                    Text(
                        text = playback.subtitle
                            ?: if (playback.offline) {
                                "Offline"
                            } else {
                                playback.mediaKind.name.lowercase().replaceFirstChar { it.uppercase() }
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.68f),
                        maxLines = 1,
                    )
                }
            }

            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerGlassIconAction(
                    glyph = PlayerGlassGlyph.REWIND_10,
                    contentDescription = "Rewind 10 seconds",
                    onClick = {
                        scope.launch {
                            playbackController.seekTo((playerState.positionMs - 10_000L).coerceAtLeast(0L))
                        }
                    },
                )
                PlayerGlassIconAction(
                    glyph = if (playerState.playWhenReady) PlayerGlassGlyph.PAUSE else PlayerGlassGlyph.PLAY,
                    contentDescription = if (playerState.playWhenReady) "Pause" else "Resume",
                    emphasized = true,
                    onClick = {
                        scope.launch { playbackController.setPlayWhenReady(!playerState.playWhenReady) }
                    },
                )
                PlayerGlassIconAction(
                    glyph = PlayerGlassGlyph.FORWARD_10,
                    contentDescription = "Forward 10 seconds",
                    onClick = {
                        scope.launch {
                            val upper = playerState.durationMs ?: playback.knownDurationMs ?: Long.MAX_VALUE
                            playbackController.seekTo((playerState.positionMs + 10_000L).coerceAtMost(upper))
                        }
                    },
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.92f)
                    .padding(bottom = OwnPlaySpacing.Md),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val duration = playerState.durationMs ?: playback.knownDurationMs
                if (duration != null && duration > 0L) {
                    val seekPosition = (pendingSeekMs ?: playerState.positionMs).coerceIn(0L, duration)
                    PlayerGlassSeekBar(
                        fraction = seekPosition.toFloat() / duration.toFloat(),
                        onFractionChange = { fraction ->
                            pendingSeekMs = (duration.toDouble() * fraction.toDouble()).toLong().coerceIn(0L, duration)
                        },
                        onChangeFinished = {
                            val destination = pendingSeekMs
                            pendingSeekMs = null
                            if (destination != null) scope.launch { playbackController.seekTo(destination) }
                        },
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            formatLibraryDuration(seekPosition),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f),
                        )
                        Text(
                            formatLibraryDuration(duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f),
                        )
                    }
                } else {
                    Text(
                        text = when (playerState.phase) {
                            PlaybackPhase.BUFFERING -> "Buffering…"
                            PlaybackPhase.ERROR -> playerState.errorMessage ?: "Playback unavailable"
                            else -> "Preparing duration…"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (playerState.phase == PlaybackPhase.ERROR) {
                        PlayerGlassPillAction(
                            text = "Retry",
                            onClick = { scope.launch { playbackController.retry() } },
                        )
                    }
                    PlayerGlassPillAction(
                        text = "Options",
                        emphasized = optionsVisible,
                        onClick = { optionsVisible = !optionsVisible },
                    )
                }
            }

            if (optionsVisible) {
                PlaybackOptionsPanel(
                    audioTracks = playerState.audioTracks,
                    subtitleTracks = playerState.subtitleTracks,
                    subtitleSelection = playerState.subtitleSelection,
                    playbackSpeed = playerState.playbackSpeed,
                    allowSpeed = true,
                    onSelectAudio = { selectionId ->
                        scope.launch { playbackController.selectAudioTrack(selectionId) }
                    },
                    onSelectSubtitle = { selection ->
                        scope.launch { playbackController.selectSubtitle(selection) }
                    },
                    onSelectSpeed = { speed ->
                        scope.launch { playbackController.setPlaybackSpeed(speed) }
                    },
                    onDismiss = { optionsVisible = false },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(OwnPlaySpacing.Lg),
                )
            }
        }
    }
}

@Composable
private fun LibraryPlaybackSurfaceStage33(
    playbackController: PlaybackController,
    controllerScope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val surfaceView = remember(context) { SurfaceView(context).apply { keepScreenOn = true } }
    AndroidView(factory = { surfaceView }, modifier = modifier)

    DisposableEffect(playbackController, surfaceView) {
        controllerScope.launch { playbackController.bindVideoTarget(VideoTarget.FULLSCREEN, surfaceView) }
        onDispose {
            controllerScope.launch { playbackController.unbindVideoTarget(VideoTarget.FULLSCREEN, surfaceView) }
        }
    }
}

internal fun ResolvedLibraryPlayback.toStage33LoadRequest(): PlaybackLoadRequest = PlaybackLoadRequest(
    media = PlaybackMedia(
        id = contentId,
        uri = uri,
        title = title,
        kind = if (offline) {
            PlaybackKind.OFFLINE
        } else {
            when (mediaKind) {
                LibraryMediaKind.MOVIE -> PlaybackKind.MOVIE
                LibraryMediaKind.EPISODE -> PlaybackKind.EPISODE
            }
        },
        streamFormat = streamFormat,
    ),
    start = start,
)
