package app.ownplay.mobile.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.ownplay.mobile.OwnPlayApplication
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayShapes
import app.ownplay.mobile.feature.live.domain.LiveOrganizationMode
import app.ownplay.mobile.feature.live.domain.LiveOrganizationRepository
import app.ownplay.mobile.feature.live.domain.OwnPlayLiveCatalogSnapshot
import app.ownplay.mobile.feature.live.domain.OwnPlayLiveSemanticCategory
import app.ownplay.mobile.downloads.domain.DownloadPreferences
import app.ownplay.mobile.downloads.domain.DownloadPreferencesRepository
import app.ownplay.mobile.feature.playback.domain.PlaybackPreferences
import app.ownplay.mobile.feature.playback.domain.PlaybackPreferencesRepository
import app.ownplay.mobile.feature.settings.domain.DisplayPreferences
import app.ownplay.mobile.feature.settings.domain.DisplayPreferencesRepository
import app.ownplay.mobile.feature.settings.domain.SourceRefreshSchedule
import app.ownplay.mobile.feature.settings.domain.SourceRefreshScheduleRepository
import app.ownplay.mobile.sources.domain.SourceInput
import app.ownplay.mobile.sources.domain.SourceMutationRejection
import app.ownplay.mobile.sources.domain.SourceMutationResult
import app.ownplay.mobile.sources.domain.SourceRefreshResult
import app.ownplay.mobile.sources.domain.SourceRepository
import app.ownplay.mobile.sources.domain.SourceSummary
import app.ownplay.mobile.sources.domain.SourceType
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val application = LocalContext.current.applicationContext as OwnPlayApplication
    val services = remember(application) { application.services }
    SettingsSourcesScreen(
        repository = services.sourceRepository,
        refreshScheduleRepository = services.refreshScheduleRepository,
        displayPreferencesRepository = services.displayPreferencesRepository,
        liveOrganizationRepository = services.liveOrganizationRepository,
        playbackPreferencesRepository = services.playbackPreferencesRepository,
        downloadPreferencesRepository = services.downloadPreferencesRepository,
        modifier = modifier,
    )
}

