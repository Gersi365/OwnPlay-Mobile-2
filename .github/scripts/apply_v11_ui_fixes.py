from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 anchor, found {count}")
    return text.replace(old, new, 1)


live_path = Path("app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt")
live = live_path.read_text()

anchor = "    var fallbackLoadRequest by remember { mutableStateOf<PlaybackLoadRequest?>(null) }\n"
live = replace_once(
    live,
    anchor,
    anchor + "    var waitingForInitialChannels by remember(catalog?.activeSourceId) { mutableStateOf(false) }\n",
    "Live loading state",
)

anchor = "    LaunchedEffect(\n        playback.phase,\n"
loading_effect = """    LaunchedEffect(catalog?.activeSourceId, catalog?.channels?.size) {
        val hasActiveSource = catalog?.activeSourceId != null
        val hasNoProviderChannels = catalog?.channels?.isEmpty() == true
        if (hasActiveSource && hasNoProviderChannels) {
            waitingForInitialChannels = true
            delay(12_000)
            waitingForInitialChannels = false
        } else {
            waitingForInitialChannels = false
        }
    }

"""
live = replace_once(live, anchor, loading_effect + anchor, "Live loading effect")

live = replace_once(
    live,
    "            catalog = catalog,\n            channels = visibleChannels,\n",
    "            catalog = catalog,\n            waitingForInitialChannels = waitingForInitialChannels,\n            channels = visibleChannels,\n",
    "Live browse call",
)
live = replace_once(
    live,
    "private fun LiveBrowseAndPreview(\n    catalog: LiveCatalog?,\n    channels: List<LiveChannel>,\n",
    "private fun LiveBrowseAndPreview(\n    catalog: LiveCatalog?,\n    waitingForInitialChannels: Boolean,\n    channels: List<LiveChannel>,\n",
    "Live browse signature",
)

old_empty = """            channels.isEmpty() -> item {
                Box(modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm)) {
                    OwnPlayStatePanel(
                        title = \"No channels available\",
                        message = \"Refresh ${catalog.activeSourceName ?: \"the active source\"} to load Live channels.\",
                    )
                }
            }
"""
new_empty = """            catalog.channels.isEmpty() && waitingForInitialChannels -> item {
                Box(modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm)) {
                    OwnPlayStatePanel(
                        title = \"Loading Live channels…\",
                        message = \"Connecting to ${catalog.activeSourceName ?: \"the active source\"}. Large provider catalogs can take a moment to appear.\",
                    )
                }
            }

            catalog.channels.isEmpty() -> item {
                Box(modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm)) {
                    OwnPlayStatePanel(
                        title = \"Live channels are not ready\",
                        message = \"Channels will appear automatically if the source is still refreshing. Otherwise refresh ${catalog.activeSourceName ?: \"the active source\"} in Settings > Sources.\",
                    )
                }
            }

            channels.isEmpty() -> item {
                Box(modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm)) {
                    OwnPlayStatePanel(
                        title = \"No channels in this category\",
                        message = \"Choose another provider category.\",
                    )
                }
            }
"""
live = replace_once(live, old_empty, new_empty, "Live empty state")
live_path.write_text(live)

library_path = Path("app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryShell.kt")
library = library_path.read_text()
library = replace_once(
    library,
    "import app.ownplay.mobile.design.OwnPlayWordmark\n",
    "",
    "Library wordmark import",
)

old_top = """            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = OwnPlaySpacing.Xl, vertical = OwnPlaySpacing.Lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
            ) {
                OwnPlayWordmark(showTagline = false)
                Text(
                    text = if (playback.offline) \"OFFLINE\" else playback.mediaKind.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = OwnPlayColors.Accent,
                )
            }
"""
new_top = """            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
            ) {
                Text(
                    text = \"‹ LIBRARY\",
                    modifier = Modifier
                        .clickable(onClick = ::closePlayer)
                        .padding(vertical = OwnPlaySpacing.Xs),
                    style = MaterialTheme.typography.labelLarge,
                    color = OwnPlayColors.Accent,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (playback.offline) \"OFFLINE\" else playback.mediaKind.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = OwnPlayColors.TextSecondary,
                )
            }
"""
library = replace_once(library, old_top, new_top, "Library top overlay")

library = replace_once(
    library,
    """                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(OwnPlaySpacing.Xl),
                color = OwnPlayColors.Surface.copy(alpha = 0.94f),
                shape = OwnPlayShapeTokens.Medium,
""",
    """                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Md),
                color = OwnPlayColors.Surface.copy(alpha = 0.86f),
                shape = OwnPlayShapeTokens.Small,
""",
    "Library compact surface",
)

