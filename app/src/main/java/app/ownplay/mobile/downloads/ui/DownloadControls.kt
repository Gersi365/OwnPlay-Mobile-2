package app.ownplay.mobile.downloads.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayPrimaryButton
import app.ownplay.mobile.design.OwnPlaySecondaryButton
import app.ownplay.mobile.design.OwnPlaySpacing
import app.ownplay.mobile.downloads.domain.DownloadAction
import app.ownplay.mobile.downloads.domain.DownloadItem
import app.ownplay.mobile.downloads.domain.DownloadState
import app.ownplay.mobile.downloads.domain.DownloadStatePolicy
import kotlin.math.roundToInt

@Composable
fun DownloadControls(
    item: DownloadItem?,
    onAction: (DownloadAction) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val primaryAction = DownloadStatePolicy.primaryAction(item)
    Column(
        modifier = if (compact) modifier else modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) OwnPlaySpacing.Xs else OwnPlaySpacing.Sm),
    ) {
        if (item != null) {
            Text(
                text = statusLabel(item),
                style = MaterialTheme.typography.bodyMedium,
                color = if (item.state == DownloadState.FAILED) OwnPlayColors.TextSecondary else OwnPlayColors.Accent,
            )
            item.progressFraction?.let { progress ->
                Box(
                    modifier = (if (compact) Modifier.width(160.dp) else Modifier.fillMaxWidth())
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(OwnPlayColors.Divider),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .background(OwnPlayColors.Accent),
                    )
                }
            }
        }

        Row(
            modifier = if (compact) Modifier else Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (compact) OwnPlaySpacing.Xs else OwnPlaySpacing.Sm),
        ) {
            if (compact) {
                if (primaryAction == DownloadAction.DOWNLOAD) {
                    OwnPlaySecondaryButton(
                        text = actionLabel(primaryAction),
                        onClick = { onAction(primaryAction) },
                    )
                } else {
                    OwnPlayPrimaryButton(
                        text = actionLabel(primaryAction),
                        onClick = { onAction(primaryAction) },
                    )
                }
                if (item != null) {
                    OwnPlaySecondaryButton(
                        text = "Remove",
                        onClick = { onAction(DownloadAction.REMOVE) },
                    )
                }
            } else {
                OwnPlayPrimaryButton(
                    text = actionLabel(primaryAction),
                    onClick = { onAction(primaryAction) },
                    modifier = Modifier.weight(1f),
                )
                if (item != null) {
                    OwnPlaySecondaryButton(
                        text = "Remove",
                        onClick = { onAction(DownloadAction.REMOVE) },
                        modifier = Modifier.weight(0.7f),
                    )
                }
            }
        }
    }
}

private fun statusLabel(item: DownloadItem): String = when (item.state) {
    DownloadState.QUEUED -> "Queued for download"
    DownloadState.DOWNLOADING -> item.progressFraction?.let { progress ->
        "Downloading ${(progress * 100f).roundToInt()}%"
    } ?: "Downloading ${formatBytes(item.bytesDownloaded)}"

    DownloadState.PAUSED -> "Download paused • ${formatBytes(item.bytesDownloaded)} saved"
    DownloadState.FAILED -> "Download needs attention"
    DownloadState.COMPLETED -> if (item.resumePositionMs != null) {
        "Downloaded • offline resume available"
    } else {
        "Downloaded • verified for offline playback"
    }
}

private fun actionLabel(action: DownloadAction): String = when (action) {
    DownloadAction.DOWNLOAD -> "Download"
    DownloadAction.PAUSE -> "Pause"
    DownloadAction.RESUME -> "Resume Download"
    DownloadAction.RETRY -> "Retry"
    DownloadAction.REMOVE -> "Remove"
    DownloadAction.PLAY_OFFLINE -> "Play Offline"
    DownloadAction.RESUME_OFFLINE -> "Resume Offline"
}

private fun formatBytes(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    val mebibytes = safeBytes.toDouble() / (1024.0 * 1024.0)
    return if (mebibytes >= 1.0) {
        "%.1f MB".format(mebibytes)
    } else {
        "${safeBytes / 1024L} KB"
    }
}