@Composable
private fun SettingsSourcesScreen(
    repository: SourceRepository,
    refreshScheduleRepository: SourceRefreshScheduleRepository,
    displayPreferencesRepository: DisplayPreferencesRepository,
    liveOrganizationRepository: LiveOrganizationRepository,
    playbackPreferencesRepository: PlaybackPreferencesRepository,
    downloadPreferencesRepository: DownloadPreferencesRepository,
    modifier: Modifier,
) {
    val sourcesFlow = remember(repository) { repository.observeSources() }
    val activeSourceFlow = remember(repository) { repository.observeActiveSource() }
    val sources by sourcesFlow.collectAsState(initial = emptyList())
    val activeSource by activeSourceFlow.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var busyIds by remember { mutableStateOf(emptySet<String>()) }
    var message by remember { mutableStateOf<String?>(null) }
    var addType by remember { mutableStateOf<SourceType?>(null) }
    var renameSource by remember { mutableStateOf<SourceSummary?>(null) }
    var removeSource by remember { mutableStateOf<SourceSummary?>(null) }

    fun runForSource(source: SourceSummary, block: suspend () -> String) {
        if (source.sourceId.value in busyIds) return
        busyIds = busyIds + source.sourceId.value
        scope.launch {
            message = block()
            busyIds = busyIds - source.sourceId.value
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Settings", color = OwnPlayColors.TextPrimary, fontWeight = FontWeight.Bold)
                Text(
                    "Sources",
                    color = OwnPlayColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Manage provider sources without exposing saved credentials.",
                    color = OwnPlayColors.TextSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { addType = SourceType.XTREAM }) { Text("Add Xtream") }
                    TextButton(onClick = { addType = SourceType.M3U }) { Text("Add M3U") }
                }
            }
        }

        message?.let { status ->
            item {
                Text(status, color = OwnPlayColors.TextSecondary)
            }
        }

        if (sources.isEmpty()) {
            item {
                Text(
                    "No sources configured. Add Xtream or M3U to begin.",
                    color = OwnPlayColors.TextMuted,
                )
            }
        } else {
            items(sources, key = { it.sourceId.value }) { source ->
                SourceSettingsCard(
                    source = source,
                    isActive = activeSource?.sourceId == source.sourceId,
                    busy = source.sourceId.value in busyIds,
                    onSetActive = {
                        runForSource(source) {
                            if (repository.setActiveSource(source.sourceId)) {
                                "${source.displayName} is now active."
                            } else {
                                "Could not select ${source.displayName}."
                            }
                        }
                    },
                    onRefresh = {
                        runForSource(source) {
                            when (val result = repository.refreshSource(source.sourceId)) {
                                SourceRefreshResult.Success -> "${source.displayName} refreshed."
                                is SourceRefreshResult.Failure ->
                                    result.safeMessage ?: "${source.displayName} refresh failed."
                            }
                        }
                    },
                    onRename = { renameSource = source },
                    onRemove = { removeSource = source },
                )
            }
        }

        item {
            RefreshScheduleSection(
                source = activeSource,
                repository = refreshScheduleRepository,
                onMessage = { message = it },
            )
        }

        item {
            DisplayPreferencesSection(
                repository = displayPreferencesRepository,
                onMessage = { message = it },
            )
        }

        item {
            LiveOrganizationSettingsSection(
                source = activeSource,
                repository = liveOrganizationRepository,
                onMessage = { message = it },
            )
        }

        item {
            PlaybackSettingsSection(
                repository = playbackPreferencesRepository,
                onMessage = { message = it },
            )
        }

        item {
            DownloadSettingsSection(
                repository = downloadPreferencesRepository,
                onMessage = { message = it },
            )
        }

        item {
            SettingsNextSections()
        }
    }

    when (addType) {
        SourceType.XTREAM -> XtreamSourceDialog(
            onDismiss = { addType = null },
            onSubmit = { input ->
                scope.launch {
                    val result = repository.addSource(input)
                    message = mutationMessage(result, "Xtream source added.")
                    if (result is SourceMutationResult.Success) addType = null
                }
            },
        )
        SourceType.M3U -> M3uSourceDialog(
            onDismiss = { addType = null },
            onSubmit = { input ->
                scope.launch {
                    val result = repository.addSource(input)
                    message = mutationMessage(result, "M3U source added.")
                    if (result is SourceMutationResult.Success) addType = null
                }
            },
        )
        null -> Unit
    }

    renameSource?.let { source ->
        RenameSourceDialog(
            source = source,
            onDismiss = { renameSource = null },
            onSubmit = { name ->
                runForSource(source) {
                    val result = repository.renameSource(source.sourceId, name)
                    if (result is SourceMutationResult.Success) renameSource = null
                    mutationMessage(result, "Source renamed.")
                }
            },
        )
    }

    removeSource?.let { source ->
        AlertDialog(
            onDismissRequest = { removeSource = null },
            title = { Text("Remove source?") },
            text = {
                Text(
                    "${source.displayName} and its source-scoped app data will be removed. " +
                        "Completed files already published to storage are left in place.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        removeSource = null
                        runForSource(source) {
                            if (repository.removeSource(source.sourceId)) {
                                "${source.displayName} removed."
                            } else {
                                "Could not remove ${source.displayName}."
                            }
                        }
                    },
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removeSource = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SourceSettingsCard(
    source: SourceSummary,
    isActive: Boolean,
    busy: Boolean,
    onSetActive: () -> Unit,
    onRefresh: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = OwnPlayShapes.Medium,
        color = OwnPlayColors.Surface,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(source.displayName, color = OwnPlayColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                if (isActive) Text("Active", color = OwnPlayColors.Accent)
            }
            Text(
                "${source.type.name} • ${source.connectionLabel}",
                color = OwnPlayColors.TextSecondary,
            )
            Text(
                source.lastSuccessfulRefreshAtEpochMs?.let {
                    "Last refresh: ${DateFormat.getDateTimeInstance().format(Date(it))}"
                } ?: "Not refreshed yet",
                color = OwnPlayColors.TextMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!isActive) {
                    TextButton(enabled = !busy, onClick = onSetActive) { Text("Set active") }
                }
                TextButton(enabled = !busy, onClick = onRefresh) { Text("Refresh") }
                TextButton(enabled = !busy, onClick = onRename) { Text("Rename") }
                TextButton(enabled = !busy, onClick = onRemove) { Text("Remove") }
            }
        }
    }
}

