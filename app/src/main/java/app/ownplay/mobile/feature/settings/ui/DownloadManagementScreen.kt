package app.ownplay.mobile.feature.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayModal
import app.ownplay.mobile.design.OwnPlayShapeTokens
import app.ownplay.mobile.design.OwnPlaySpacing
import app.ownplay.mobile.design.OwnPlayStatePanel
import app.ownplay.mobile.design.OwnPlayTopBar
import app.ownplay.mobile.downloads.domain.DownloadItem
import app.ownplay.mobile.downloads.domain.DownloadOperationResult
import app.ownplay.mobile.downloads.domain.DownloadRepository
import app.ownplay.mobile.downloads.domain.DownloadState
import kotlinx.coroutines.launch

@Composable
fun DownloadManagementScreen(
    downloadRepository: DownloadRepository,
    onPlayOffline: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val downloadsFlow = remember(downloadRepository) { downloadRepository.observeDownloads() }
    val downloads by downloadsFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingRemoval by remember { mutableStateOf<DownloadItem?>(null) }
    BackHandler(onBack = onBack)

    pendingRemoval?.let { item ->
        OwnPlayModal(
            title = "Remove download?",
            message = "Remove ${item.title} from this device and OwnPlay Downloads?",
            confirmLabel = "Remove",
            dismissLabel = "Cancel",
            onConfirm = {
                pendingRemoval = null
                scope.launch {
                    errorMessage = when (val result = downloadRepository.remove(item.downloadId)) {
                        is DownloadOperationResult.Failure -> result.safeMessage
                        is DownloadOperationResult.Success -> null
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
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(downloads, key = { it.downloadId }) { item ->
                    DownloadManagementRow(
                        item = item,
                        onPrimary = {
                            if (item.state == DownloadState.COMPLETED) {
                                errorMessage = null
                                onPlayOffline(item.downloadId)
                            } else {
                                scope.launch {
                                    errorMessage = when (val result = primaryAction(downloadRepository, item)) {
                                        is DownloadOperationResult.Failure -> result.safeMessage
                                        is DownloadOperationResult.Success -> null
                                    }
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
    onPrimary: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        shape = OwnPlayShapeTokens.Small,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(48.dp)
                    .background(
                        color = when (item.state) {
                            DownloadState.COMPLETED -> OwnPlayColors.Accent
                            DownloadState.FAILED -> OwnPlayColors.AccentStrong.copy(alpha = 0.72f)
                            else -> OwnPlayColors.Divider
                        },
                        shape = OwnPlayShapeTokens.Small,
                    ),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = OwnPlayColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
                Text(
                    text = downloadStatus(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.state == DownloadState.COMPLETED) {
                        OwnPlayColors.Accent
                    } else {
                        OwnPlayColors.TextSecondary
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DownloadManagementAction(
                        text = primaryLabel(item.state),
                        emphasized = item.state == DownloadState.COMPLETED ||
                            item.state == DownloadState.PAUSED ||
                            item.state == DownloadState.FAILED,
                        onClick = onPrimary,
                    )
                    DownloadManagementAction(
                        text = "Remove",
                        emphasized = false,
                        onClick = onRemove,
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadManagementAction(
    text: String,
    emphasized: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
        color = if (emphasized) OwnPlayColors.Accent.copy(alpha = 0.10f) else Color.Transparent,
        shape = OwnPlayShapeTokens.Small,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (emphasized) OwnPlayColors.Accent else OwnPlayColors.TextMuted,
                maxLines = 1,
            )
        }
    }
}

private fun primaryLabel(state: DownloadState): String = when (state) {
    DownloadState.DOWNLOADING, DownloadState.QUEUED -> "Pause"
    DownloadState.PAUSED -> "Resume"
    DownloadState.FAILED -> "Retry"
    DownloadState.COMPLETED -> "Play Offline"
}

private fun downloadStatus(item: DownloadItem): String {
    val progress = item.progressFraction?.let { " · ${(it * 100).toInt()}%" }.orEmpty()
    return when (item.state) {
        DownloadState.QUEUED -> "Queued$progress"
        DownloadState.DOWNLOADING -> "Downloading$progress"
        DownloadState.PAUSED -> "Paused$progress"
        DownloadState.FAILED -> "Needs attention"
        DownloadState.COMPLETED -> "Downloaded · verified offline"
    }
}
