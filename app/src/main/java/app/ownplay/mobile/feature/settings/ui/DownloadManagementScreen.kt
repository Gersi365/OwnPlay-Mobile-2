package app.ownplay.mobile.feature.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.mobile.data.prefs.LibraryVisibilityPreferences
import app.ownplay.mobile.data.prefs.LibraryVisibilitySnapshot
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayModal
import app.ownplay.mobile.design.OwnPlayShapeTokens
import app.ownplay.mobile.design.OwnPlaySpacing
import app.ownplay.mobile.design.OwnPlayStatePanel
import app.ownplay.mobile.design.OwnPlayTopBar
import app.ownplay.mobile.downloads.domain.DownloadAction
import app.ownplay.mobile.downloads.domain.DownloadItem
import app.ownplay.mobile.downloads.domain.DownloadOperationResult
import app.ownplay.mobile.downloads.domain.DownloadRepository
import app.ownplay.mobile.downloads.domain.DownloadState
import app.ownplay.mobile.downloads.domain.DownloadStatePolicy
import app.ownplay.mobile.downloads.domain.OfflineAvailability
import app.ownplay.mobile.downloads.ui.rememberDownloadPermissionDispatcher
import app.ownplay.mobile.feature.library.data.LibraryDownloadEpisodeContext
import app.ownplay.mobile.feature.library.data.LibraryDownloadMetadataResolver
import app.ownplay.mobile.feature.library.domain.LibraryMediaKind
import app.ownplay.mobile.feature.library.domain.LibraryMediaMetadata
import app.ownplay.mobile.feature.library.ui.LibraryRemoteArtwork
import kotlinx.coroutines.launch