@Composable
private fun RefreshScheduleSection(
    source: SourceSummary?,
    repository: SourceRefreshScheduleRepository,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = OwnPlayShapes.Medium,
        color = OwnPlayColors.SurfaceRaised,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Refresh schedule", color = OwnPlayColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            if (source == null) {
                Text("Add or select a source to configure automatic refresh.", color = OwnPlayColors.TextMuted)
            } else {
                val scheduleFlow = remember(repository, source.sourceId) {
                    repository.observeSchedule(source.sourceId)
                }
                val schedule by scheduleFlow.collectAsState(initial = SourceRefreshSchedule.MANUAL)
                Text(
                    "${source.displayName}: ${schedule.displayName}",
                    color = OwnPlayColors.TextSecondary,
                )
                SourceRefreshSchedule.entries.chunked(2).forEach { options ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        options.forEach { option ->
                            TextButton(
                                enabled = option != schedule,
                                onClick = {
                                    scope.launch {
                                        onMessage(
                                            if (repository.setSchedule(source.sourceId, option)) {
                                                "Refresh schedule set to ${option.displayName}."
                                            } else {
                                                "Could not update refresh schedule."
                                            },
                                        )
                                    }
                                },
                            ) { Text(option.displayName) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisplayPreferencesSection(
    repository: DisplayPreferencesRepository,
    onMessage: (String) -> Unit,
) {
    val preferences by repository.preferences.collectAsState(initial = DisplayPreferences())
    val scope = rememberCoroutineScope()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = OwnPlayShapes.Medium,
        color = OwnPlayColors.SurfaceRaised,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Display", color = OwnPlayColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(
                if (preferences.compactMediaRows) "Compact media rows are on." else "Compact media rows are off.",
                color = OwnPlayColors.TextSecondary,
            )
            TextButton(
                onClick = {
                    val target = !preferences.compactMediaRows
                    scope.launch {
                        onMessage(
                            if (repository.setCompactMediaRows(target)) {
                                if (target) "Compact media rows enabled." else "Compact media rows disabled."
                            } else {
                                "Could not update display preference."
                            },
                        )
                    }
                },
            ) {
                Text(if (preferences.compactMediaRows) "Use standard rows" else "Use compact rows")
            }
        }
    }
}

@Composable
private fun LiveOrganizationSettingsSection(
    source: SourceSummary?,
    repository: LiveOrganizationRepository,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = OwnPlayShapes.Medium,
        color = OwnPlayColors.SurfaceRaised,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Live organization", color = OwnPlayColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            if (source == null) {
                Text("Add or select a source to configure Live organization.", color = OwnPlayColors.TextMuted)
            } else {
                val modeFlow = remember(repository, source.sourceId) { repository.observeMode(source.sourceId) }
                val catalogFlow = remember(repository, source.sourceId) { repository.observeOwnPlayCatalog(source.sourceId) }
                val mode by modeFlow.collectAsState(initial = LiveOrganizationMode.PROVIDER)
                val catalog by catalogFlow.collectAsState(
                    initial = OwnPlayLiveCatalogSnapshot(
                        countries = emptyList(),
                        semanticCategories = OwnPlayLiveSemanticCategory.canonicalOrder,
                        channelIdsByPlacement = emptyMap(),
                    ),
                )
                Text(
                    "${source.displayName}: ${if (mode == LiveOrganizationMode.OWNPLAY) "OwnPlay" else "Provider"}",
                    color = OwnPlayColors.TextSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        enabled = mode != LiveOrganizationMode.PROVIDER,
                        onClick = {
                            scope.launch {
                                onMessage(
                                    if (repository.setMode(source.sourceId, LiveOrganizationMode.PROVIDER)) {
                                        "Live organization set to Provider."
                                    } else {
                                        "Could not update Live organization."
                                    },
                                )
                            }
                        },
                    ) { Text("Provider") }
                    TextButton(
                        enabled = mode != LiveOrganizationMode.OWNPLAY,
                        onClick = {
                            scope.launch {
                                onMessage(
                                    if (repository.setMode(source.sourceId, LiveOrganizationMode.OWNPLAY)) {
                                        "Live organization set to OwnPlay."
                                    } else {
                                        "Could not update Live organization."
                                    },
                                )
                            }
                        },
                    ) { Text("OwnPlay") }
                }
                if (mode == LiveOrganizationMode.OWNPLAY) {
                    Text(
                        "Automatic organization: ${catalog.countries.size} country scopes • " +
                            "${OwnPlayLiveSemanticCategory.canonicalOrder.size} fixed categories each.",
                        color = OwnPlayColors.TextSecondary,
                    )
                    Text(
                        "Manual placement corrections: ${catalog.manualPlacementChannelIds.size}.",
                        color = OwnPlayColors.TextSecondary,
                    )
                    Text(
                        "Review, Move and Reset individual channels from Live. Manual placement remains separate from provider organization.",
                        color = OwnPlayColors.TextMuted,
                    )
                } else {
                    Text(
                        "Provider mode keeps provider categories and ordering. Switch to OwnPlay for country + fixed-category organization.",
                        color = OwnPlayColors.TextMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackSettingsSection(
    repository: PlaybackPreferencesRepository,
    onMessage: (String) -> Unit,
) {
    val preferences by repository.preferences.collectAsState(initial = PlaybackPreferences())
    val scope = rememberCoroutineScope()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = OwnPlayShapes.Medium,
        color = OwnPlayColors.SurfaceRaised,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Playback", color = OwnPlayColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(
                if (preferences.automaticPictureInPicture) {
                    "Automatic Picture-in-Picture is on."
                } else {
                    "Automatic Picture-in-Picture is off."
                },
                color = OwnPlayColors.TextSecondary,
            )
            Text(
                "When enabled, eligible fullscreen playback can enter Picture-in-Picture when you leave OwnPlay.",
                color = OwnPlayColors.TextMuted,
            )
            TextButton(
                onClick = {
                    val target = !preferences.automaticPictureInPicture
                    scope.launch {
                        onMessage(
                            if (repository.setAutomaticPictureInPicture(target)) {
                                if (target) {
                                    "Automatic Picture-in-Picture enabled."
                                } else {
                                    "Automatic Picture-in-Picture disabled."
                                }
                            } else {
                                "Could not update playback preference."
                            },
                        )
                    }
                },
            ) {
                Text(
                    if (preferences.automaticPictureInPicture) {
                        "Disable automatic PiP"
                    } else {
                        "Enable automatic PiP"
                    },
                )
            }
        }
    }
}

