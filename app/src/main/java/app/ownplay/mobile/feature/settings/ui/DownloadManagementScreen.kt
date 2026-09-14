package app.ownplay.mobile.feature.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
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
import app.ownplay.mobile.feature.library.data.LibraryDownloadMetadataResolver
import app.ownplay.mobile.feature.library.domain.LibraryMediaKind
import app.ownplay.mobile.feature.library.domain.LibraryMediaMetadata
import app.ownplay.mobile.feature.library.ui.LibraryRemoteArtwork
import app.ownplay.mobile.feature.library.ui.formatLibraryDuration
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
                "Manage downloads",
                style = MaterialTheme.typography.headlineSmall,
                color = OwnPlayColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Downloads are saved under Download/OwnPlay Downloads.",
                style = MaterialTheme.typography.bodyMedium,
                color = OwnPlayColors.TextSecondary,
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
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
            ) {
                items(downloads, key = { it.downloadId }) { item ->
                    val offlineAvailability = availabilityByDownloadId[item.downloadId]
                    DownloadManagementRow(
                        item = item,
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
private fun DownloadManagementRow(
    item: DownloadItem,
    offlineAvailability: OfflineAvailability?,
    hiddenFromLibrary: Boolean,
    onPrimary: () -> Unit,
    onToggleLibraryVisibility: () -> Unit,
    onRemove: () -> Unit,
) {
    val metadata = item.metadata
    val artwork = metadata?.posterUrl ?: metadata?.backdropUrl
    val displayTitle = metadata?.title?.takeIf { it.isNotBlank() } ?: item.title
    val contextLine = downloadContext(item, displayTitle)
    val factsLine = downloadFacts(item)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        shape = OwnPlayShapeTokens.Small,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
                verticalAlignment = Alignment.Top,
            ) {
                LibraryRemoteArtwork(
                    locator = artwork,
                    contentDescription = "$displayTitle poster",
                    modifier = Modifier
                        .width(82.dp)
                        .aspectRatio(2f / 3f)
                        .clip(OwnPlayShapeTokens.Small),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = downloadEyebrow(item),
                        style = MaterialTheme.typography.labelSmall,
                        color = when (item.state) {
                            DownloadState.FAILED -> OwnPlayColors.AccentStrong
                            else -> OwnPlayColors.Accent
                        },
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = OwnPlayColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                    )
                    contextLine?.let { context ->
                        Text(
                            text = context,
                            style = MaterialTheme.typography.bodySmall,
                            color = OwnPlayColors.TextSecondary,
                            maxLines = 2,
                        )
                    }
                    factsLine?.let { facts ->
                        Text(
                            text = facts,
                            style = MaterialTheme.typography.labelSmall,
                            color = OwnPlayColors.TextMuted,
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = downloadStatus(item, hiddenFromLibrary, offlineAvailability),
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            item.state == DownloadState.COMPLETED && offlineAvailability == OfflineAvailability.MISSING ->
                                OwnPlayColors.AccentStrong
                            item.state == DownloadState.COMPLETED -> OwnPlayColors.Accent
                            else -> OwnPlayColors.TextSecondary
                        },
                    )
                    item.progressFraction
                        ?.takeIf { item.state != DownloadState.COMPLETED }
                        ?.let { progress ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(OwnPlayColors.Divider, OwnPlayShapeTokens.Small),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progress)
                                        .height(2.dp)
                                        .background(OwnPlayColors.Accent, OwnPlayShapeTokens.Small),
                                )
                            }
                        }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DownloadManagementAction(
                    text = primaryLabel(item, offlineAvailability),
                    emphasized = true,
                    modifier = Modifier.weight(1f),
                    onClick = onPrimary,
                )
                if (item.state == DownloadState.COMPLETED) {
                    DownloadManagementAction(
                        text = if (hiddenFromLibrary) "Show" else "Hide",
                        emphasized = false,
                        modifier = Modifier.weight(1f),
                        onClick = onToggleLibraryVisibility,
                    )
                }
                DownloadManagementAction(
                    text = "Delete",
                    emphasized = false,
                    modifier = Modifier.weight(1f),
                    onClick = onRemove,
                )
            }
        }
    }
}

@Composable
private fun DownloadManagementAction(
    text: String,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        color = if (emphasized) OwnPlayColors.AccentStrong else OwnPlayColors.SurfaceElevated,
        shape = OwnPlayShapeTokens.Small,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (emphasized) Color.White else OwnPlayColors.TextSecondary,
                fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
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
        DownloadState.COMPLETED -> "OFFLINE"
    }
    return "$kind • $state"
}

private fun downloadContext(item: DownloadItem, displayTitle: String): String? {
    if (item.mediaKind != LibraryMediaKind.EPISODE) return null
    val seriesName = item.title
        .substringBefore(" • ", missingDelimiterValue = "")
        .trim()
        .takeIf { it.isNotBlank() && !it.equals(displayTitle, ignoreCase = true) }
    val episodeCode = EpisodeCodePattern.find(item.title)
        ?.value
        ?.uppercase()
    return listOfNotNull(seriesName, episodeCode)
        .joinToString(" • ")
        .takeIf { it.isNotBlank() }
}

private fun downloadFacts(item: DownloadItem): String? {
    val metadata = item.metadata ?: return null
    return buildList {
        metadata.releaseDate?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
        metadata.rating?.trim()?.takeIf { it.isNotBlank() }?.let { add("★ $it") }
        metadata.durationMs?.takeIf { it > 0L }?.let { add(formatLibraryDuration(it)) }
    }.joinToString(" • ").takeIf { it.isNotBlank() }
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
            -> if (hiddenFromLibrary) "Downloaded · hidden from Library" else "Downloaded"
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
