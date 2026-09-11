package app.ownplay.mobile.feature.live.ui

import android.graphics.BitmapFactory
import android.view.OrientationEventListener
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import app.ownplay.mobile.feature.live.domain.LiveNowNext
import app.ownplay.mobile.feature.live.domain.LiveProgram
import app.ownplay.mobile.feature.live.domain.LiveIntent
import app.ownplay.mobile.feature.live.domain.LivePlaybackResolution
import app.ownplay.mobile.feature.live.domain.LivePresentation
import app.ownplay.mobile.feature.live.domain.LivePresentationReducer
import app.ownplay.mobile.feature.live.domain.LivePresentationState
import app.ownplay.mobile.feature.live.domain.LiveRepository
import app.ownplay.mobile.playback.PlaybackController
import app.ownplay.mobile.playback.domain.AudioFormatLabelPolicy
import app.ownplay.mobile.playback.domain.PlaybackAudioTrack
import app.ownplay.mobile.playback.domain.PlaybackKind
import app.ownplay.mobile.playback.domain.PlaybackLoadRequest
import app.ownplay.mobile.playback.domain.PlaybackMedia
import app.ownplay.mobile.playback.domain.PlaybackPhase
import app.ownplay.mobile.playback.domain.PlaybackSnapshot
import app.ownplay.mobile.playback.domain.VideoTarget
import app.ownplay.mobile.playback.ui.AudioTrackSelectorPanel
import app.ownplay.mobile.playback.ui.PlayerLocalControlHudOverlay
import app.ownplay.mobile.playback.ui.playerLocalVerticalControls
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LiveShell(
    liveRepository: LiveRepository,
    playbackController: PlaybackController,
    showChannelLogos: Boolean,
    onFullscreenChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val catalogFlow = remember(liveRepository) { liveRepository.observeCatalog() }
    val catalog by catalogFlow.collectAsState(initial = null)
    val playback by playbackController.state.collectAsState()
    val scope = rememberCoroutineScope()
    val browseListState = rememberLazyListState()

    var presentationState by remember { mutableStateOf(LivePresentationState()) }
    var resolutionError by remember { mutableStateOf<String?>(null) }
    var fallbackLoadRequest by remember { mutableStateOf<PlaybackLoadRequest?>(null) }
    var waitingForInitialChannels by remember(catalog?.activeSourceId) { mutableStateOf(false) }

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
    val rawCategories = catalog?.categories.orEmpty()
    val categories = LiveBrowsePolicy.visibleCategories(rawCategories)
    var selectedCategoryKey by remember(catalog?.activeSourceId) { mutableStateOf<String?>(null) }
    val activeCategoryKey = LiveBrowsePolicy.activeCategoryKey(categories, selectedCategoryKey)
    val visibleChannels = activeCategoryKey?.let { key ->
        channels.filter { channel -> channel.categoryKey == key }
    } ?: channels
    val selectedChannel = channels.firstOrNull { it.channelId == presentationState.selectedChannelId }
    val selectedGuide = rememberLiveGuide(liveRepository, selectedChannel?.channelId)
    val audioCompatibilityMessage = when {
        fallbackLoadRequest != null -> null
        playback.phase == PlaybackPhase.READY &&
            playback.audioTrackPresent == true &&
            playback.audioTrackSupported == false ->
            "Unsupported audio: ${AudioFormatLabelPolicy.describe(playback.audioMimeType, playback.audioCodecs)}."
        playback.phase == PlaybackPhase.READY &&
            playback.audioTrackPresent == true &&
            playback.audioTrackSelected == false ->
            "Audio track could not be selected: ${AudioFormatLabelPolicy.describe(playback.audioMimeType, playback.audioCodecs)}."
        else -> null
    }

    LaunchedEffect(categories, selectedCategoryKey) {
        val resolvedCategoryKey = LiveBrowsePolicy.activeCategoryKey(categories, selectedCategoryKey)
        if (selectedCategoryKey != resolvedCategoryKey) {
            selectedCategoryKey = resolvedCategoryKey
        }
    }

    LaunchedEffect(catalog?.activeSourceId, catalog?.channels?.size) {
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

    LaunchedEffect(
        playback.phase,
        playback.audioTrackPresent,
        playback.audioTrackSupported,
        playback.audioTrackSelected,
        fallbackLoadRequest,
    ) {
        if (LivePlaybackFallbackPolicy.shouldUseFallback(playback)) {
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

    val orientationContext = LocalContext.current
    DisposableEffect(
        orientationContext,
        presentationState.presentation,
        presentationState.selectedChannelId,
    ) {
        val selectedId = presentationState.selectedChannelId
        if (presentationState.presentation != LivePresentation.PREVIEW || selectedId == null) {
            onDispose { }
        } else {
            var landscapeTriggered = false
            val listener = object : OrientationEventListener(orientationContext) {
                override fun onOrientationChanged(orientation: Int) {
                    if (orientation == ORIENTATION_UNKNOWN) return
                    when {
                        LiveOrientationPolicy.isLandscape(orientation) && !landscapeTriggered -> {
                            landscapeTriggered = true
                            dispatch(LiveIntent.ChannelTapped(selectedId))
                        }
                        LiveOrientationPolicy.isPortrait(orientation) -> landscapeTriggered = false
                    }
                }
            }
            if (listener.canDetectOrientation()) listener.enable()
            onDispose { listener.disable() }
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
            playbackSnapshot = playback,
            resolutionError = resolutionError,
            guide = selectedGuide,
            audioCompatibilityMessage = audioCompatibilityMessage,
            audioTracks = playback.audioTracks,
            onBackToPreview = { dispatch(LiveIntent.BackPressed) },
            modifier = modifier,
        )
    } else {
        LiveBrowseAndPreview(
            catalog = catalog,
            waitingForInitialChannels = waitingForInitialChannels,
            channels = visibleChannels,
            categories = categories,
            liveRepository = liveRepository,
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
            selectedGuide = selectedGuide,
            audioCompatibilityMessage = audioCompatibilityMessage,
            showChannelLogos = showChannelLogos,
            listState = browseListState,
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
    waitingForInitialChannels: Boolean,
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
    LaunchedEffect(selectedChannel?.channelId, selectedCategoryKey, channels, categories) {
        val selectedId = selectedChannel?.channelId ?: return@LaunchedEffect
        val selectedIndex = channels.indexOfFirst { it.channelId == selectedId }
        if (selectedIndex < 0) return@LaunchedEffect

        val fixedItemsBeforeChannels = 2 + if (categories.isNotEmpty()) 1 else 0
        val previewItemIndex = fixedItemsBeforeChannels + selectedIndex
        repeat(4) {
            if (listState.layoutInfo.totalItemsCount > previewItemIndex) {
                listState.animateScrollToItem(previewItemIndex)
                return@LaunchedEffect
            }
            delay(16)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
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

            catalog.channels.isEmpty() && waitingForInitialChannels -> item {
                Box(modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm)) {
                    OwnPlayStatePanel(
                        title = "Loading Live channels…",
                        message = "Connecting to ${catalog.activeSourceName ?: "the active source"}. Large provider catalogs can take a moment to appear.",
                    )
                }
            }

            catalog.channels.isEmpty() -> item {
                Box(modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm)) {
                    OwnPlayStatePanel(
                        title = "Live channels are not ready",
                        message = "Channels will appear automatically if the source is still refreshing. Otherwise refresh ${catalog.activeSourceName ?: "the active source"} in Settings > Sources.",
                    )
                }
            }

            channels.isEmpty() -> item {
                Box(modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm)) {
                    OwnPlayStatePanel(
                        title = "No channels in this category",
                        message = "Choose another provider category.",
                    )
                }
            }

            else -> channels.forEachIndexed { index, channel ->
                val selectedPreviewChannel = selectedChannel?.takeIf { it.channelId == channel.channelId }
                val selected = selectedPreviewChannel != null
                if (selectedPreviewChannel != null) {
                    item(key = "live-preview-${channel.channelId}") {
                        LivePreviewBlock(
                            selectedChannel = selectedPreviewChannel,
                            playbackController = playbackController,
                            controllerScope = controllerScope,
                            playbackPhase = playbackPhase,
                            resolutionError = resolutionError,
                            guide = selectedGuide,
                            audioCompatibilityMessage = audioCompatibilityMessage,
                        )
                    }
                }

                item(key = channel.channelId) {
                    val guide = if (selected) selectedGuide else rememberLiveGuide(liveRepository, channel.channelId)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Xs),
                    ) {
                        ChannelRow(
                            number = (index + 1).toString().padStart(3, '0'),
                            channel = channel,
                            selected = selected,
                            guide = guide,
                            showChannelLogos = showChannelLogos,
                            onClick = { onChannelTapped(channel) },
                        )
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
private fun LivePreviewBlock(
    selectedChannel: LiveChannel,
    playbackController: PlaybackController,
    controllerScope: CoroutineScope,
    playbackPhase: PlaybackPhase,
    resolutionError: String?,
    guide: LiveNowNext,
    audioCompatibilityMessage: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
    ) {
        PreviewSurface(
            selectedChannel = selectedChannel,
            playbackController = playbackController,
            controllerScope = controllerScope,
            playbackPhase = playbackPhase,
            resolutionError = resolutionError,
            audioCompatibilityMessage = audioCompatibilityMessage,
        )
        NowPlayingPanel(selectedChannel = selectedChannel, guide = guide)
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
    audioCompatibilityMessage: String?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .playerLocalVerticalControls(playbackController, controllerScope)
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

            PlayerLocalControlHudOverlay(
                modifier = Modifier.align(Alignment.Center),
            )

            LiveBadge(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(OwnPlaySpacing.Md),
            )

            val statusMessage = when {
                resolutionError != null -> resolutionError
                playbackPhase == PlaybackPhase.ERROR -> "Playback unavailable"
                audioCompatibilityMessage != null -> audioCompatibilityMessage
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
private fun NowPlayingPanel(selectedChannel: LiveChannel, guide: LiveNowNext) {
    OwnPlayPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = OwnPlaySpacing.Md, vertical = OwnPlaySpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
            ) {
                Text(
                    text = "NOW",
                    style = MaterialTheme.typography.labelSmall,
                    color = OwnPlayColors.Accent,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = guide.now?.let(::programTimeRange)?.takeIf { it.isNotBlank() } ?: "LIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = OwnPlayColors.TextSecondary,
                )
                Text(
                    text = selectedChannel.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = OwnPlayColors.TextSecondary,
                    maxLines = 1,
                )
            }
            Text(
                text = guide.now?.title ?: "Guide unavailable",
                style = MaterialTheme.typography.titleMedium,
                color = OwnPlayColors.TextPrimary,
                maxLines = 1,
            )
            guide.next?.let { next ->
                Text(
                    text = "NEXT ${programTimeRange(next)} • ${next.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = OwnPlayColors.TextSecondary,
                    maxLines = 1,
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
    guide: LiveNowNext,
    showChannelLogos: Boolean,
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
            if (showChannelLogos) {
                ChannelLogoIdentity(channel = channel, selected = selected)
                Spacer(modifier = Modifier.width(OwnPlaySpacing.Md))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = OwnPlayColors.TextPrimary,
                )
                Text(
                    text = guide.now?.let { program -> "Now ${programTimeRange(program)} • ${program.title}" }
                        ?: if (selected) "Previewing now" else "Live channel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OwnPlayColors.TextSecondary,
                    maxLines = 1,
                )
                guide.next?.let { next ->
                    Text(
                        text = "Next ${programTimeRange(next)} • ${next.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OwnPlayColors.TextSecondary,
                        maxLines = 1,
                    )
                }
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
private fun ChannelLogoIdentity(channel: LiveChannel, selected: Boolean) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = channel.logoUrl) {
        value = channel.logoUrl?.takeIf { it.isNotBlank() }?.let { loadChannelLogo(it) }
    }
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(OwnPlayShapeTokens.Small)
            .background(if (selected) OwnPlayColors.AccentStrong else OwnPlayColors.SurfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = "${channel.name} logo",
                modifier = Modifier.fillMaxSize().padding(4.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                text = channel.name.firstOrNull()?.uppercaseChar()?.toString() ?: "•",
                style = MaterialTheme.typography.titleMedium,
                color = OwnPlayColors.TextPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private suspend fun loadChannelLogo(locator: String) = withContext(Dispatchers.IO) {
    runCatching {
        val connection = URL(locator).openConnection() as? HttpURLConnection ?: return@runCatching null
        connection.connectTimeout = 4_000
        connection.readTimeout = 5_000
        connection.instanceFollowRedirects = true
        try {
            if (connection.responseCode !in 200..299) return@runCatching null
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8_192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count <= 0) break
                    total += count
                    if (total > MAX_CHANNEL_LOGO_BYTES) return@runCatching null
                    output.write(buffer, 0, count)
                }
            }
            val bytes = output.toByteArray()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

private const val MAX_CHANNEL_LOGO_BYTES = 2 * 1024 * 1024

@Composable
private fun FullscreenLive(
    channel: LiveChannel,
    channelNumber: String,
    playbackController: PlaybackController,
    controllerScope: CoroutineScope,
    playbackSnapshot: PlaybackSnapshot,
    resolutionError: String?,
    guide: LiveNowNext,
    audioCompatibilityMessage: String?,
    audioTracks: List<PlaybackAudioTrack>,
    onBackToPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var overlayVisible by remember(channel.channelId) { mutableStateOf(true) }
    var audioSelectorVisible by remember(channel.channelId) { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(
        overlayVisible,
        channel.channelId,
        playbackSnapshot.phase,
        playbackSnapshot.isPlaying,
    ) {
        if (overlayVisible && LivePlayerControlsPolicy.shouldAutoHide(playbackSnapshot)) {
            delay(4_000)
            if (LivePlayerControlsPolicy.shouldAutoHide(playbackController.currentSnapshot())) {
                overlayVisible = false
            }
        }
    }

    LaunchedEffect(overlayVisible) {
        if (!overlayVisible) audioSelectorVisible = false
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
                .playerLocalVerticalControls(playbackController, controllerScope)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) {
                    overlayVisible = !overlayVisible
                },
        )

        PlayerLocalControlHudOverlay(
            modifier = Modifier.align(Alignment.Center),
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
                                playbackSnapshot.phase == PlaybackPhase.ERROR -> "Playback unavailable"
                                audioCompatibilityMessage != null -> audioCompatibilityMessage
                                playbackSnapshot.phase == PlaybackPhase.BUFFERING -> "Buffering live stream…"
                                guide.now != null -> "Now ${programTimeRange(guide.now)} • ${guide.now.title}"
                                else -> "Live • Guide unavailable"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = OwnPlayColors.TextSecondary,
                            maxLines = 1,
                        )
                        guide.next?.let { next ->
                            Text(
                                text = "Next ${programTimeRange(next)} • ${next.title}",
                                style = MaterialTheme.typography.bodySmall,
                                color = OwnPlayColors.TextSecondary,
                                maxLines = 1,
                            )
                        }
                    }
                    if (audioTracks.isNotEmpty()) {
                        Text(
                            text = "AUDIO",
                            modifier = Modifier
                                .clickable { audioSelectorVisible = !audioSelectorVisible }
                                .padding(OwnPlaySpacing.Sm),
                            style = MaterialTheme.typography.labelLarge,
                            color = OwnPlayColors.Accent,
                        )
                    }
                    Text(
                        text = "BACK TO PREVIEW",
                        modifier = Modifier
                            .clickable(onClick = onBackToPreview)
                            .padding(OwnPlaySpacing.Sm),
                        style = MaterialTheme.typography.labelLarge,
                        color = OwnPlayColors.Accent,
                    )
                }
            }

            if (audioSelectorVisible && audioTracks.isNotEmpty()) {
                AudioTrackSelectorPanel(
                    tracks = audioTracks,
                    onSelect = { selectionId ->
                        controllerScope.launch { playbackController.selectAudioTrack(selectionId) }
                        audioSelectorVisible = false
                    },
                    onDismiss = { audioSelectorVisible = false },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(OwnPlaySpacing.Xl),
                )
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

@Composable
private fun rememberLiveGuide(
    liveRepository: LiveRepository,
    channelId: String?,
): LiveNowNext {
    var guide by remember(liveRepository, channelId) { mutableStateOf(LiveNowNext()) }
    LaunchedEffect(liveRepository, channelId) {
        guide = channelId?.let { liveRepository.loadNowNext(it) } ?: LiveNowNext()
    }
    return guide
}

private fun programTimeRange(program: LiveProgram): String {
    val start = program.startEpochSeconds?.let(::formatEpgTime)
    val end = program.endEpochSeconds?.let(::formatEpgTime)
    return when {
        start != null && end != null -> "$start–$end"
        start != null -> start
        end != null -> "until $end"
        else -> ""
    }
}

private fun formatEpgTime(epochSeconds: Long): String = Instant
    .ofEpochSecond(epochSeconds)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("HH:mm"))

private fun channelNumber(
    channels: List<LiveChannel>,
    channel: LiveChannel,
): String {
    val index = channels.indexOfFirst { it.channelId == channel.channelId }
    return (if (index >= 0) index + 1 else 1).toString().padStart(3, '0')
}