@Composable
private fun DownloadSettingsSection(
    repository: DownloadPreferencesRepository,
    onMessage: (String) -> Unit,
) {
    val preferences by repository.preferences.collectAsState(initial = DownloadPreferences())
    val scope = rememberCoroutineScope()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = OwnPlayShapes.Medium,
        color = OwnPlayColors.SurfaceRaised,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Downloads", color = OwnPlayColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(
                if (preferences.unmeteredNetworkOnly) {
                    "New and resumed downloads require an unmetered network."
                } else {
                    "New and resumed downloads can use any connected network."
                },
                color = OwnPlayColors.TextSecondary,
            )
            Text(
                "Changing this does not rewrite work that is already running.",
                color = OwnPlayColors.TextMuted,
            )
            TextButton(
                onClick = {
                    val target = !preferences.unmeteredNetworkOnly
                    scope.launch {
                        onMessage(
                            if (repository.setUnmeteredNetworkOnly(target)) {
                                if (target) {
                                    "Unmetered network requirement enabled for new and resumed downloads."
                                } else {
                                    "Downloads may use any connected network."
                                }
                            } else {
                                "Could not update download preference."
                            },
                        )
                    }
                },
            ) {
                Text(
                    if (preferences.unmeteredNetworkOnly) {
                        "Allow any connected network"
                    } else {
                        "Require unmetered network"
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsNextSections() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = OwnPlayShapes.Medium,
        color = OwnPlayColors.SurfaceRaised,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Next settings sections", color = OwnPlayColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(
                "Backup & restore • About",
                color = OwnPlayColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun XtreamSourceDialog(
    onDismiss: () -> Unit,
    onSubmit: (SourceInput.Xtream) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    SourceInputDialog(
        title = "Add Xtream source",
        onDismiss = onDismiss,
        onConfirm = { onSubmit(SourceInput.Xtream(name, server, username, password)) },
    ) {
        OutlinedTextField(name, { name = it }, label = { Text("Display name") }, singleLine = true)
        OutlinedTextField(server, { server = it }, label = { Text("Server URL") }, singleLine = true)
        OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true)
        OutlinedTextField(
            password,
            { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
    }
}

@Composable
private fun M3uSourceDialog(
    onDismiss: () -> Unit,
    onSubmit: (SourceInput.M3u) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var playlist by remember { mutableStateOf("") }
    var epg by remember { mutableStateOf("") }
    SourceInputDialog(
        title = "Add M3U source",
        onDismiss = onDismiss,
        onConfirm = { onSubmit(SourceInput.M3u(name, playlist, epg.trim().ifBlank { null })) },
    ) {
        OutlinedTextField(name, { name = it }, label = { Text("Display name") }, singleLine = true)
        OutlinedTextField(playlist, { playlist = it }, label = { Text("Playlist URL") }, singleLine = true)
        OutlinedTextField(epg, { epg = it }, label = { Text("EPG URL (optional)") }, singleLine = true)
    }
}

@Composable
private fun RenameSourceDialog(
    source: SourceSummary,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var name by remember(source.sourceId) { mutableStateOf(source.displayName) }
    SourceInputDialog(
        title = "Rename source",
        onDismiss = onDismiss,
        onConfirm = { onSubmit(name) },
    ) {
        OutlinedTextField(name, { name = it }, label = { Text("Display name") }, singleLine = true)
    }
}

@Composable
private fun SourceInputDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun mutationMessage(
    result: SourceMutationResult,
    success: String,
): String = when (result) {
    is SourceMutationResult.Success -> success
    is SourceMutationResult.Rejected -> when (result.reason) {
        SourceMutationRejection.INVALID_NAME -> "Enter a valid display name."
        SourceMutationRejection.INVALID_CONNECTION -> "Enter a valid source connection."
        SourceMutationRejection.INVALID_CREDENTIALS -> "Enter valid source credentials."
        SourceMutationRejection.DUPLICATE_SOURCE -> "That source connection is already configured."
        SourceMutationRejection.STORAGE_FAILURE -> "The source change could not be saved."
    }
}
