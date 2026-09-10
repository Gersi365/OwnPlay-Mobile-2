package app.ownplay.mobile.feature.live.ui

import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayPanel
import app.ownplay.mobile.design.OwnPlaySectionHeader
import app.ownplay.mobile.design.OwnPlayShapeTokens
import app.ownplay.mobile.design.OwnPlaySpacing
import app.ownplay.mobile.design.OwnPlayStatePanel
import app.ownplay.mobile.design.OwnPlayTopBar
import app.ownplay.mobile.design.OwnPlayWordmark
import app.ownplay.mobile.feature.live.domain.LiveCatalog
import app.ownplay.mobile.feature.live.domain.LiveCategory
import app.ownplay.mobile.feature.live.domain.LiveChannel
import app.ownplay.mobile.feature.live.domain.LiveEffect
import app.ownplay.mobile.feature.live.domain.LiveIntent
import app.ownplay.mobile.feature.live.domain.LivePlaybackResolution
import app.ownplay.mobile.feature.live.domain.LivePresentation
import app.ownplay.mobile.feature.live.domain.LivePresentationReducer
import app.ownplay.mobile.feature.live.domain.LivePresentationState
import app.ownplay.mobile.feature.live.domain.LiveRepository
import app.ownplay.mobile.playback.PlaybackController
import app.ownplay.mobile.playback.domain.PlaybackKind
import app.ownplay.mobile.playback.domain.PlaybackLoadRequest
import app.ownplay.mobile.playback.domain.PlaybackMedia
import app.ownplay.mobile.playback.domain.PlaybackPhase
import app.ownplay.mobile.playback.domain.VideoTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LiveShell(
    liveRepository: LiveRepository,
    playbackController: PlaybackController,
    onFullscreenChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val catalogFlow = remember(liveRepository) { liveRepository.observeCatalog() }
    val catalog by catalogFlow.collectAsState(initial = null)
    val playback by playbackController.state.collectAsState()
    val scope = rememberCoroutineScope()

    var presentationState by remember { mutableStateOf(LivePresentationState()) }
    var resolutionError by remember { mutableStateOf<String?>(null) }
    var fallbackLoadRequest by remember { mutableStateOf<PlaybackLoadRequest?>(null) }

    fun applyEffect(effect: LiveEffect) {
        when (effect) {
            is LiveEffect.LoadChannel -> scope.launch {
                resolutionError = null
                when (val resolved = liveRepository.resolvePlayback(effect.channelId)) {
                    is LivePlaybackResolution.Success -> {
                        fallbackLoadRequest = resolved.value.fallbackUri?.let { fallbackUri ->
                            PlaybackLoadRequest(
                                media = PlaybackMedia(
                                    id = resolved.value.channel.channelId,
                                    uri = fallbackUri,
                                    title = resolved.value.channel.name,
                                    kind = PlaybackKind.LIVE,
                                    streamFormat = resolved.value.fallbackStreamFormat ?: resolved.value.streamFormat,
                                ),
                            )
                        }
                        playbackController.load(
                            PlaybackLoadRequest(
                                media = PlaybackMedia(
                                    id = resolved.value.channel.channelId,
                                    uri = resolved.value.uri,
                                    title = resolved.value.channel.name,
                                    kind = PlaybackKind.LIVE,
                                    streamFormat = resolved.value.streamFormat,
                                ),
                            ),
                        )
                    }

                    is LivePlaybackResolution.Failure -> {
                        fallbackLoadRequest = null
                        playbackController.stop(clearMedia = true)
                        resolutionError = resolved.safeMessage
                    }
                }
            }

            LiveEffect.StopPlayback -> scope.launch {
                resolutionError = null
                fallbackLoadRequest = null
                playbackController.stop(clearMedia = true)
            }
        }
    }

    fun dispatch(intent: LiveIntent) {
        val transition = LivePresentationReducer.reduce(presentationState, intent)
        val wasFullscreen = presentationState.presentation == LivePresentation.FULLSCREEN
        val isFullscreen = transition.state.presentation == LivePresentation.FULLSCREEN
        if (wasFullscreen != isFullscreen) {
            onFullscreenChanged(isFullscreen)
        }
        presentationState = transition.state
        transition.effects.forEach(::applyEffect)
    }

    val channels = catalog?.channels.orEmpty()
    val categories = LiveBrowsePolicy.visibleCategories(catalog?.categories.orEmpty())
    var selectedCategoryKey by remember(catalog?.activeSourceId) { mutableStateOf<String?>(null) }
    val activeCategoryKey = LiveBrowsePolicy.activeCategoryKey(categories, selectedCategoryKey)
    val visibleChannels = activeCategoryKey?.let { key ->
        channels.filter { channel -> channel.categoryKey == key }
    } ?: channels
    val selectedChannel = channels.firstOrNull { it.channelId == presentationState.selectedChannelId }

    LaunchedEffect(categories, selectedCategoryKey) {
        val resolvedCategoryKey = LiveBrowsePolicy.activeCategoryKey(categories, selectedCategoryKey)
        if (selectedCategoryKey != resolvedCategoryKey) {
            selectedCategoryKey = resolvedCategoryKey
        }
    }

    LaunchedEffect(playback.phase, fallbackLoadRequest) {
        if (playback.phase == PlaybackPhase.ERROR) {
            val fallback = fallbackLoadRequest ?: return@LaunchedEffect
            fallbackLoadRequest = null
            playbackController.load(fallback)
        }
    }

    DisposableEffect(Unit) {
        onDispose { onFullscreenChanged(false) }
    }

    LaunchedEffect(catalog?.activeSourceId, channels, presentationState.selectedChannelId) {
        val selectedId = presentationState.selectedChannelId
        if (selectedId != null && channels.none { it.channelId == selectedId }) {
            dispatch(LiveIntent.ChannelUnavailable(selectedId))
        }
    }

    BackHandler(enabled = presentationState.presentation != LivePresentation.BROWSE) {
        dispatch(LiveIntent.BackPressed)
    }

    if (presentationState.presentation == LivePresentation.FULLSCREEN && selectedChannel != null) {
        val channelNumber = channelNumber(channels, selectedChannel)
        FullscreenLive(
            channel = selectedChannel,
            channelNumber = channelNumber,
            playbackController = playbackController,
            controllerScope = scope,
            playbackPhase = playback.phase,
            resolutionError = resolutionError,
            modifier = modifier,
        )
    } else {
        LiveBrowseAndPreview(
            catalog = catalog,
            channels = visibleChannels,
            categories = categories,
            selectedCategoryKey = activeCategoryKey,
            onCategorySelected = { categoryKey ->
                selectedCategoryKey = categoryKey
                if (
                    presentationState.presentation == LivePresentation.PREVIEW &&
                    selectedChannel?.categoryKey != categoryKey
                ) {
                    dispatch(LiveIntent.BackPressed)
                }
            },
            selectedChannel = selectedChannel,
            playbackController = playbackController,
            controllerScope = scope,
            playbackPhase = playback.phase,
            resolutionError = resolutionError,
            onChannelTapped = { channel ->
                dispatch(LiveIntent.ChannelTapped(channel.channelId))
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun LiveBrowseAndPreview(
    catalog: LiveCatalog?,
    channels: List<LiveChannel>,
    categories: List<LiveCategory>,
    selectedCategoryKey: String?,
    onCategorySelected: (String?) -> Unit,
    selectedChannel: LiveChannel?,
    playbackController: PlaybackController,
    controllerScope: CoroutineScope,
    playbackPhase: PlaybackPhase,
    resolutionError: String?,
    onChannelTapped: (LiveChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
    ) {
        item {
            OwnPlayTopBar(showTagline = false)
        }

        item {
            Box(modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg)) {
                OwnPlaySectionHeader(
                    title = "Channels",
                    actionLabel = if (selectedChannel == null) {
                        "Tap a channel to preview"
                    } else {
                        "Tap the selected channel again for fullscreen"
                    },
                )
            }
        }

        if (categories.isNotEmpty()) {
            item {
                LiveCategoryStrip(
                    categories = categories,
                    selectedCategoryKey = selectedCategoryKey,
                    onSelected = onCategorySelected,
                )
            }
        }

        when {
            catalog == null -> item {
                Box(modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm)) {
                    OwnPlayStatePanel(
                        title = "Loading Live",
                        message = "Reading the active source and cached channels.",
                    )
                }
            }

            catalog.activeSourceId == null -> item {
                Box(modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm)) {
                    OwnPlayStatePanel(
                        title = "No active source",
                        message = "Add or select a source in Settings to populate Live channels.",
                    )
                }
            }

            channels.isEmpty() -> item {
                Box(modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm)) {
                    OwnPlayStatePanel(
                        title = "No channels available",
                        message = "Refresh ${catalog.activeSourceName ?: "the active source"} to load Live channels.",
                    )
                }
            }

            else -> itemsIndexed(
                items = channels,
                key = { _, channel -> channel.channelId },
            ) { index, channel ->
                val selected = channel.channelId == selectedChannel?.channelId
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Xs),
                    verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs),
                ) {
                    if (selected) {
                        PreviewSurface(
                            selectedChannel = channel,
                            playbackController = playbackController,
                            controllerScope = controllerScope,
                            playbackPhase = playbackPhase,
                            resolutionError = resolutionError,
                        )
                    }

                    ChannelRow(
                        number = (index + 1).toString().padStart(3, '0'),
                        channel = channel,
                        selected = selected,
                        onClick = { onChannelTapped(channel) },
                    )

                    if (selected) {
                        NowPlayingPanel(selectedChannel = channel)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(OwnPlaySpacing.Xl))
        }
    }
}

