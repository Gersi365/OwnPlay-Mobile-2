package app.ownplay.mobile.feature.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayShapeTokens
import app.ownplay.mobile.design.OwnPlaySpacing
import app.ownplay.mobile.downloads.domain.DownloadAction
import app.ownplay.mobile.downloads.domain.DownloadItem
import app.ownplay.mobile.downloads.domain.DownloadState
import app.ownplay.mobile.downloads.domain.DownloadStatePolicy
import kotlin.math.roundToInt

@Composable
internal fun LibraryOfflineControls(
    item: DownloadItem,
    onAction: (DownloadAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryAction = DownloadStatePolicy.primaryAction(item)
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
            horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs),
        ) {
            LibraryPrimaryAction(
                text = libraryOfflineActionLabel(primaryAction),
                onClick = { onAction(primaryAction) },
                modifier = Modifier.weight(1f),
                glyph = if (
                    primaryAction == DownloadAction.PLAY_OFFLINE ||
                    primaryAction == DownloadAction.RESUME_OFFLINE
                ) {
                    LibraryActionGlyph.PLAY
                } else {
                    null
                },
            )
            LibrarySecondaryAction(
                text = "Remove",
                onClick = { onAction(DownloadAction.REMOVE) },
                modifier = Modifier.weight(0.66f),
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
