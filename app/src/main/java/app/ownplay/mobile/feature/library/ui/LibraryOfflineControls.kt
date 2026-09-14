package app.ownplay.mobile.feature.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayShapeTokens
import app.ownplay.mobile.downloads.domain.DownloadAction
import app.ownplay.mobile.downloads.domain.DownloadItem
import app.ownplay.mobile.downloads.domain.DownloadState
import app.ownplay.mobile.downloads.domain.DownloadStatePolicy
import kotlin.math.roundToInt

@Composable
internal fun LibraryOfflineControls(
    item: DownloadItem,
    onAction: (DownloadAction) -> Unit,
    onHideFromLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryAction = DownloadStatePolicy.primaryAction(item)
    var moreMenuVisible by remember(item.downloadId) { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = libraryOfflineStatus(item),
            style = MaterialTheme.typography.bodySmall,
            color = if (item.state == DownloadState.FAILED) {
                OwnPlayColors.TextSecondary
            } else {
                OwnPlayColors.TextMuted
            },
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        item.progressFraction?.let { progress ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        OwnPlayColors.Divider.copy(alpha = 0.74f),
                        shape = OwnPlayShapeTokens.Small,
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(2.dp)
                        .background(OwnPlayColors.Accent, shape = OwnPlayShapeTokens.Small),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OfflineRailAction(
                glyph = libraryOfflineActionGlyph(primaryAction),
                contentDescription = libraryOfflineActionLabel(primaryAction),
                emphasized = true,
                onClick = { onAction(primaryAction) },
            )
            OfflineRailAction(
                glyph = "✓",
                contentDescription = "Hide from Library",
                onClick = onHideFromLibrary,
            )
            Box {
                OfflineRailAction(
                    glyph = "⋮",
                    contentDescription = "More download actions",
                    onClick = { moreMenuVisible = true },
                )
                DropdownMenu(
                    expanded = moreMenuVisible,
                    onDismissRequest = { moreMenuVisible = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Remove download") },
                        onClick = {
                            moreMenuVisible = false
                            onAction(DownloadAction.REMOVE)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OfflineRailAction(
    glyph: String,
    contentDescription: String,
    onClick: () -> Unit,
    emphasized: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .semantics { this.contentDescription = contentDescription },
        color = Color.Transparent,
        shape = OwnPlayShapeTokens.Action,
        tonalElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = glyph,
                style = MaterialTheme.typography.titleMedium,
                color = if (emphasized) OwnPlayColors.Accent else OwnPlayColors.TextSecondary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

private fun libraryOfflineStatus(item: DownloadItem): String = when (item.state) {
    DownloadState.QUEUED -> "Preparing offline file"
    DownloadState.DOWNLOADING -> item.progressFraction?.let { progress ->
        "Downloading ${(progress * 100f).roundToInt()}%"
    } ?: "Downloading"

    DownloadState.PAUSED -> "Download paused"
    DownloadState.FAILED -> "Download needs attention"
    DownloadState.COMPLETED -> if (item.resumePositionMs != null) {
        "Ready offline • resume available"
    } else {
        "Ready offline"
    }
}

private fun libraryOfflineActionLabel(action: DownloadAction): String = when (action) {
    DownloadAction.DOWNLOAD -> "Download"
    DownloadAction.PAUSE -> "Pause"
    DownloadAction.RESUME -> "Resume"
    DownloadAction.RETRY -> "Retry"
    DownloadAction.REMOVE -> "Remove"
    DownloadAction.PLAY_OFFLINE -> "Play"
    DownloadAction.RESUME_OFFLINE -> "Resume"
}

private fun libraryOfflineActionGlyph(action: DownloadAction): String = when (action) {
    DownloadAction.DOWNLOAD -> "↓"
    DownloadAction.PAUSE -> "Ⅱ"
    DownloadAction.RESUME,
    DownloadAction.PLAY_OFFLINE,
    DownloadAction.RESUME_OFFLINE,
    -> "▶"
    DownloadAction.RETRY -> "↻"
    DownloadAction.REMOVE -> "×"
}