@Composable
fun DownloadManagementScreen(
    downloadRepository: DownloadRepository,
    downloadMetadataResolver: LibraryDownloadMetadataResolver,
    libraryVisibilityPreferences: LibraryVisibilityPreferences,
    onPlayOffline: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val downloadsFlow = remember(downloadRepository) { downloadRepository.observeDownloads() }
    val storedDownloads by downloadsFlow.collectAsState(initial = emptyList())
    val metadataOverrides = remember { mutableStateMapOf<String, LibraryMediaMetadata>() }
    val metadataBackfillAttempted = remember { mutableSetOf<String>() }
    val episodeContextByDownloadId = remember { mutableStateMapOf<String, LibraryDownloadEpisodeContext>() }
    val episodeContextAttempted = remember { mutableSetOf<String>() }
    val availabilityByDownloadId = remember { mutableStateMapOf<String, OfflineAvailability>() }
    val availabilityChecked = remember { mutableSetOf<String>() }
    val downloads = storedDownloads.map { item ->
        metadataOverrides[item.downloadId]?.let { metadata -> item.copy(metadata = metadata) } ?: item
    }
    val visibilityFlow = remember(libraryVisibilityPreferences) { libraryVisibilityPreferences.visibility }
    val visibility by visibilityFlow.collectAsState(initial = LibraryVisibilitySnapshot())
    val scope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingRemoval by remember { mutableStateOf<DownloadItem?>(null) }
    val permissionDispatcher = rememberDownloadPermissionDispatcher(
        onBlocked = { message -> errorMessage = message },
    )
    BackHandler(onBack = onBack)

    fun redownloadMissing(item: DownloadItem) {
        errorMessage = null
        permissionDispatcher(DownloadAction.DOWNLOAD) {
            scope.launch {
                errorMessage = when (val result = downloadRepository.redownload(item.downloadId)) {
                    is DownloadOperationResult.Failure -> result.safeMessage
                    is DownloadOperationResult.Success -> {
                        availabilityByDownloadId.remove(item.downloadId)
                        availabilityChecked.remove(item.downloadId)
                        null
                    }
                }
            }
        }
    }

    LaunchedEffect(storedDownloads) {
        val activeIds = storedDownloads.mapTo(mutableSetOf()) { it.downloadId }
        metadataOverrides.keys.toList().filterNot(activeIds::contains).forEach(metadataOverrides::remove)
        metadataBackfillAttempted.retainAll(activeIds)
        episodeContextByDownloadId.keys.toList().filterNot(activeIds::contains).forEach(episodeContextByDownloadId::remove)
        episodeContextAttempted.retainAll(activeIds)
        availabilityByDownloadId.keys.toList().filterNot(activeIds::contains).forEach(availabilityByDownloadId::remove)
        availabilityChecked.retainAll(activeIds)

        storedDownloads.forEach { item ->
            if (item.metadata != null) {
                metadataOverrides.remove(item.downloadId)
            } else if (metadataBackfillAttempted.add(item.downloadId)) {
                val metadata = downloadMetadataResolver.resolve(
                    sourceId = item.sourceId,
                    mediaKind = item.mediaKind,
                    contentId = item.contentId,
                )
                if (metadata != null) {
                    metadataOverrides[item.downloadId] = metadata
                    downloadRepository.saveMetadata(item.downloadId, metadata)
                }
            }

            if (item.state == DownloadState.COMPLETED) {
                if (availabilityChecked.add(item.downloadId)) {
                    availabilityByDownloadId[item.downloadId] = downloadRepository.offlineAvailability(item.downloadId)
                }
            } else {
                availabilityByDownloadId.remove(item.downloadId)
                availabilityChecked.remove(item.downloadId)
            }
        }

        storedDownloads
            .filter { item ->
                item.mediaKind == LibraryMediaKind.EPISODE && item.downloadId !in episodeContextAttempted
            }
            .groupBy { it.sourceId }
            .forEach { (sourceId, items) ->
                val contextByEpisodeId = downloadMetadataResolver.resolveEpisodeContexts(
                    sourceId = sourceId,
                    episodeIds = items.map { it.contentId },
                )
                items.forEach { item ->
                    episodeContextAttempted.add(item.downloadId)
                    contextByEpisodeId[item.contentId]?.let { context ->
                        episodeContextByDownloadId[item.downloadId] = context
                    }
                }
            }
    }

    pendingRemoval?.let { item ->
        OwnPlayModal(
            title = "Delete download?",
            message = "Delete ${item.title} from OwnPlay Downloads? This removes the offline file and its saved offline metadata.",
            confirmLabel = "Delete",
            dismissLabel = "Cancel",
            onConfirm = {
                pendingRemoval = null
                scope.launch {
                    errorMessage = when (val result = downloadRepository.remove(item.downloadId)) {
                        is DownloadOperationResult.Failure -> result.safeMessage
                        is DownloadOperationResult.Success -> {
                            libraryVisibilityPreferences.showDownload(item.downloadId)
                            null
                        }
                    }
                }
            },
            onDismiss = { pendingRemoval = null },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        OwnPlayTopBar(showTagline = false)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "‹ Settings",
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(vertical = 12.dp),
                style = MaterialTheme.typography.labelLarge,
                color = OwnPlayColors.Accent,
            )
            Text(
                "Downloaded Media",
                style = MaterialTheme.typography.headlineSmall,
                color = OwnPlayColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Offline titles on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = OwnPlayColors.TextSecondary,
            )
            Text(
                "Saved under Download/OwnPlay Downloads",
                style = MaterialTheme.typography.bodySmall,
                color = OwnPlayColors.TextMuted,
            )
        }

        errorMessage?.let { message ->
            OwnPlayStatePanel(
                title = "Download action unavailable",
                message = message,
                modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg),
            )
        }

        if (downloads.isEmpty()) {
            OwnPlayStatePanel(
                title = "No downloads",
                message = "Start a movie or episode download from Library and it will appear here.",
                modifier = Modifier.padding(OwnPlaySpacing.Lg),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = OwnPlaySpacing.Lg,
                    end = OwnPlaySpacing.Lg,
                    top = OwnPlaySpacing.Sm,
                    bottom = OwnPlaySpacing.Xl,
                ),
                horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
            ) {
                items(downloads, key = { it.downloadId }) { item ->
                    val offlineAvailability = availabilityByDownloadId[item.downloadId]
                    DownloadManagementCard(
                        item = item,
                        episodeContext = episodeContextByDownloadId[item.downloadId],
                        offlineAvailability = offlineAvailability,
                        hiddenFromLibrary = visibility.isDownloadHidden(item.downloadId),
                        onPrimary = {
                            errorMessage = null
                            if (
                                item.state == DownloadState.COMPLETED &&
                                offlineAvailability == OfflineAvailability.MISSING
                            ) {
                                redownloadMissing(item)
                            } else if (item.state == DownloadState.COMPLETED) {
                                scope.launch {
                                    when (val current = downloadRepository.offlineAvailability(item.downloadId)) {
                                        OfflineAvailability.AVAILABLE -> {
                                            availabilityByDownloadId[item.downloadId] = current
                                            onPlayOffline(item.downloadId)
                                        }
                                        OfflineAvailability.MISSING -> {
                                            availabilityByDownloadId[item.downloadId] = current
                                            errorMessage = "The offline file is missing. Download it again."
                                        }
                                        OfflineAvailability.INCOMPLETE -> {
                                            availabilityByDownloadId[item.downloadId] = current
                                            errorMessage = "The offline file is not ready yet."
                                        }
                                    }
                                }
                            } else {
                                val action = DownloadStatePolicy.primaryAction(item)
                                permissionDispatcher(action) { allowedAction ->
                                    when (allowedAction) {
                                        DownloadAction.PAUSE,
                                        DownloadAction.RESUME,
                                        DownloadAction.RETRY,
                                        -> scope.launch {
                                            errorMessage = when (val result = primaryAction(downloadRepository, item)) {
                                                is DownloadOperationResult.Failure -> result.safeMessage
                                                is DownloadOperationResult.Success -> null
                                            }
                                        }

                                        DownloadAction.PLAY_OFFLINE,
                                        DownloadAction.RESUME_OFFLINE,
                                        -> onPlayOffline(item.downloadId)

                                        DownloadAction.DOWNLOAD,
                                        DownloadAction.REMOVE,
                                        -> Unit
                                    }
                                }
                            }
                        },
                        onToggleLibraryVisibility = {
                            scope.launch {
                                if (visibility.isDownloadHidden(item.downloadId)) {
                                    libraryVisibilityPreferences.showDownload(item.downloadId)
                                } else {
                                    libraryVisibilityPreferences.hideDownload(item.downloadId)
                                }
                            }
                        },
                        onRemove = { pendingRemoval = item },
                    )
                }
            }
        }
    }
}

