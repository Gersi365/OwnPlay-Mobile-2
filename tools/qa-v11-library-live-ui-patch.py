from pathlib import Path
import re

ROOT = Path('.')


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly one match, found {count}')
    return text.replace(old, new, 1)


def regex_once(text: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly one regex match, found {count}')
    return updated


library_path = ROOT / 'app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryShell.kt'
library = library_path.read_text()
library = replace_once(
    library,
    'import androidx.compose.ui.text.font.FontWeight\n',
    'import androidx.compose.ui.text.font.FontWeight\nimport androidx.compose.ui.text.style.TextOverflow\n',
    'Library TextOverflow import',
)
library = replace_once(
    library,
    'import app.ownplay.mobile.design.OwnPlayWordmark\n',
    '',
    'remove fullscreen wordmark import',
)

fullscreen_overlay = '''        if (overlayVisible) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
            ) {
                Text(
                    text = "OwnPlay",
                    style = MaterialTheme.typography.titleMedium,
                    color = OwnPlayColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (playback.offline) "OFFLINE" else playback.mediaKind.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = OwnPlayColors.Accent,
                    fontWeight = FontWeight.Bold,
                )
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Md),
                color = Color.Black.copy(alpha = 0.72f),
                shape = OwnPlayShapeTokens.Small,
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = OwnPlaySpacing.Md, vertical = OwnPlaySpacing.Sm),
                    verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playback.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = OwnPlayColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            playback.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OwnPlayColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        val statusText = when (playerState.phase) {
                            PlaybackPhase.BUFFERING -> "BUFFERING"
                            PlaybackPhase.ERROR -> "UNAVAILABLE"
                            else -> null
                        }
                        statusText?.let { status ->
                            Text(
                                text = status,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (playerState.phase == PlaybackPhase.ERROR) {
                                    OwnPlayColors.TextSecondary
                                } else {
                                    OwnPlayColors.Accent
                                },
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    val duration = playerState.durationMs ?: playback.knownDurationMs
                    if (duration != null && duration > 0L) {
                        val sliderPosition = (pendingSeekMs ?: playerState.positionMs).coerceIn(0L, duration)
                        Slider(
                            value = sliderPosition.toFloat(),
                            onValueChange = { value -> pendingSeekMs = value.roundToLong().coerceIn(0L, duration) },
                            onValueChangeFinished = {
                                val destination = pendingSeekMs
                                pendingSeekMs = null
                                if (destination != null) {
                                    scope.launch { playbackController.seekTo(destination) }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp),
                            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = formatDuration(sliderPosition),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = OwnPlayColors.TextSecondary,
                            )
                            Text(
                                text = formatDuration(duration),
                                style = MaterialTheme.typography.bodySmall,
                                color = OwnPlayColors.TextSecondary,
                            )
                        }
                    } else {
                        Text(
                            text = when (playerState.phase) {
                                PlaybackPhase.BUFFERING -> "Preparing playback…"
                                PlaybackPhase.ERROR -> "Playback unavailable"
                                else -> "Preparing duration…"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = OwnPlayColors.TextSecondary,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs),
                    ) {
                        CompactPlayerControl("−10s") {
                            scope.launch {
                                playbackController.seekTo((playerState.positionMs - 10_000L).coerceAtLeast(0L))
                            }
                        }
                        CompactPlayerControl(
                            label = if (playerState.playWhenReady) "PAUSE" else "PLAY",
                            emphasized = true,
                        ) {
                            scope.launch { playbackController.setPlayWhenReady(!playerState.playWhenReady) }
                        }
                        CompactPlayerControl("+10s") {
                            scope.launch {
                                val upper = playerState.durationMs ?: playback.knownDurationMs ?: Long.MAX_VALUE
                                playbackController.seekTo((playerState.positionMs + 10_000L).coerceAtMost(upper))
                            }
                        }
                        if (playerState.phase == PlaybackPhase.ERROR) {
                            CompactPlayerControl("RETRY", emphasized = true) {
                                scope.launch { playbackController.retry() }
                            }
                        }
                        if (playerState.audioTracks.isNotEmpty()) {
                            CompactPlayerControl("AUDIO") {
                                audioSelectorVisible = !audioSelectorVisible
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        CompactPlayerControl("LIBRARY", emphasized = true, onClick = ::closePlayer)
                    }
                }
            }

            if (audioSelectorVisible && playerState.audioTracks.isNotEmpty()) {'''

library = regex_once(
    library,
    r'        if \(overlayVisible\) \{\n.*?\n            if \(audioSelectorVisible && playerState\.audioTracks\.isNotEmpty\(\)\) \{',
    fullscreen_overlay,
    'Library fullscreen overlay',
)

compact_control = '''@Composable
private fun CompactPlayerControl(
    label: String,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = if (emphasized) OwnPlayColors.Accent else OwnPlayColors.TextPrimary,
        fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
        maxLines = 1,
    )
}

'''
library = replace_once(
    library,
    '@Composable\nprivate fun LibraryPlaybackSurface(',
    compact_control + '@Composable\nprivate fun LibraryPlaybackSurface(',
    'CompactPlayerControl insertion',
)
library_path.write_text(library)

live_path = ROOT / 'app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt'
live = live_path.read_text()

live = replace_once(
    live,
    '''private fun LiveBrowseAndPreview(
    catalog: LiveCatalog?,
    channels: List<LiveChannel>,
    categories: List<LiveCategory>,
    liveRepository: LiveRepository,
    selectedCategoryKey: String?,
    onCategorySelected: (String?) -> Unit,
    selectedChannel: LiveChannel?,
    playbackController: PlaybackController,
    controllerScope: CoroutineScope,
    playbackPhase: PlaybackPhase,
    resolutionError: String?,
    selectedGuide: LiveNowNext,
    audioCompatibilityMessage: String?,
    showChannelLogos: Boolean,
    listState: LazyListState,
    onChannelTapped: (LiveChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(selectedChannel?.channelId, selectedCategoryKey, channels, categories) {''',
    '''private fun LiveBrowseAndPreview(
    catalog: LiveCatalog?,
    channels: List<LiveChannel>,
    categories: List<LiveCategory>,
    liveRepository: LiveRepository,
    selectedCategoryKey: String?,
    onCategorySelected: (String?) -> Unit,
    selectedChannel: LiveChannel?,
    playbackController: PlaybackController,
    controllerScope: CoroutineScope,
    playbackPhase: PlaybackPhase,
    resolutionError: String?,
    selectedGuide: LiveNowNext,
    audioCompatibilityMessage: String?,
    showChannelLogos: Boolean,
    listState: LazyListState,
    onChannelTapped: (LiveChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    var emptyChannelWaitElapsed by remember(catalog?.activeSourceId) { mutableStateOf(false) }

    LaunchedEffect(catalog?.activeSourceId, channels.isEmpty()) {
        emptyChannelWaitElapsed = false
        if (catalog?.activeSourceId != null && channels.isEmpty()) {
            delay(12_000)
            emptyChannelWaitElapsed = true
        }
    }

    LaunchedEffect(selectedChannel?.channelId, selectedCategoryKey, channels, categories) {''',
    'Live empty-channel grace state',
)

live = replace_once(
    live,
    '''            channels.isEmpty() -> item {
                Box(modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm)) {
                    OwnPlayStatePanel(
                        title = "No channels available",
                        message = "Refresh ${catalog.activeSourceName ?: "the active source"} to load Live channels.",
                    )
                }
            }
''',
    '''            channels.isEmpty() -> item {
                Box(modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm)) {
                    OwnPlayStatePanel(
                        title = if (emptyChannelWaitElapsed) {
                            "Still waiting for Live channels"
                        } else {
                            "Loading Live channels…"
                        },
                        message = if (emptyChannelWaitElapsed) {
                            "No channel data has arrived yet from ${catalog.activeSourceName ?: "the active source"}. " +
                                "Refresh the source in Settings if this persists."
                        } else {
                            "Waiting for ${catalog.activeSourceName ?: "the active source"} to provide channel data."
                        },
                    )
                }
            }
''',
    'Live empty-channel state panel',
)
live_path.write_text(live)

print('Applied focused QA v11 Library/Live UI patch.')