library = replace_once(
    library,
    """                Column(
                    modifier = Modifier.padding(OwnPlaySpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
                ) {
                    Text(playback.title, style = MaterialTheme.typography.titleLarge, color = OwnPlayColors.TextPrimary)
                    playback.subtitle?.let { subtitle ->
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = OwnPlayColors.TextSecondary)
                    }
""",
    """                Column(
                    modifier = Modifier.padding(horizontal = OwnPlaySpacing.Md, vertical = OwnPlaySpacing.Sm),
                    verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs),
                ) {
                    Text(
                        text = playback.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = OwnPlayColors.TextPrimary,
                        maxLines = 1,
                    )
                    playback.subtitle?.let { subtitle ->
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = OwnPlayColors.TextSecondary,
                            maxLines = 1,
                        )
                    }
""",
    "Library compact title",
)

old_controls = """                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
                    ) {
                        OwnPlaySecondaryButton(
                            text = \"−10s\",
                            onClick = {
                                scope.launch {
                                    playbackController.seekTo((playerState.positionMs - 10_000L).coerceAtLeast(0L))
                                }
                            },
                            modifier = Modifier.weight(0.8f),
                        )
                        OwnPlayPrimaryButton(
                            text = if (playerState.playWhenReady) \"Pause\" else \"Play\",
                            onClick = {
                                scope.launch { playbackController.setPlayWhenReady(!playerState.playWhenReady) }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        OwnPlaySecondaryButton(
                            text = \"+10s\",
                            onClick = {
                                scope.launch {
                                    val upper = playerState.durationMs ?: playback.knownDurationMs ?: Long.MAX_VALUE
                                    playbackController.seekTo((playerState.positionMs + 10_000L).coerceAtMost(upper))
                                }
                            },
                            modifier = Modifier.weight(0.8f),
                        )
                        if (playerState.phase == PlaybackPhase.ERROR) {
                            OwnPlaySecondaryButton(
                                text = \"Retry\",
                                onClick = { scope.launch { playbackController.retry() } },
                                modifier = Modifier.weight(0.8f),
                            )
                        }
                        if (playerState.audioTracks.isNotEmpty()) {
                            OwnPlaySecondaryButton(
                                text = \"Audio\",
                                onClick = { audioSelectorVisible = !audioSelectorVisible },
                                modifier = Modifier.weight(0.8f),
                            )
                        }
                    }
                    Text(
                        text = \"BACK TO LIBRARY\",
                        modifier = Modifier
                            .align(Alignment.End)
                            .clickable(onClick = ::closePlayer)
                            .padding(vertical = OwnPlaySpacing.Sm),
                        style = MaterialTheme.typography.labelLarge,
                        color = OwnPlayColors.Accent,
                    )
"""
new_controls = """                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(
                            OwnPlaySpacing.Sm,
                            Alignment.CenterHorizontally,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LibraryPlayerControlButton(
                            text = \"−10s\",
                            onClick = {
                                scope.launch {
                                    playbackController.seekTo((playerState.positionMs - 10_000L).coerceAtLeast(0L))
                                }
                            },
                        )
                        LibraryPlayerControlButton(
                            text = if (playerState.playWhenReady) \"Pause\" else \"Play\",
                            emphasized = true,
                            onClick = {
                                scope.launch { playbackController.setPlayWhenReady(!playerState.playWhenReady) }
                            },
                        )
                        LibraryPlayerControlButton(
                            text = \"+10s\",
                            onClick = {
                                scope.launch {
                                    val upper = playerState.durationMs ?: playback.knownDurationMs ?: Long.MAX_VALUE
                                    playbackController.seekTo((playerState.positionMs + 10_000L).coerceAtMost(upper))
                                }
                            },
                        )
                        if (playerState.phase == PlaybackPhase.ERROR) {
                            LibraryPlayerControlButton(
                                text = \"Retry\",
                                onClick = { scope.launch { playbackController.retry() } },
                            )
                        }
                        if (playerState.audioTracks.isNotEmpty()) {
                            LibraryPlayerControlButton(
                                text = \"Audio\",
                                onClick = { audioSelectorVisible = !audioSelectorVisible },
                            )
                        }
                    }
"""
library = replace_once(library, old_controls, new_controls, "Library compact controls")

marker = "@Composable\nprivate fun LibraryPlaybackSurface(\n"
helper = """@Composable
private fun LibraryPlayerControlButton(
    text: String,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = OwnPlayShapeTokens.Small,
        color = if (emphasized) OwnPlayColors.Accent else OwnPlayColors.Background.copy(alpha = 0.72f),
        border = BorderStroke(
            width = 1.dp,
            color = if (emphasized) OwnPlayColors.Accent else OwnPlayColors.Divider,
        ),
        tonalElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = OwnPlaySpacing.Md, vertical = OwnPlaySpacing.Sm),
            style = MaterialTheme.typography.labelLarge,
            color = if (emphasized) Color.White else OwnPlayColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LibraryPlaybackSurface(
"""
library = replace_once(library, marker, helper, "Library compact control helper")
library_path.write_text(library)