private suspend fun primaryAction(
    repository: DownloadRepository,
    item: DownloadItem,
): DownloadOperationResult = when (item.state) {
    DownloadState.DOWNLOADING, DownloadState.QUEUED -> repository.pause(item.downloadId)
    DownloadState.PAUSED -> repository.resume(item.downloadId)
    DownloadState.FAILED -> repository.retry(item.downloadId)
    DownloadState.COMPLETED -> DownloadOperationResult.Success(item)
}

@Composable
private fun DownloadManagementCard(
    item: DownloadItem,
    episodeContext: LibraryDownloadEpisodeContext?,
    offlineAvailability: OfflineAvailability?,
    hiddenFromLibrary: Boolean,
    onPrimary: () -> Unit,
    onToggleLibraryVisibility: () -> Unit,
    onRemove: () -> Unit,
) {
    val metadata = item.metadata
    val artwork = metadata?.posterUrl ?: metadata?.backdropUrl
    val displayTitle = metadata?.title?.takeIf { it.isNotBlank() } ?: item.title
    val contextLine = downloadContext(item, displayTitle, episodeContext)
    var menuExpanded by remember(item.downloadId) { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(OwnPlayShapeTokens.Medium)
                .background(OwnPlayColors.SurfaceElevated)
                .clickable(role = Role.Button, onClick = onPrimary),
        ) {
            LibraryRemoteArtwork(
                locator = artwork,
                contentDescription = "$displayTitle poster",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.52f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.94f),
                        ),
                    ),
            )
            if (item.state != DownloadState.COMPLETED) {
                item.progressFraction?.let { progress ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.White.copy(alpha = 0.18f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .height(3.dp)
                                .background(OwnPlayColors.Accent),
                        )
                    }
                }
            }
            Surface(
                onClick = { menuExpanded = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .width(48.dp)
                    .height(48.dp),
                color = Color.Black.copy(alpha = 0.58f),
                shape = OwnPlayShapeTokens.Action,
                tonalElevation = 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "⋯",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    if (item.state == DownloadState.COMPLETED) {
                        DropdownMenuItem(
                            text = { Text(if (hiddenFromLibrary) "Show in Library" else "Hide from Library") },
                            onClick = {
                                menuExpanded = false
                                onToggleLibraryVisibility()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete download") },
                        onClick = {
                            menuExpanded = false
                            onRemove()
                        },
                    )
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = downloadEyebrow(item),
                    style = MaterialTheme.typography.labelSmall,
                    color = OwnPlayColors.Accent,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
            }
        }

        contextLine?.let { context ->
            Text(
                text = context,
                style = MaterialTheme.typography.bodySmall,
                color = OwnPlayColors.TextMuted,
                maxLines = 1,
            )
        }
        Text(
            text = downloadStatus(item, hiddenFromLibrary, offlineAvailability),
            style = MaterialTheme.typography.labelSmall,
            color = if (
                item.state == DownloadState.COMPLETED &&
                offlineAvailability == OfflineAvailability.MISSING
            ) OwnPlayColors.AccentStrong else OwnPlayColors.TextSecondary,
            maxLines = 1,
        )
        DownloadManagementAction(
            text = primaryLabel(item, offlineAvailability),
            modifier = Modifier.fillMaxWidth(),
            onClick = onPrimary,
        )
    }
}

