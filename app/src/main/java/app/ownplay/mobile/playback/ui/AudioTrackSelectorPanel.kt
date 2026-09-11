package app.ownplay.mobile.playback.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayShapeTokens
import app.ownplay.mobile.design.OwnPlaySpacing
import app.ownplay.mobile.playback.domain.AudioTrackPolicy
import app.ownplay.mobile.playback.domain.PlaybackAudioTrack

@Composable
fun AudioTrackSelectorPanel(
    tracks: List<PlaybackAudioTrack>,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.width(340.dp),
        color = OwnPlayColors.Surface.copy(alpha = 0.97f),
        shape = OwnPlayShapeTokens.Medium,
        border = BorderStroke(1.dp, OwnPlayColors.Divider),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(OwnPlaySpacing.Md),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "AUDIO",
                    style = MaterialTheme.typography.titleMedium,
                    color = OwnPlayColors.TextPrimary,
                )
                Text(
                    text = "CLOSE",
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(OwnPlaySpacing.Sm),
                    style = MaterialTheme.typography.labelLarge,
                    color = OwnPlayColors.Accent,
                )
            }

            LazyColumn(
                modifier = Modifier.heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs),
            ) {
                item(key = "auto") {
                    AudioTrackRow(
                        title = "Auto",
                        detail = "Use player default selection",
                        selected = false,
                        supported = true,
                        onClick = { onSelect(null) },
                    )
                }
                itemsIndexed(
                    items = tracks,
                    key = { _, track -> track.selectionId },
                ) { index, track ->
                    AudioTrackRow(
                        title = AudioTrackPolicy.primaryLabel(track, index + 1),
                        detail = AudioTrackPolicy.detailLabel(track),
                        selected = track.selected,
                        supported = track.supported,
                        onClick = { onSelect(track.selectionId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioTrackRow(
    title: String,
    detail: String,
    selected: Boolean,
    supported: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = supported, onClick = onClick),
        color = if (selected) OwnPlayColors.SurfaceSelected else OwnPlayColors.SurfaceElevated,
        shape = OwnPlayShapeTokens.Small,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) OwnPlayColors.Accent else OwnPlayColors.Divider,
        ),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = OwnPlaySpacing.Md, vertical = OwnPlaySpacing.Sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (supported) OwnPlayColors.TextPrimary else OwnPlayColors.TextSecondary,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = OwnPlayColors.TextSecondary,
                )
            }
            if (selected) {
                Text(
                    text = "SELECTED",
                    style = MaterialTheme.typography.labelSmall,
                    color = OwnPlayColors.Accent,
                )
            }
        }
    }
}