@Composable
private fun LiveCategoryStrip(
    categories: List<LiveCategory>,
    selectedCategoryKey: String?,
    onSelected: (String?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = OwnPlaySpacing.Lg),
        horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
    ) {
        items(categories, key = { it.categoryKey }) { category ->
            ProviderCategoryChip(category.name, selectedCategoryKey == category.categoryKey) {
                onSelected(category.categoryKey)
            }
        }
    }
}

@Composable
private fun ProviderCategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = OwnPlayShapeTokens.Small,
        color = if (selected) OwnPlayColors.AccentSoft else OwnPlayColors.SurfaceElevated,
        border = BorderStroke(1.dp, if (selected) OwnPlayColors.Accent else Color.Transparent),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = OwnPlaySpacing.Md, vertical = OwnPlaySpacing.Sm),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) OwnPlayColors.TextPrimary else OwnPlayColors.TextSecondary,
        )
    }
}

@Composable
private fun PreviewSurface(
    selectedChannel: LiveChannel?,
    playbackController: PlaybackController,
    controllerScope: CoroutineScope,
    playbackPhase: PlaybackPhase,
    resolutionError: String?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(OwnPlayShapeTokens.Medium)
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (selectedChannel == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Live Preview",
                    style = MaterialTheme.typography.titleLarge,
                    color = OwnPlayColors.TextPrimary,
                )
                Spacer(modifier = Modifier.height(OwnPlaySpacing.Xs))
                Text(
                    text = "Select a channel below",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OwnPlayColors.TextSecondary,
                )
            }
        } else {
            LivePlaybackSurface(
                target = VideoTarget.PREVIEW,
                playbackController = playbackController,
                controllerScope = controllerScope,
                modifier = Modifier.fillMaxSize(),
            )

            LiveBadge(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(OwnPlaySpacing.Md),
            )

            val statusMessage = when {
                resolutionError != null -> resolutionError
                playbackPhase == PlaybackPhase.ERROR -> "Playback unavailable"
                playbackPhase == PlaybackPhase.BUFFERING -> "Loading ${selectedChannel.name}…"
                else -> null
            }
            if (statusMessage != null) {
                Surface(
                    color = OwnPlayColors.Background.copy(alpha = 0.88f),
                    shape = OwnPlayShapeTokens.Small,
                ) {
                    Text(
                        text = statusMessage,
                        modifier = Modifier.padding(horizontal = OwnPlaySpacing.Md, vertical = OwnPlaySpacing.Sm),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OwnPlayColors.TextPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun NowPlayingPanel(selectedChannel: LiveChannel?) {
    OwnPlayPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(OwnPlaySpacing.Lg),
            horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Lg),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Now Playing",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OwnPlayColors.TextSecondary,
                )
                Text(
                    text = selectedChannel?.name ?: "Select a channel",
                    style = MaterialTheme.typography.titleLarge,
                    color = OwnPlayColors.TextPrimary,
                )
                Text(
                    text = if (selectedChannel == null) "Live preview is idle" else "Live channel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OwnPlayColors.TextSecondary,
                )
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(86.dp)
                    .background(OwnPlayColors.Divider),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Next",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OwnPlayColors.TextSecondary,
                )
                Text(
                    text = "Guide unavailable",
                    style = MaterialTheme.typography.titleMedium,
                    color = OwnPlayColors.TextPrimary,
                )
                Text(
                    text = "EPG is optional and never blocks playback",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OwnPlayColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun ChannelRow(
    number: String,
    channel: LiveChannel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = if (selected) OwnPlayColors.SurfaceSelected else OwnPlayColors.Surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) OwnPlayColors.Accent else OwnPlayColors.Divider,
        ),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.bodyMedium,
                color = OwnPlayColors.TextSecondary,
                modifier = Modifier.width(44.dp),
            )
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(OwnPlayShapeTokens.Small)
                    .background(if (selected) OwnPlayColors.AccentStrong else OwnPlayColors.SurfaceElevated),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = channel.name.firstOrNull()?.uppercaseChar()?.toString() ?: "•",
                    style = MaterialTheme.typography.titleMedium,
                    color = OwnPlayColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.width(OwnPlaySpacing.Md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = OwnPlayColors.TextPrimary,
                )
                Text(
                    text = if (selected) "Previewing now" else "Live channel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OwnPlayColors.TextSecondary,
                )
            }
            Text(
                text = if (selected) "FULLSCREEN ›" else "›",
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) OwnPlayColors.Accent else OwnPlayColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun FullscreenLive(
    channel: LiveChannel,
    channelNumber: String,
    playbackController: PlaybackController,
    controllerScope: CoroutineScope,
    playbackPhase: PlaybackPhase,
    resolutionError: String?,
    modifier: Modifier = Modifier,
) {
    var overlayVisible by remember(channel.channelId) { mutableStateOf(true) }
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(overlayVisible, channel.channelId) {
        if (overlayVisible) {
            delay(4_000)
            overlayVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        LivePlaybackSurface(
            target = VideoTarget.FULLSCREEN,
            playbackController = playbackController,
            controllerScope = controllerScope,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) {
                    overlayVisible = !overlayVisible
                },
        )

        if (overlayVisible) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = OwnPlaySpacing.Xl, vertical = OwnPlaySpacing.Lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
            ) {
                OwnPlayWordmark(showTagline = false)
                LiveBadge()
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(OwnPlaySpacing.Xl),
                color = OwnPlayColors.Surface.copy(alpha = 0.94f),
                shape = OwnPlayShapeTokens.Medium,
                border = BorderStroke(1.dp, OwnPlayColors.Divider),
                tonalElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier.padding(OwnPlaySpacing.Lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Lg),
                ) {
                    Text(
                        text = channelNumber,
                        style = MaterialTheme.typography.titleMedium,
                        color = OwnPlayColors.TextSecondary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = channel.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = OwnPlayColors.TextPrimary,
                        )
                        Text(
                            text = when {
                                resolutionError != null -> resolutionError
                                playbackPhase == PlaybackPhase.ERROR -> "Playback unavailable"
                                playbackPhase == PlaybackPhase.BUFFERING -> "Buffering live stream…"
                                else -> "Live • Guide unavailable"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = OwnPlayColors.TextSecondary,
                        )
                    }
                    Text(
                        text = "BACK TO PREVIEW",
                        style = MaterialTheme.typography.labelLarge,
                        color = OwnPlayColors.Accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color(0xFFE53935),
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = "LIVE",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

@Composable
private fun LivePlaybackSurface(
    target: VideoTarget,
    playbackController: PlaybackController,
    controllerScope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val surfaceView = remember(context, target) {
        SurfaceView(context).apply {
            keepScreenOn = true
        }
    }

    AndroidView(
        factory = { surfaceView },
        modifier = modifier,
    )

    DisposableEffect(playbackController, target, surfaceView) {
        controllerScope.launch {
            playbackController.bindVideoTarget(target, surfaceView)
        }
        onDispose {
            controllerScope.launch {
                playbackController.unbindVideoTarget(target, surfaceView)
            }
        }
    }
}

private fun channelNumber(
    channels: List<LiveChannel>,
    channel: LiveChannel,
): String {
    val index = channels.indexOfFirst { it.channelId == channel.channelId }
    return (if (index >= 0) index + 1 else 1).toString().padStart(3, '0')
}
