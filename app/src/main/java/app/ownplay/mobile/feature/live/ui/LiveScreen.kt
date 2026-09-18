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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import app.ownplay.mobile.feature.live.domain.LiveOrganizationChannel
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

    LiveSourceScreen(
        source = source,
        repository = services.liveOrganizationRepository,
        playbackSessionController = services.playbackSessionController,
        playbackEngine = services.playbackEngine,
        artworkLoader = services.libraryArtworkLoader,
        compactMediaRows = displayPreferences.compactMediaRows,
        showChannelLogos = displayPreferences.showChannelLogos,
        preferTvgName = displayPreferences.preferTvgName,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveSourceScreen(
    source: SourceSummary,
    repository: LiveOrganizationRepository,
    playbackSessionController: PlaybackSessionController,
    playbackEngine: Media3PlaybackEngine,
    artworkLoader: LibraryArtworkLoader,
    compactMediaRows: Boolean,
    showChannelLogos: Boolean,
    preferTvgName: Boolean,
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
    var countrySheetOpen by remember { mutableStateOf(false) }
    var movingChannelId by remember { mutableStateOf<String?>(null) }
    var moveCountryId by remember { mutableStateOf<String?>(null) }
    var moveSemanticName by remember { mutableStateOf(OwnPlayLiveSemanticCategory.GENERAL.name) }
    var operationMessage by remember { mutableStateOf<String?>(null) }

    val selectedCountryId = LiveBrowseStatePolicy.selectedCountryId(
        requestedCountryId = requestedCountryId,
        countries = ownPlayCatalog.countries,
    )
    val selectedCountry = ownPlayCatalog.countries.firstOrNull { it.countryId == selectedCountryId }
    val selectedSemantic = LiveBrowseStatePolicy.selectedSemanticCategory(requestedSemanticName)
    val providerOptions = LiveBrowseStatePolicy.providerCategoryOptions(providerCatalog)
    val selectedProviderCategoryId = LiveBrowseStatePolicy.selectedProviderCategoryId(
        requestedCategoryId = requestedProviderCategoryId,
        catalog = providerCatalog,
    )
    val channelById = providerCatalog.channels.associateBy(LiveOrganizationChannel::channelId)
    val playbackTarget = (playbackState.target as? PlaybackTarget.LiveChannel)
        ?.takeIf { it.sourceId == sourceId }
    val playbackChannelName = playbackTarget?.let { target ->
        channelById[target.channelId]
            ?.let { channel -> LiveChannelDisplayPolicy.displayName(channel, preferTvgName) }
            ?: "Live channel"
    }
    val visibleChannelIds = if (mode == LiveOrganizationMode.OWNPLAY) {
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
    val visibleChannels = visibleChannelIds.mapNotNull(channelById::get)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == LiveOrganizationMode.OWNPLAY,
                onClick = {
                    operationMessage = null
                    scope.launch { repository.setMode(sourceId, LiveOrganizationMode.OWNPLAY) }
                },
                label = { Text("OwnPlay") },
            )
            FilterChip(
                selected = mode == LiveOrganizationMode.PROVIDER,
                onClick = {
                    operationMessage = null
                    scope.launch { repository.setMode(sourceId, LiveOrganizationMode.PROVIDER) }
                },
                label = { Text("Provider") },
            )
        }

        if (mode == LiveOrganizationMode.OWNPLAY) {
            OutlinedButton(
                onClick = { countrySheetOpen = true },
                enabled = ownPlayCatalog.countries.isNotEmpty(),
            ) {
                Text(selectedCountry?.displayName ?: "Select country")
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(OwnPlayLiveSemanticCategory.canonicalOrder, key = { it.name }) { category ->
                    FilterChip(
                        selected = category == selectedSemantic,
                        onClick = { requestedSemanticName = category.name },
                        label = { Text(category.displayName) },
                    )
                }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                readiness = playbackState.readiness,
                playbackEngine = playbackEngine,
                onFullscreen = playbackSessionController::enterFullscreen,
            )
        }

        operationMessage?.let { message ->
            Text(text = message, color = OwnPlayColors.Error)
        }

        if (visibleChannels.isEmpty()) {
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
                verticalArrangement = Arrangement.spacedBy(if (compactMediaRows) 4.dp else 8.dp),
            ) {
                items(visibleChannels, key = { it.channelId }) { channel ->
                    LiveChannelRow(
                        channel = channel,
                        ownPlayMode = mode == LiveOrganizationMode.OWNPLAY,
                        hasManualPlacement = channel.channelId in ownPlayCatalog.manualPlacementChannelIds,
                        compact = compactMediaRows,
                        showLogo = showChannelLogos,
                        preferTvgName = preferTvgName,
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

    if (countrySheetOpen) {
        CountrySelectionSheet(
            countries = ownPlayCatalog.countries,
            selectedCountryId = selectedCountryId,
            onSelected = { countryId ->
                requestedCountryId = countryId
                countrySheetOpen = false
            },
            onDismiss = { countrySheetOpen = false },
        )
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
            readiness = playbackState.readiness,
            playbackEngine = playbackEngine,
            onDismiss = playbackSessionController::returnToPreview,
        )
    }
}

@Composable
private fun LiveChannelRow(
    channel: LiveOrganizationChannel,
    ownPlayMode: Boolean,
    hasManualPlacement: Boolean,
    compact: Boolean,
    showLogo: Boolean,
    preferTvgName: Boolean,
    artworkLoader: LibraryArtworkLoader,
    onActivate: () -> Unit,
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
                horizontal = if (compact) 12.dp else 16.dp,
                vertical = if (compact) 7.dp else 12.dp,
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
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = LiveChannelDisplayPolicy.displayName(channel, preferTvgName),
                    color = OwnPlayColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                if (ownPlayMode && hasManualPlacement) {
                    Text(text = "Manual placement", color = OwnPlayColors.TextMuted)
                }
            }
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
                        .height(180.dp),
                )
                TextButton(
                    onClick = onFullscreen,
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Text("Fullscreen")
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Preview",
                    color = OwnPlayColors.TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                )
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

@Composable
private fun PlaybackFullscreenPresentation(
    channelName: String,
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
private fun CountrySelectionSheet(
    countries: List<OwnPlayCountryScope>,
    selectedCountryId: String?,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Select country",
                fontWeight = FontWeight.Bold,
                color = OwnPlayColors.TextPrimary,
            )
            LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                items(countries, key = { it.countryId }) { country ->
                    TextButton(
                        onClick = { onSelected(country.countryId) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (country.countryId == selectedCountryId) "${country.displayName} • Selected" else country.displayName,
                        )
                    }
                }
            }
        }
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
