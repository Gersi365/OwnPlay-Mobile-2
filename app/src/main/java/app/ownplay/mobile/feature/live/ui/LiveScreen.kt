package app.ownplay.mobile.feature.live.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ownplay.mobile.OwnPlayApplication
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayFeaturePlaceholder
import app.ownplay.mobile.feature.live.domain.LiveGuidePolicy
import app.ownplay.mobile.feature.live.domain.LiveGuideRepository
import app.ownplay.mobile.feature.live.domain.LiveNowNext
import app.ownplay.mobile.feature.live.domain.LiveOrganizationChannel
import app.ownplay.mobile.feature.live.domain.LiveProgram
import app.ownplay.mobile.feature.live.domain.LiveOrganizationMode
import app.ownplay.mobile.feature.live.domain.LiveOrganizationRepository
import app.ownplay.mobile.feature.live.domain.OwnPlayCountryScope
import app.ownplay.mobile.feature.live.domain.OwnPlayLiveCatalogSnapshot
import app.ownplay.mobile.feature.live.domain.OwnPlayLivePlacement
import app.ownplay.mobile.feature.live.domain.OwnPlayLiveSemanticCategory
import app.ownplay.mobile.feature.live.domain.ProviderLiveCatalogSnapshot
import app.ownplay.mobile.feature.library.data.LibraryArtworkLoader
import app.ownplay.mobile.feature.library.ui.ArtworkPresentation
import app.ownplay.mobile.feature.library.ui.LibraryArtwork
import app.ownplay.mobile.feature.playback.data.Media3PlaybackEngine
import app.ownplay.mobile.feature.playback.domain.PlaybackPresentation
import app.ownplay.mobile.feature.playback.domain.PlaybackReadiness
import app.ownplay.mobile.feature.playback.domain.PlaybackSessionController
import app.ownplay.mobile.feature.playback.domain.PlaybackTarget
import app.ownplay.mobile.feature.playback.ui.PlaybackVideoSurface
import app.ownplay.mobile.feature.settings.domain.DisplayPreferences
import app.ownplay.mobile.sources.domain.SourceSummary
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LiveScreen(
    modifier: Modifier = Modifier,
) {
    val application = LocalContext.current.applicationContext as OwnPlayApplication
    val services = remember(application) { application.services }
    val activeSourceFlow = remember(services.sourceRepository) {
        services.sourceRepository.observeActiveSource()
    }
    val activeSource by activeSourceFlow.collectAsState(initial = null)
    val displayPreferences by services.displayPreferencesRepository.preferences.collectAsState(
        initial = DisplayPreferences(),
    )

    val source = activeSource
    if (source == null) {
        OwnPlayFeaturePlaceholder(
            title = "Live",
            message = "Add a source in Settings to start watching live channels.",
            modifier = modifier,
        )
        return
    }

    var initialRefreshError by remember(source.sourceId) { mutableStateOf<String?>(null) }
    var initialRefreshInProgress by remember(source.sourceId) { mutableStateOf(false) }
    LaunchedEffect(source.sourceId, source.lastSuccessfulRefreshAtEpochMs) {
        if (source.enabled && source.lastSuccessfulRefreshAtEpochMs == null) {
            initialRefreshError = null
            initialRefreshInProgress = true
            try {
                val result = services.sourceRepository.refreshSource(source.sourceId)
                if (result is app.ownplay.mobile.sources.domain.SourceRefreshResult.Failure) {
                    initialRefreshError = result.safeMessage ?: "Source refresh failed."
                }
            } finally {
                initialRefreshInProgress = false
            }
        }
    }

    LiveSourceScreen(
        source = source,
        repository = services.liveOrganizationRepository,
        guideRepository = services.liveGuideRepository,
        playbackSessionController = services.playbackSessionController,
        playbackEngine = services.playbackEngine,
        artworkLoader = services.libraryArtworkLoader,
        compactMediaRows = displayPreferences.compactMediaRows,
        showChannelLogos = displayPreferences.showChannelLogos,
        preferTvgName = displayPreferences.preferTvgName,
        hideChannelPrefix = displayPreferences.hideChannelPrefix,
        catalogLoadError = initialRefreshError,
        catalogLoading = initialRefreshInProgress,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveSourceScreen(
    source: SourceSummary,
    repository: LiveOrganizationRepository,
    guideRepository: LiveGuideRepository,
    playbackSessionController: PlaybackSessionController,
    playbackEngine: Media3PlaybackEngine,
    artworkLoader: LibraryArtworkLoader,
    compactMediaRows: Boolean,
    showChannelLogos: Boolean,
    preferTvgName: Boolean,
    hideChannelPrefix: Boolean,
    catalogLoadError: String?,
    catalogLoading: Boolean,
    modifier: Modifier,
) {
    val sourceId = source.sourceId
    val modeFlow = remember(repository, sourceId) { repository.observeMode(sourceId) }
    val providerFlow = remember(repository, sourceId) { repository.observeProviderCatalog(sourceId) }
    val ownPlayFlow = remember(repository, sourceId) { repository.observeOwnPlayCatalog(sourceId) }
    val favoritesFlow = remember(repository, sourceId) { repository.observeFavoriteChannelIds(sourceId) }

    val mode by modeFlow.collectAsState(initial = LiveOrganizationMode.PROVIDER)
    val providerCatalog by providerFlow.collectAsState(
        initial = ProviderLiveCatalogSnapshot(categories = emptyList(), channels = emptyList()),
    )
    val ownPlayCatalog by ownPlayFlow.collectAsState(
        initial = OwnPlayLiveCatalogSnapshot(
            countries = emptyList(),
            semanticCategories = OwnPlayLiveSemanticCategory.canonicalOrder,
            channelIdsByPlacement = emptyMap(),
        ),
    )
    val favoriteChannelIds by favoritesFlow.collectAsState(initial = emptySet())
    val playbackState by playbackSessionController.state.collectAsState()
    val scope = rememberCoroutineScope()

    var requestedCountryId by rememberSaveable(sourceId.value) { mutableStateOf<String?>(null) }
    var requestedSemanticName by rememberSaveable(sourceId.value) {
        mutableStateOf(OwnPlayLiveSemanticCategory.GENERAL.name)
    }
    var requestedProviderCategoryId by rememberSaveable(sourceId.value) { mutableStateOf<String?>(null) }
    var favoritesOnly by rememberSaveable(sourceId.value) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(sourceId.value) { mutableStateOf("") }
    var movingChannelId by remember { mutableStateOf<String?>(null) }
    var moveCountryId by remember { mutableStateOf<String?>(null) }
    var moveSemanticName by remember { mutableStateOf(OwnPlayLiveSemanticCategory.GENERAL.name) }
    var operationMessage by remember { mutableStateOf<String?>(null) }

    val selectedCountryId = LiveBrowseStatePolicy.selectedCountryId(
        requestedCountryId = requestedCountryId,
        countries = ownPlayCatalog.countries,
    )
    val selectedSemantic = LiveBrowseStatePolicy.selectedSemanticCategory(requestedSemanticName)
    val providerOptions = remember(providerCatalog.categories, providerCatalog.channels) {
        LiveBrowseStatePolicy.providerCategoryOptions(providerCatalog)
    }
    val selectedProviderCategoryId = remember(requestedProviderCategoryId, providerOptions) {
        requestedProviderCategoryId
            ?.takeIf { requested -> providerOptions.any { it.categoryId == requested } }
            ?: providerOptions.firstOrNull()?.categoryId
    }
    val displayChannels = if (mode == LiveOrganizationMode.OWNPLAY) {
        ownPlayCatalog.channels
    } else {
        providerCatalog.channels
    }
    val channelById = remember(displayChannels) {
        displayChannels.associateBy(LiveOrganizationChannel::channelId)
    }
    val playbackTarget = (playbackState.target as? PlaybackTarget.LiveChannel)
        ?.takeIf { it.sourceId == sourceId }
    val playbackChannelName = playbackTarget?.let { target ->
        channelById[target.channelId]
            ?.let { channel -> LiveChannelDisplayPolicy.displayName(
                    channel,
                    preferTvgName,
                    hideChannelPrefix && mode == LiveOrganizationMode.OWNPLAY,
                ) }
            ?: "Live channel"
    }
    val selectedGuide = rememberLiveGuide(guideRepository, sourceId, playbackTarget?.channelId)
    val epgNowEpochSeconds = rememberEpgClock()
    val visibleChannelIds = remember(
        mode,
        ownPlayCatalog,
        providerCatalog,
        selectedCountryId,
        selectedSemantic,
        selectedProviderCategoryId,
        favoritesOnly,
        favoriteChannelIds,
        searchQuery,
        displayChannels,
    ) {
        if (searchQuery.isNotBlank()) {
            LiveBrowseStatePolicy.searchChannelIds(
                channels = displayChannels,
                query = searchQuery,
                favoritesOnly = favoritesOnly,
                favoriteChannelIds = favoriteChannelIds,
            )
        } else if (mode == LiveOrganizationMode.OWNPLAY) {
            LiveBrowseStatePolicy.visibleOwnPlayChannelIds(
                catalog = ownPlayCatalog,
                countryId = selectedCountryId,
                semanticCategory = selectedSemantic,
                favoritesOnly = favoritesOnly,
                favoriteChannelIds = favoriteChannelIds,
            )
        } else {
            LiveBrowseStatePolicy.visibleProviderChannelIds(
                catalog = providerCatalog,
                categoryId = selectedProviderCategoryId,
                favoritesOnly = favoritesOnly,
                favoriteChannelIds = favoriteChannelIds,
            )
        }
    }
    val visibleChannels = remember(visibleChannelIds, channelById) {
        visibleChannelIds.mapNotNull(channelById::get)
    }

    LaunchedEffect(visibleChannelIds, playbackTarget, playbackState.presentation) {
        if (
            LiveBrowseStatePolicy.shouldDismissPreview(
                selectedChannelId = playbackTarget?.channelId,
                visibleChannelIds = visibleChannelIds,
                isPreview = playbackState.presentation == PlaybackPresentation.PREVIEW,
            )
        ) {
            playbackSessionController.clear()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Live",
                    color = OwnPlayColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = source.displayName,
                    color = OwnPlayColors.TextSecondary,
                )
            }
            FilterChip(
                selected = favoritesOnly,
                onClick = { favoritesOnly = !favoritesOnly },
                label = { Text("Favorites") },
            )
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search channels") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (mode == LiveOrganizationMode.OWNPLAY) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(ownPlayCatalog.countries, key = { it.countryId }) { country ->
                    FilterChip(
                        selected = country.countryId == selectedCountryId,
                        onClick = { requestedCountryId = country.countryId },
                        label = { Text(country.displayName) },
                    )
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(OwnPlayLiveSemanticCategory.canonicalOrder, key = { it.name }) { category ->
                    FilterChip(
                        selected = category == selectedSemantic,
                        onClick = { requestedSemanticName = category.name },
                        label = { Text(category.displayName) },
                    )
                }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(providerOptions, key = { it.categoryId }) { category ->
                    FilterChip(
                        selected = category.categoryId == selectedProviderCategoryId,
                        onClick = { requestedProviderCategoryId = category.categoryId },
                        label = { Text(category.displayName) },
                    )
                }
            }
        }

        if (
            playbackTarget != null &&
            playbackChannelName != null &&
            playbackState.presentation == PlaybackPresentation.PREVIEW
        ) {
            PlaybackPreviewCard(
                channelName = playbackChannelName,
                guide = selectedGuide,
                nowEpochSeconds = epgNowEpochSeconds,
                readiness = playbackState.readiness,
                playbackEngine = playbackEngine,
                onFullscreen = playbackSessionController::enterFullscreen,
            )
        }

        if (catalogLoading) {
            Text(text = "Importing Live catalog…", color = OwnPlayColors.TextMuted)
        }
        catalogLoadError?.let { message ->
            Text(text = message, color = OwnPlayColors.Error)
        }
        operationMessage?.let { message ->
            Text(text = message, color = OwnPlayColors.Error)
        }

        if (visibleChannels.isEmpty() && !catalogLoading) {
            Text(
                text = if (favoritesOnly) "No favorite channels in this selection." else "No channels in this selection.",
                color = OwnPlayColors.TextMuted,
                modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(if (compactMediaRows) 3.dp else 6.dp),
            ) {
                items(visibleChannels, key = { it.channelId }) { channel ->
                    val guide = rememberLiveGuide(guideRepository, sourceId, channel.channelId)
                    LiveChannelRow(
                        channel = channel,
                        guide = guide,
                        nowEpochSeconds = epgNowEpochSeconds,
                        selected = playbackTarget?.channelId == channel.channelId,
                        favorite = channel.channelId in favoriteChannelIds,
                        ownPlayMode = mode == LiveOrganizationMode.OWNPLAY,
                        hasManualPlacement = channel.channelId in ownPlayCatalog.manualPlacementChannelIds,
                        compact = compactMediaRows,
                        showLogo = showChannelLogos,
                        preferTvgName = preferTvgName,
                        hideChannelPrefix = hideChannelPrefix && mode == LiveOrganizationMode.OWNPLAY,
                        artworkLoader = artworkLoader,
                        onActivate = {
                            scope.launch {
                                playbackSessionController.activateLiveChannel(
                                    PlaybackTarget.LiveChannel(
                                        sourceId = sourceId,
                                        channelId = channel.channelId,
                                    ),
                                )
                            }
                        },
                        onFavorite = {
                            scope.launch {
                                if (!repository.setFavorite(
                                        sourceId = sourceId,
                                        channelId = channel.channelId,
                                        favorite = channel.channelId !in favoriteChannelIds,
                                    )
                                ) {
                                    operationMessage = "Favorite could not be updated."
                                }
                            }
                        },
                        onMove = {
                            movingChannelId = channel.channelId
                            moveCountryId = selectedCountryId
                                ?: ownPlayCatalog.countries.firstOrNull()?.countryId
                            moveSemanticName = selectedSemantic.name
                            operationMessage = null
                        },
                        onReset = {
                            operationMessage = null
                            scope.launch {
                                if (!repository.resetChannelToAutomatic(sourceId, channel.channelId)) {
                                    operationMessage = "Manual placement could not be reset."
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    val channelToMove = movingChannelId
    if (channelToMove != null) {
        MoveChannelSheet(
            countries = ownPlayCatalog.countries,
            selectedCountryId = LiveBrowseStatePolicy.selectedCountryId(
                requestedCountryId = moveCountryId,
                countries = ownPlayCatalog.countries,
            ),
            selectedSemantic = LiveBrowseStatePolicy.selectedSemanticCategory(moveSemanticName),
            onCountrySelected = { moveCountryId = it },
            onSemanticSelected = { moveSemanticName = it.name },
            onMove = { countryId, semantic ->
                scope.launch {
                    val moved = repository.moveChannel(
                        sourceId = sourceId,
                        channelId = channelToMove,
                        placement = OwnPlayLivePlacement(
                            countryId = countryId,
                            semanticCategory = semantic,
                        ),
                    )
                    if (moved) {
                        movingChannelId = null
                        requestedCountryId = countryId
                        requestedSemanticName = semantic.name
                        operationMessage = null
                    } else {
                        operationMessage = "Channel placement could not be updated."
                    }
                }
            },
            onDismiss = { movingChannelId = null },
        )
    }

    if (
        playbackTarget != null &&
        playbackChannelName != null &&
        playbackState.presentation == PlaybackPresentation.FULLSCREEN
    ) {
        PlaybackFullscreenPresentation(
            channelName = playbackChannelName,
            guide = selectedGuide,
            nowEpochSeconds = epgNowEpochSeconds,
            readiness = playbackState.readiness,
            playbackEngine = playbackEngine,
            onDismiss = playbackSessionController::returnToPreview,
        )
    }
}

@Composable
private fun LiveChannelRow(
    channel: LiveOrganizationChannel,
    guide: LiveNowNext,
    nowEpochSeconds: Long,
    selected: Boolean,
    favorite: Boolean,
    ownPlayMode: Boolean,
    hasManualPlacement: Boolean,
    compact: Boolean,
    showLogo: Boolean,
    preferTvgName: Boolean,
    hideChannelPrefix: Boolean,
    artworkLoader: LibraryArtworkLoader,
    onActivate: () -> Unit,
    onFavorite: () -> Unit,
    onMove: () -> Unit,
    onReset: () -> Unit,
) {
    Surface(
        color = OwnPlayColors.Surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onActivate),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 10.dp else 12.dp,
                vertical = if (compact) 6.dp else 8.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showLogo && !channel.logoUrl.isNullOrBlank()) {
                LibraryArtwork(
                    url = channel.logoUrl,
                    loader = artworkLoader,
                    compact = compact,
                    presentation = ArtworkPresentation.CHANNEL_LOGO,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = LiveChannelDisplayPolicy.displayName(
                        channel,
                        preferTvgName,
                        hideChannelPrefix,
                    ),
                    color = OwnPlayColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                guide.now?.let { current ->
                    Text(
                        text = currentProgramLine(current, nowEpochSeconds),
                        color = OwnPlayColors.TextSecondary,
                        maxLines = 1,
                    )
                }
                if (selected) {
                    guide.next?.let { next ->
                        Text(
                            text = "Next ${programTimeRange(next)} • ${next.title}",
                            color = OwnPlayColors.TextMuted,
                            maxLines = 1,
                        )
                    }
                }
                if (ownPlayMode && hasManualPlacement) {
                    Text(text = "Manual placement", color = OwnPlayColors.TextMuted)
                }
            }
            Text(
                text = if (favorite) "★" else "☆",
                color = if (favorite) OwnPlayColors.Accent else OwnPlayColors.TextMuted,
                modifier = Modifier
                    .clickable(onClick = onFavorite)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
            if (ownPlayMode) {
                TextButton(onClick = onMove) { Text("Move") }
                if (hasManualPlacement) {
                    TextButton(onClick = onReset) { Text("Reset") }
                }
            }
        }
    }
}

@Composable
private fun PlaybackPreviewCard(
    channelName: String,
    guide: LiveNowNext,
    nowEpochSeconds: Long,
    readiness: PlaybackReadiness,
    playbackEngine: Media3PlaybackEngine,
    onFullscreen: () -> Unit,
) {
    Surface(
        color = OwnPlayColors.Surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth()) {
                PlaybackVideoSurface(
                    playbackEngine = playbackEngine,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp),
                )
                TextButton(
                    onClick = onFullscreen,
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Text("Fullscreen")
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = channelName,
                    color = OwnPlayColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                guide.now?.let { current ->
                    Text(
                        text = currentProgramLine(current, nowEpochSeconds),
                        color = OwnPlayColors.TextSecondary,
                        maxLines = 1,
                    )
                }
                guide.next?.let { next ->
                    Text(
                        text = "Next ${programTimeRange(next)} • ${next.title}",
                        color = OwnPlayColors.TextMuted,
                        maxLines = 1,
                    )
                }
                PlaybackReadinessMessage(readiness)
            }
        }
    }
}

@Composable
private fun PlaybackFullscreenPresentation(
    channelName: String,
    guide: LiveNowNext,
    nowEpochSeconds: Long,
    readiness: PlaybackReadiness,
    playbackEngine: Media3PlaybackEngine,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = OwnPlayColors.Background,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                PlaybackVideoSurface(
                    playbackEngine = playbackEngine,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = channelName,
                        color = OwnPlayColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    PlaybackReadinessMessage(readiness)
                }
            }
        }
    }
}

@Composable
private fun rememberLiveGuide(
    repository: LiveGuideRepository,
    sourceId: app.ownplay.mobile.sources.domain.SourceId,
    channelId: String?,
): LiveNowNext {
    var guide by remember(repository, sourceId, channelId) { mutableStateOf(LiveNowNext()) }
    LaunchedEffect(repository, sourceId, channelId) {
        val stableChannelId = channelId?.takeIf(String::isNotBlank)
        if (stableChannelId == null) {
            guide = LiveNowNext()
            return@LaunchedEffect
        }
        while (true) {
            guide = repository.loadNowNext(sourceId, stableChannelId)
            val nowMs = System.currentTimeMillis()
            val boundary = LiveGuidePolicy.nextBoundaryEpochSeconds(guide, nowMs / 1_000L)
            val waitMs = boundary
                ?.let { ((it * 1_000L) + 100L - nowMs).coerceAtLeast(250L) }
                ?.coerceAtMost(125_000L)
                ?: 125_000L
            delay(waitMs)
        }
    }
    return guide
}

@Composable
private fun rememberEpgClock(): Long {
    var nowEpochSeconds by remember { mutableStateOf(System.currentTimeMillis() / 1_000L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            nowEpochSeconds = System.currentTimeMillis() / 1_000L
        }
    }
    return nowEpochSeconds
}

private fun currentProgramLine(program: LiveProgram, nowEpochSeconds: Long): String {
    val progress = LiveGuidePolicy.progressFraction(program, nowEpochSeconds)
        ?.let { value -> "${(value * 100).toInt()}%" }
    return listOf(programTimeRange(program), progress, program.title)
        .filterNotNull()
        .filter(String::isNotBlank)
        .joinToString(" • ")
}

private fun programTimeRange(program: LiveProgram): String {
    val formatter = DateFormat.getTimeInstance(DateFormat.SHORT)
    val start = program.startEpochSeconds?.let { formatter.format(Date(it * 1_000L)) }
    val end = program.endEpochSeconds?.let { formatter.format(Date(it * 1_000L)) }
    return when {
        start != null && end != null -> "$start–$end"
        start != null -> start
        end != null -> end
        else -> ""
    }
}

@Composable
private fun PlaybackReadinessMessage(readiness: PlaybackReadiness) {
    val message = when (readiness) {
        PlaybackReadiness.IDLE -> null
        PlaybackReadiness.PREPARING -> "Preparing playback…"
        PlaybackReadiness.PREPARED -> null
        PlaybackReadiness.UNAVAILABLE -> "Playback is unavailable for this channel."
    }
    if (message != null) {
        Text(text = message, color = OwnPlayColors.TextMuted)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveChannelSheet(
    countries: List<OwnPlayCountryScope>,
    selectedCountryId: String?,
    selectedSemantic: OwnPlayLiveSemanticCategory,
    onCountrySelected: (String) -> Unit,
    onSemanticSelected: (OwnPlayLiveSemanticCategory) -> Unit,
    onMove: (String, OwnPlayLiveSemanticCategory) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Move channel",
                fontWeight = FontWeight.Bold,
                color = OwnPlayColors.TextPrimary,
            )
            Text(text = "Country", color = OwnPlayColors.TextSecondary)
            LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                items(countries, key = { it.countryId }) { country ->
                    TextButton(
                        onClick = { onCountrySelected(country.countryId) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (country.countryId == selectedCountryId) "${country.displayName} • Selected" else country.displayName,
                        )
                    }
                }
            }
            Text(text = "Category", color = OwnPlayColors.TextSecondary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(OwnPlayLiveSemanticCategory.canonicalOrder, key = { it.name }) { category ->
                    FilterChip(
                        selected = category == selectedSemantic,
                        onClick = { onSemanticSelected(category) },
                        label = { Text(category.displayName) },
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        selectedCountryId?.let { countryId -> onMove(countryId, selectedSemantic) }
                    },
                    enabled = selectedCountryId != null,
                ) {
                    Text("Move channel")
                }
            }
        }
    }
}
