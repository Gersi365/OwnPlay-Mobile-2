package app.ownplay.mobile.feature.live.ui

import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayRemoteImageLoader
import app.ownplay.mobile.design.RemoteImageProfile
import app.ownplay.mobile.design.OwnPlayFilterChip
import app.ownplay.mobile.design.OwnPlaySearchField
import app.ownplay.mobile.design.OwnPlaySectionHeader
import app.ownplay.mobile.design.OwnPlayShapeTokens
import app.ownplay.mobile.design.OwnPlaySpacing
import app.ownplay.mobile.design.OwnPlayStatePanel
import app.ownplay.mobile.design.OwnPlayTopBar
import app.ownplay.mobile.feature.live.domain.ChannelNameDisplayPolicy
import app.ownplay.mobile.feature.live.domain.LiveCatalog
import app.ownplay.mobile.feature.live.domain.LiveCategory
import app.ownplay.mobile.feature.live.domain.LiveChannel
import app.ownplay.mobile.feature.live.domain.LiveCustomGroup
import app.ownplay.mobile.feature.live.domain.LiveEffect
import app.ownplay.mobile.feature.live.domain.LiveEpgProgressPolicy
import app.ownplay.mobile.feature.live.domain.LiveIntent
import app.ownplay.mobile.feature.live.domain.LiveNowNext
import app.ownplay.mobile.feature.live.domain.LivePlaybackResolution
import app.ownplay.mobile.feature.live.domain.LivePresentation
import app.ownplay.mobile.feature.live.domain.LivePresentationReducer
import app.ownplay.mobile.feature.live.domain.LivePresentationState
import app.ownplay.mobile.feature.live.domain.LiveProgram
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
import app.ownplay.mobile.playback.ui.PlaybackOptionsPanel
import app.ownplay.mobile.playback.ui.PlaybackSubtitleOverlay
import app.ownplay.mobile.playback.ui.PlayerGlassGlyph
import app.ownplay.mobile.playback.ui.PlayerGlassIconAction
import app.ownplay.mobile.playback.ui.PlayerGlassPillAction
import app.ownplay.mobile.playback.ui.PlayerGlassScrims
import app.ownplay.mobile.playback.ui.PlayerLocalControlHudOverlay
import app.ownplay.mobile.playback.ui.playerLocalVerticalControls
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LiveShell(
    liveRepository: LiveRepository,
    playbackController: PlaybackController,
    showChannelLogos: Boolean,
    hideChannelPrefix: Boolean,
    autoFullscreenRequestToken: Int = 0,
    autoPreviewRequestToken: Int = 0,
    onFullscreenChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val catalogFlow = remember(liveRepository) { liveRepository.observeCatalog() }
    val catalog by catalogFlow.collectAsState(initial = null)
    val playback by playbackController.state.collectAsState()
    val scope = rememberCoroutineScope()
    val browseListState = rememberLazyListState()
    val channelLoadGeneration = remember { AtomicLong(0L) }

    var presentationState by remember { mutableStateOf(LivePresentationState()) }
    var resolutionError by remember { mutableStateOf<String?>(null) }
    var fallbackLoadRequest by remember { mutableStateOf<PlaybackLoadRequest?>(null) }
    var waitingForInitialChannels by remember(catalog?.activeSourceId) { mutableStateOf(false) }
    var searchVisible by remember(catalog?.activeSourceId) { mutableStateOf(false) }
    var searchQuery by remember(catalog?.activeSourceId) { mutableStateOf("") }
    var favoritesOnly by remember(catalog?.activeSourceId) { mutableStateOf(false) }

    fun displayChannelName(channel: LiveChannel): String =
        ChannelNameDisplayPolicy.displayName(channel.name, hideChannelPrefix)

    fun applyEffect(effect: LiveEffect) {
        when (effect) {
            is LiveEffect.LoadChannel -> {
                val generation = channelLoadGeneration.incrementAndGet()
                scope.launch {
                    resolutionError = null
                    fallbackLoadRequest = null
                    when (val resolved = liveRepository.resolvePlayback(effect.channelId)) {
                        is LivePlaybackResolution.Success -> {
                            if (
                                channelLoadGeneration.get() != generation ||
                                presentationState.selectedChannelId != effect.channelId
                            ) {
                                return@launch
                            }
                            val displayTitle = displayChannelName(resolved.value.channel)
                            fallbackLoadRequest = resolved.value.fallbackUri?.let { fallbackUri ->
                                PlaybackLoadRequest(
                                    media = PlaybackMedia(
                                        id = resolved.value.channel.channelId,
                                        uri = fallbackUri,
                                        title = displayTitle,
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
                                        title = displayTitle,
                                        kind = PlaybackKind.LIVE,
                                        streamFormat = resolved.value.streamFormat,
                                    ),
                                ),
                            )
                        }

                        is LivePlaybackResolution.Failure -> {
                            if (
                                channelLoadGeneration.get() != generation ||
                                presentationState.selectedChannelId != effect.channelId
                            ) {
                                return@launch
                            }
                            fallbackLoadRequest = null
                            playbackController.stop(clearMedia = true)
                            resolutionError = resolved.safeMessage
                        }
                    }
                }
            }

            LiveEffect.StopPlayback -> {
                channelLoadGeneration.incrementAndGet()
                scope.launch {
                    resolutionError = null
                    fallbackLoadRequest = null
                    playbackController.stop(clearMedia = true)
                }
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
    val customGroups = catalog?.customGroups.orEmpty()
    val rawCategories = catalog?.categories.orEmpty()
    val categories = remember(rawCategories) { LiveBrowsePolicy.visibleCategories(rawCategories) }
    var selectedCategoryKey by remember(catalog?.activeSourceId) { mutableStateOf<String?>(null) }
    var selectedCustomGroupId by remember(catalog?.activeSourceId) { mutableStateOf<String?>(null) }
    val activeCategoryKey = LiveBrowsePolicy.activeCategoryKey(categories, selectedCategoryKey)
    val selectedCustomGroup = remember(customGroups, selectedCustomGroupId) {
        customGroups.firstOrNull { group -> group.groupId == selectedCustomGroupId }
    }
    val normalizedSearchQuery = searchQuery.trim()
    val searchActive = normalizedSearchQuery.isNotEmpty()
    val favoriteChannels = remember(channels) { LiveBrowsePolicy.favoriteChannels(channels) }
    val customGroupChannels = remember(channels, selectedCustomGroup) {
        LiveBrowsePolicy.customGroupChannels(channels, selectedCustomGroup?.channelIds.orEmpty())
    }
    val visibleChannels = remember(
        channels,
        favoriteChannels,
        customGroupChannels,
        activeCategoryKey,
        favoritesOnly,
        selectedCustomGroup,
        searchActive,
        normalizedSearchQuery,
        hideChannelPrefix,
    ) {
        when {
            searchActive -> channels.filter { channel ->
                channel.name.contains(normalizedSearchQuery, ignoreCase = true) ||
                    ChannelNameDisplayPolicy.displayName(channel.name, hideChannelPrefix)
                        .contains(normalizedSearchQuery, ignoreCase = true)
            }
            selectedCustomGroup != null -> customGroupChannels
            favoritesOnly -> favoriteChannels
            else -> activeCategoryKey?.let { key ->
                channels.filter { channel -> channel.categoryKey == key }
            } ?: channels
        }
    }
    val showCategories = !searchActive && (
        categories.isNotEmpty() || favoriteChannels.isNotEmpty() || customGroups.isNotEmpty()
    )
    val selectedChannel = remember(channels, presentationState.selectedChannelId) {
        channels.firstOrNull { it.channelId == presentationState.selectedChannelId }
    }
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

    fun selectProviderCategory(categoryKey: String?) {
        favoritesOnly = false
        selectedCustomGroupId = null
        selectedCategoryKey = categoryKey
        if (
            presentationState.presentation == LivePresentation.PREVIEW &&
            selectedChannel?.categoryKey != categoryKey
        ) {
            dispatch(LiveIntent.BackPressed)
        }
    }

    fun stepProviderCategory(direction: LiveNavigationDirection) {
        if (searchActive || favoritesOnly || selectedCustomGroup != null) return
        val nextCategoryKey = LiveGestureNavigationPolicy.adjacentKey(
            keys = categories.map { it.categoryKey },
            currentKey = activeCategoryKey,
            direction = direction,
        ) ?: return
        selectProviderCategory(nextCategoryKey)
    }

    fun stepFullscreenChannel(direction: LiveNavigationDirection) {
        val currentChannelId = presentationState.selectedChannelId ?: return
        val nextChannelId = LiveGestureNavigationPolicy.adjacentKey(
            keys = visibleChannels.map { it.channelId },
            currentKey = currentChannelId,
            direction = direction,
        ) ?: return
        dispatch(LiveIntent.ChannelSwitched(nextChannelId))
    }

    LaunchedEffect(autoFullscreenRequestToken) {
        if (
            autoFullscreenRequestToken > 0 &&
            presentationState.presentation == LivePresentation.PREVIEW
        ) {
            val channelId = selectedChannel?.channelId ?: return@LaunchedEffect
            dispatch(LiveIntent.ChannelTapped(channelId))
        }
    }

    LaunchedEffect(autoPreviewRequestToken) {
        if (
            autoPreviewRequestToken > 0 &&
            presentationState.presentation == LivePresentation.FULLSCREEN
        ) {
            dispatch(LiveIntent.BackPressed)
        }
    }

    LaunchedEffect(favoriteChannels.isEmpty(), favoritesOnly) {
        if (favoritesOnly && favoriteChannels.isEmpty()) favoritesOnly = false
    }

    LaunchedEffect(customGroups, selectedCustomGroupId) {
        if (selectedCustomGroupId != null && selectedCustomGroup == null) {
            selectedCustomGroupId = null
        }
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
        playback.mediaId,
        playback.phase,
        playback.audioTrackPresent,
        playback.audioTrackSupported,
        playback.audioTrackSelected,
        fallbackLoadRequest,
    ) {
        val fallback = fallbackLoadRequest ?: return@LaunchedEffect
        if (playback.mediaId != fallback.media.id) return@LaunchedEffect
        if (LivePlaybackFallbackPolicy.shouldUseFallback(playback)) {
            fallbackLoadRequest = null
            playbackController.load(fallback)
            return@LaunchedEffect
        }
        if (LivePlaybackFallbackPolicy.shouldUseFallbackAfterBuffering(playback)) {
            delay(LivePlaybackFallbackPolicy.PRIMARY_BUFFERING_TIMEOUT_MS)
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

    LaunchedEffect(
        visibleChannels,
        presentationState.presentation,
        presentationState.selectedChannelId,
    ) {
        val selectedId = presentationState.selectedChannelId ?: return@LaunchedEffect
        if (
            presentationState.presentation == LivePresentation.PREVIEW &&
            visibleChannels.none { it.channelId == selectedId }
        ) {
            dispatch(LiveIntent.BackPressed)
        }
    }

    BackHandler(enabled = presentationState.presentation != LivePresentation.BROWSE) {
        dispatch(LiveIntent.BackPressed)
    }

    if (presentationState.presentation == LivePresentation.FULLSCREEN && selectedChannel != null) {
        val channelNumber = remember(channels, selectedChannel.channelId) {
            channelNumber(channels, selectedChannel)
        }
        FullscreenLive(
            channel = selectedChannel,
            displayName = displayChannelName(selectedChannel),
            channelNumber = channelNumber,
            playbackController = playbackController,
            controllerScope = scope,
            playbackSnapshot = playback,
            resolutionError = resolutionError,
            guide = selectedGuide,
            audioCompatibilityMessage = audioCompatibilityMessage,
            audioTracks = playback.audioTracks,
            onBackToPreview = { dispatch(LiveIntent.BackPressed) },
            onPreviousChannel = { stepFullscreenChannel(LiveNavigationDirection.PREVIOUS) },
            onNextChannel = { stepFullscreenChannel(LiveNavigationDirection.NEXT) },
            modifier = modifier,
        )
    } else {
        LiveBrowseAndPreview(
            catalog = catalog,
            waitingForInitialChannels = waitingForInitialChannels,
            channels = visibleChannels,
            categories = categories,
            favoriteCount = favoriteChannels.size,
            favoritesOnly = favoritesOnly,
            customGroups = customGroups,
            selectedCustomGroupId = selectedCustomGroupId,
            liveRepository = liveRepository,
            selectedCategoryKey = activeCategoryKey,
            searchVisible = searchVisible,
            searchQuery = searchQuery,
            showCategories = showCategories,
            categorySwipeEnabled = !searchActive && !favoritesOnly && selectedCustomGroup == null && categories.size > 1,
            onPreviousCategoryGesture = { stepProviderCategory(LiveNavigationDirection.PREVIOUS) },
            onNextCategoryGesture = { stepProviderCategory(LiveNavigationDirection.NEXT) },
            onSearchToggle = {
                searchVisible = !searchVisible
                if (!searchVisible) searchQuery = ""
            },
            onSearchQueryChange = { searchQuery = it },
            onFavoriteFilterSelected = {
                selectedCustomGroupId = null
                favoritesOnly = true
                if (
                    presentationState.presentation == LivePresentation.PREVIEW &&
                    selectedChannel?.favorite != true
                ) {
                    dispatch(LiveIntent.BackPressed)
                }
            },
            onCustomGroupSelected = { groupId ->
                favoritesOnly = false
                selectedCustomGroupId = groupId
                val group = customGroups.firstOrNull { it.groupId == groupId }
                if (
                    presentationState.presentation == LivePresentation.PREVIEW &&
                    selectedChannel?.channelId !in group?.channelIds.orEmpty()
                ) {
                    dispatch(LiveIntent.BackPressed)
                }
            },
            onCategorySelected = ::selectProviderCategory,
            selectedChannel = selectedChannel,
            playbackController = playbackController,
            controllerScope = scope,
            playbackPhase = playback.phase,
            resolutionError = resolutionError,
            selectedGuide = selectedGuide,
            audioCompatibilityMessage = audioCompatibilityMessage,
            showChannelLogos = showChannelLogos,
            hideChannelPrefix = hideChannelPrefix,
            listState = browseListState,
            onChannelTapped = { channel -> dispatch(LiveIntent.ChannelTapped(channel.channelId)) },
            onChannelFavoriteToggle = { channel ->
                scope.launch { liveRepository.setChannelFavorite(channel.channelId, !channel.favorite) }
                if (
                    favoritesOnly &&
                    channel.favorite &&
                    presentationState.presentation == LivePresentation.PREVIEW &&
                    selectedChannel?.channelId == channel.channelId
                ) {
                    dispatch(LiveIntent.BackPressed)
                }
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
    favoriteCount: Int,
    favoritesOnly: Boolean,
    customGroups: List<LiveCustomGroup>,
    selectedCustomGroupId: String?,
    liveRepository: LiveRepository,
    selectedCategoryKey: String?,
    searchVisible: Boolean,
    searchQuery: String,
    showCategories: Boolean,
    categorySwipeEnabled: Boolean,
    onPreviousCategoryGesture: () -> Unit,
    onNextCategoryGesture: () -> Unit,
    onSearchToggle: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFavoriteFilterSelected: () -> Unit,
    onCustomGroupSelected: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    selectedChannel: LiveChannel?,
    playbackController: PlaybackController,
    controllerScope: CoroutineScope,
    playbackPhase: PlaybackPhase,
    resolutionError: String?,
    selectedGuide: LiveNowNext,
    audioCompatibilityMessage: String?,
    showChannelLogos: Boolean,
    hideChannelPrefix: Boolean,
    listState: LazyListState,
    onChannelTapped: (LiveChannel) -> Unit,
    onChannelFavoriteToggle: (LiveChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nowEpochSeconds = rememberEpgClock()

    LaunchedEffect(
        selectedChannel?.channelId,
        selectedCategoryKey,
        selectedCustomGroupId,
        channels,
        categories,
    ) {
        val selectedId = selectedChannel?.channelId ?: return@LaunchedEffect
        val selectedIndex = channels.indexOfFirst { it.channelId == selectedId }
        if (selectedIndex < 0) return@LaunchedEffect

        val fixedItemsBeforeChannels = 2 + if (showCategories) 1 else 0
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
            OwnPlayTopBar(
                showTagline = false,
                onSearchClick = onSearchToggle,
            )
        }

        item {
            Column(
                modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs),
            ) {
                OwnPlaySectionHeader(
                    title = "Live",
                    actionLabel = if (searchQuery.isNotBlank()) {
                        "${channels.size} matches"
                    } else {
                        catalog?.channels?.size?.takeIf { it > 0 }?.let { "$it channels" }
                    },
                )
                if (searchVisible) {
                    OwnPlaySearchField(
                        query = searchQuery,
                        onQueryChange = onSearchQueryChange,
                        onClose = {
                            onSearchQueryChange("")
                            onSearchToggle()
                        },
                        placeholder = "Search channels",
                    )
                }
            }
        }

        if (showCategories) {
            item {
                LiveCategoryStrip(
                    categories = categories,
                    favoriteCount = favoriteCount,
                    favoritesOnly = favoritesOnly,
                    customGroups = customGroups,
                    selectedCustomGroupId = selectedCustomGroupId,
                    selectedCategoryKey = selectedCategoryKey,
                    onFavoritesSelected = onFavoriteFilterSelected,
                    onCustomGroupSelected = onCustomGroupSelected,
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillParentMaxHeight(0.65f)
                        .liveHorizontalNavigationGestures(
                            enabled = categorySwipeEnabled,
                            onPrevious = onPreviousCategoryGesture,
                            onNext = onNextCategoryGesture,
                        )
                        .padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm),
                ) {
                    OwnPlayStatePanel(
                        title = if (searchQuery.isNotBlank()) "No channel matches" else "No channels in this category",
                        message = if (searchQuery.isNotBlank()) {
                            "Try another channel name or close search to browse categories."
                        } else if (categorySwipeEnabled) {
                            "Swipe left or right to browse another provider category."
                        } else {
                            "Choose another provider category."
                        },
                    )
                }
            }

            else -> channels.forEach { channel ->
                val selectedPreviewChannel = selectedChannel?.takeIf { it.channelId == channel.channelId }
                val selected = selectedPreviewChannel != null
                if (selectedPreviewChannel != null) {
                    item(key = "live-preview-${channel.channelId}") {
                        LivePreviewBlock(
                            selectedChannel = selectedPreviewChannel,
                            displayName = ChannelNameDisplayPolicy.displayName(selectedPreviewChannel.name, hideChannelPrefix),
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
                    val displayName = ChannelNameDisplayPolicy.displayName(channel.name, hideChannelPrefix)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liveHorizontalNavigationGestures(
                                enabled = categorySwipeEnabled,
                                onPrevious = onPreviousCategoryGesture,
                                onNext = onNextCategoryGesture,
                            )
                            .padding(horizontal = OwnPlaySpacing.Lg, vertical = 3.dp),
                    ) {
                        ChannelRow(
                            channel = channel,
                            displayName = displayName,
                            selected = selected,
                            guide = guide,
                            nowEpochSeconds = nowEpochSeconds,
                            showChannelLogos = showChannelLogos,
                            onFavoriteToggle = { onChannelFavoriteToggle(channel) },
                            onClick = { onChannelTapped(channel) },
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(OwnPlaySpacing.Xl)) }
    }
}

@Composable
private fun LivePreviewBlock(
    selectedChannel: LiveChannel,
    displayName: String,
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
            displayName = displayName,
            playbackController = playbackController,
            controllerScope = controllerScope,
            playbackPhase = playbackPhase,
            resolutionError = resolutionError,
            audioCompatibilityMessage = audioCompatibilityMessage,
        )
        NowPlayingPanel(displayName = displayName, guide = guide)
    }
}

@Composable
private fun LiveCategoryStrip(
    categories: List<LiveCategory>,
    favoriteCount: Int,
    favoritesOnly: Boolean,
    customGroups: List<LiveCustomGroup>,
    selectedCustomGroupId: String?,
    selectedCategoryKey: String?,
    onFavoritesSelected: () -> Unit,
    onCustomGroupSelected: (String) -> Unit,
    onSelected: (String?) -> Unit,
) {
    val listState = rememberLazyListState()
    val selectedItemIndex = when {
        favoritesOnly && favoriteCount > 0 -> 0
        selectedCustomGroupId != null -> {
            val groupIndex = customGroups.indexOfFirst { it.groupId == selectedCustomGroupId }
            if (groupIndex >= 0) (if (favoriteCount > 0) 1 else 0) + groupIndex else -1
        }
        selectedCategoryKey != null -> {
            val categoryIndex = categories.indexOfFirst { it.categoryKey == selectedCategoryKey }
            if (categoryIndex >= 0) {
                (if (favoriteCount > 0) 1 else 0) + customGroups.size + categoryIndex
            } else {
                -1
            }
        }
        else -> -1
    }

    LaunchedEffect(selectedItemIndex) {
        if (selectedItemIndex < 0) return@LaunchedEffect
        repeat(4) {
            if (listState.layoutInfo.totalItemsCount > selectedItemIndex) {
                listState.animateScrollToItem(selectedItemIndex)
                return@LaunchedEffect
            }
            delay(16)
        }
    }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = OwnPlaySpacing.Lg),
        horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs),
    ) {
        if (favoriteCount > 0) {
            item(key = "ownplay-favorites") {
                OwnPlayFilterChip(
                    label = "Favorites",
                    selected = favoritesOnly && selectedCustomGroupId == null,
                    onClick = onFavoritesSelected,
                )
            }
        }
        items(customGroups, key = { group -> group.groupId }) { group ->
            OwnPlayFilterChip(
                label = group.name,
                selected = !favoritesOnly && selectedCustomGroupId == group.groupId,
                onClick = { onCustomGroupSelected(group.groupId) },
            )
        }
        items(categories, key = { it.categoryKey }) { category ->
            OwnPlayFilterChip(
                label = category.name,
                selected = !favoritesOnly && selectedCustomGroupId == null && selectedCategoryKey == category.categoryKey,
                onClick = { onSelected(category.categoryKey) },
            )
        }
    }
}

@Composable
private fun PreviewSurface(
    selectedChannel: LiveChannel?,
    displayName: String?,
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
            .clip(OwnPlayShapeTokens.Large)
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

            PlayerLocalControlHudOverlay(modifier = Modifier.align(Alignment.Center))

            LiveBadge(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(OwnPlaySpacing.Md),
            )

            val statusMessage = when {
                resolutionError != null -> resolutionError
                playbackPhase == PlaybackPhase.ERROR -> "Playback unavailable"
                audioCompatibilityMessage != null -> audioCompatibilityMessage
                playbackPhase == PlaybackPhase.BUFFERING -> "Loading ${displayName ?: selectedChannel.name}…"
                else -> null
            }
            if (statusMessage != null) {
                Surface(
                    color = OwnPlayColors.Background.copy(alpha = 0.84f),
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
private fun NowPlayingPanel(displayName: String, guide: LiveNowNext) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = OwnPlaySpacing.Xs),
        horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(54.dp)
                .background(OwnPlayColors.Accent, OwnPlayShapeTokens.Small),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
            ) {
                Text(
                    text = "NOW",
                    style = MaterialTheme.typography.labelMedium,
                    color = OwnPlayColors.Accent,
                    fontWeight = FontWeight.Bold,
                )
                guide.now?.let { current ->
                    programTimeRange(current).takeIf { it.isNotBlank() }?.let { range ->
                        Text(
                            text = range,
                            style = MaterialTheme.typography.labelMedium,
                            color = OwnPlayColors.TextMuted,
                        )
                    }
                }
                Text(
                    text = displayName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = OwnPlayColors.TextMuted,
                    maxLines = 1,
                )
            }
            guide.now?.let { current ->
                Text(
                    text = current.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = OwnPlayColors.TextPrimary,
                    maxLines = 1,
                )
            }
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
    channel: LiveChannel,
    displayName: String,
    selected: Boolean,
    guide: LiveNowNext,
    nowEpochSeconds: Long,
    showChannelLogos: Boolean,
    onFavoriteToggle: () -> Unit,
    onClick: () -> Unit,
) {
    val currentProgram = guide.now
    val currentProgress = LiveEpgProgressPolicy.fraction(currentProgram, nowEpochSeconds)

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = if (selected) OwnPlayColors.SurfaceSelected.copy(alpha = 0.78f) else Color.Transparent,
        shape = OwnPlayShapeTokens.Small,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = OwnPlaySpacing.Sm, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(42.dp)
                    .background(
                        color = if (selected) OwnPlayColors.Accent else Color.Transparent,
                        shape = OwnPlayShapeTokens.Small,
                    ),
            )
            Spacer(modifier = Modifier.width(OwnPlaySpacing.Sm))
            if (showChannelLogos) {
                ChannelLogoIdentity(channel = channel, displayName = displayName, selected = selected)
                Spacer(modifier = Modifier.width(OwnPlaySpacing.Md))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = OwnPlayColors.TextPrimary,
                    maxLines = 1,
                )
                currentProgram?.let { program ->
                    Text(
                        text = listOf(programTimeRange(program), program.title)
                            .filter(String::isNotBlank)
                            .joinToString(" • "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OwnPlayColors.TextSecondary,
                        maxLines = 1,
                    )
                    currentProgress?.let { progress ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(OwnPlayColors.Divider.copy(alpha = 0.72f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .height(2.dp)
                                    .background(OwnPlayColors.Accent),
                            )
                        }
                    }
                }
                if (selected) {
                    guide.next?.let { next ->
                        Text(
                            text = "Next ${programTimeRange(next)} • ${next.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = OwnPlayColors.TextMuted,
                            maxLines = 1,
                        )
                    }
                }
            }
            Text(
                text = if (channel.favorite) "★" else "☆",
                modifier = Modifier
                    .clickable(onClick = onFavoriteToggle)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleMedium,
                color = if (channel.favorite) OwnPlayColors.Accent else OwnPlayColors.TextMuted,
            )
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = if (selected) OwnPlayColors.Accent else OwnPlayColors.TextMuted,
            )
        }
    }
}

@Composable
private fun ChannelLogoIdentity(channel: LiveChannel, displayName: String, selected: Boolean) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = channel.logoUrl) {
        value = OwnPlayRemoteImageLoader.load(channel.logoUrl, RemoteImageProfile.LOGO)
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(OwnPlayShapeTokens.Small)
            .background(if (selected) OwnPlayColors.AccentStrong.copy(alpha = 0.72f) else OwnPlayColors.SurfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = "$displayName logo",
                modifier = Modifier.fillMaxSize().padding(4.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                text = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "•",
                style = MaterialTheme.typography.titleMedium,
                color = OwnPlayColors.TextPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun FullscreenLive(
    channel: LiveChannel,
    displayName: String,
    channelNumber: String,
    playbackController: PlaybackController,
    controllerScope: CoroutineScope,
    playbackSnapshot: PlaybackSnapshot,
    resolutionError: String?,
    guide: LiveNowNext,
    audioCompatibilityMessage: String?,
    audioTracks: List<PlaybackAudioTrack>,
    onBackToPreview: () -> Unit,
    onPreviousChannel: () -> Unit,
    onNextChannel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var overlayVisible by remember(channel.channelId) { mutableStateOf(true) }
    var optionsVisible by remember(channel.channelId) { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    BackHandler(enabled = optionsVisible) {
        optionsVisible = false
    }

    LaunchedEffect(
        overlayVisible,
        optionsVisible,
        channel.channelId,
        playbackSnapshot.phase,
        playbackSnapshot.isPlaying,
    ) {
        if (
            overlayVisible &&
            !optionsVisible &&
            LivePlayerControlsPolicy.shouldAutoHide(playbackSnapshot)
        ) {
            delay(4_000)
            if (
                !optionsVisible &&
                LivePlayerControlsPolicy.shouldAutoHide(playbackController.currentSnapshot())
            ) {
                overlayVisible = false
            }
        }
    }

    LaunchedEffect(overlayVisible) {
        if (!overlayVisible) optionsVisible = false
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
                .playerLocalVerticalControls(
                    playbackController = playbackController,
                    controllerScope = controllerScope,
                    enabled = !optionsVisible,
                )
                .liveHorizontalNavigationGestures(
                    enabled = !optionsVisible,
                    onPrevious = {
                        optionsVisible = false
                        overlayVisible = true
                        onPreviousChannel()
                    },
                    onNext = {
                        optionsVisible = false
                        overlayVisible = true
                        onNextChannel()
                    },
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = !optionsVisible,
                ) {
                    overlayVisible = !overlayVisible
                },
        )

        PlayerLocalControlHudOverlay(modifier = Modifier.align(Alignment.Center))

        PlaybackSubtitleOverlay(
            cues = playbackSnapshot.subtitleCues,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.86f)
                .padding(bottom = if (overlayVisible) 96.dp else 24.dp),
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
                    contentDescription = "Back to Live preview",
                    onClick = onBackToPreview,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                    )
                    Text(
                        text = "Channel $channelNumber",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.66f),
                    )
                }
                LiveBadge()
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.92f)
                    .padding(bottom = OwnPlaySpacing.Md),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    val statusMessage = when {
                        resolutionError != null -> resolutionError
                        playbackSnapshot.phase == PlaybackPhase.ERROR -> playbackSnapshot.errorMessage ?: "Playback unavailable"
                        audioCompatibilityMessage != null -> audioCompatibilityMessage
                        playbackSnapshot.phase == PlaybackPhase.BUFFERING -> "Buffering live stream…"
                        guide.now != null -> "Now ${programTimeRange(guide.now)} • ${guide.now.title}"
                        else -> null
                    }
                    statusMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                    guide.next?.let { next ->
                        Text(
                            text = "Next ${programTimeRange(next)} • ${next.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.66f),
                            maxLines = 1,
                        )
                    }
                }
                PlayerGlassIconAction(
                    glyph = PlayerGlassGlyph.PREVIOUS,
                    contentDescription = "Previous channel",
                    onClick = {
                        optionsVisible = false
                        overlayVisible = true
                        onPreviousChannel()
                    },
                )
                PlayerGlassIconAction(
                    glyph = PlayerGlassGlyph.NEXT,
                    contentDescription = "Next channel",
                    onClick = {
                        optionsVisible = false
                        overlayVisible = true
                        onNextChannel()
                    },
                )
                PlayerGlassPillAction(
                    text = "Options",
                    emphasized = optionsVisible,
                    onClick = { optionsVisible = !optionsVisible },
                )
            }

            if (optionsVisible) {
                PlaybackOptionsPanel(
                    audioTracks = audioTracks,
                    subtitleTracks = playbackSnapshot.subtitleTracks,
                    subtitleSelection = playbackSnapshot.subtitleSelection,
                    playbackSpeed = playbackSnapshot.playbackSpeed,
                    allowSpeed = false,
                    onSelectAudio = { selectionId ->
                        controllerScope.launch { playbackController.selectAudioTrack(selectionId) }
                    },
                    onSelectSubtitle = { selection ->
                        controllerScope.launch { playbackController.selectSubtitle(selection) }
                    },
                    onSelectSpeed = { },
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
private fun LiveBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = OwnPlayColors.AccentStrong.copy(alpha = 0.90f),
        shape = OwnPlayShapeTokens.Small,
        tonalElevation = 0.dp,
    ) {
        Text(
            text = "LIVE",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
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
        SurfaceView(context).apply { keepScreenOn = true }
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
        val stableChannelId = channelId
        if (stableChannelId == null) {
            guide = LiveNowNext()
            return@LaunchedEffect
        }
        while (true) {
            guide = liveRepository.loadNowNext(stableChannelId)
            val nowMs = System.currentTimeMillis()
            delay(app.ownplay.mobile.feature.live.domain.LiveGuidePolicy.refreshDelayMs(guide, nowMs))
        }
    }
    return guide
}

@Composable
private fun rememberEpgClock(): Long {
    var nowEpochSeconds by remember { mutableStateOf(Instant.now().epochSecond) }
    LaunchedEffect(Unit) {
        while (true) {
            nowEpochSeconds = Instant.now().epochSecond
            delay(30_000)
        }
    }
    return nowEpochSeconds
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