@Composable
private fun DownloadManagementAction(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        color = OwnPlayColors.SurfaceElevated,
        shape = OwnPlayShapeTokens.Small,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = OwnPlayColors.Accent,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

private fun primaryLabel(item: DownloadItem, availability: OfflineAvailability?): String =
    if (item.state == DownloadState.COMPLETED && availability == OfflineAvailability.MISSING) {
        "Download again"
    } else {
        when (DownloadStatePolicy.primaryAction(item)) {
            DownloadAction.PLAY_OFFLINE -> "Play offline"
            DownloadAction.RESUME_OFFLINE -> "Resume"
            DownloadAction.PAUSE -> "Pause"
            DownloadAction.RESUME -> "Resume"
            DownloadAction.RETRY -> "Retry"
            DownloadAction.DOWNLOAD -> "Download"
            DownloadAction.REMOVE -> "Delete"
        }
    }

private fun downloadEyebrow(item: DownloadItem): String {
    val kind = when (item.mediaKind) {
        LibraryMediaKind.MOVIE -> "MOVIE"
        LibraryMediaKind.EPISODE -> "EPISODE"
    }
    val state = when (item.state) {
        DownloadState.QUEUED -> "QUEUED"
        DownloadState.DOWNLOADING -> "DOWNLOADING"
        DownloadState.PAUSED -> "PAUSED"
        DownloadState.FAILED -> "NEEDS ATTENTION"
        DownloadState.COMPLETED -> null
    }
    return state?.let { "$kind • $it" } ?: kind
}

private fun downloadContext(
    item: DownloadItem,
    displayTitle: String,
    episodeContext: LibraryDownloadEpisodeContext?,
): String? {
    if (item.mediaKind != LibraryMediaKind.EPISODE) return null
    val seriesName = episodeContext?.seriesName
        ?.trim()
        ?.takeIf { it.isNotBlank() && !it.equals(displayTitle, ignoreCase = true) }
        ?: item.title
            .substringBefore(" • ", missingDelimiterValue = "")
            .trim()
            .takeIf { it.isNotBlank() && !it.equals(displayTitle, ignoreCase = true) }
    val episodeCode = episodeContext?.let { context ->
        "S${context.seasonNumber.toString().padStart(2, '0')}E${context.episodeNumber.toString().padStart(2, '0')}"
    } ?: EpisodeCodePattern.find(item.title)
        ?.value
        ?.uppercase()
    return listOfNotNull(seriesName, episodeCode)
        .joinToString(" • ")
        .takeIf { it.isNotBlank() }
}

private fun downloadStatus(
    item: DownloadItem,
    hiddenFromLibrary: Boolean,
    availability: OfflineAvailability?,
): String {
    if (item.state == DownloadState.COMPLETED) {
        return when (availability) {
            OfflineAvailability.MISSING -> "Offline file missing"
            OfflineAvailability.INCOMPLETE -> "Offline file not ready"
            OfflineAvailability.AVAILABLE,
            null,
            -> if (hiddenFromLibrary) "Available offline · hidden" else "Available offline"
        }
    }

    val progress = item.progressFraction?.let { " · ${(it * 100).toInt()}%" }.orEmpty()
    return when (item.state) {
        DownloadState.QUEUED -> "Queued$progress"
        DownloadState.DOWNLOADING -> "Downloading$progress"
        DownloadState.PAUSED -> "Paused$progress"
        DownloadState.FAILED -> "Needs attention"
        DownloadState.COMPLETED -> "Downloaded"
    }
}

private val EpisodeCodePattern = Regex("S\\d{1,2}E\\d{1,3}", RegexOption.IGNORE_CASE)
