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
import androidx.compose.ui.graphics.Color
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
        modifier = modifier.width(280.dp),
        color = Color.Black.copy(alpha = 0.74f),
        shape = OwnPlayShapeTokens.Medium,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
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
                    text = "Audio",
                    style = MaterialTheme.typography.titleMedium,
                    color = OwnPlayColors.TextPrimary,
                )
                Text(
                    text = "Done",
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = OwnPlaySpacing.Sm, vertical = OwnPlaySpacing.Xs),
                    style = MaterialTheme.typography.labelLarge,
                    color = OwnPlayColors.Accent,
                )
            }

            LazyColumn(
                modifier = Modifier.heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs),
            ) {
                item(key = "auto") {
                    AudioTrackRow(
                        title = "Auto",
                        detail = "Player default",
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
        color = if (selected) {
            OwnPlayColors.Accent.copy(alpha = 0.14f)
        } else {
            Color.White.copy(alpha = 0.025f)
        },
        shape = OwnPlayShapeTokens.Small,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) OwnPlayColors.Accent.copy(alpha = 0.40f) else Color.Transparent,
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
                verticalArrangement = Arrangement.spacedBy(1.dp),
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
                    maxLines = 1,
                )
            }
            if (selected) {
                Text(
                    text = "●",
                    style = MaterialTheme.typography.labelMedium,
                    color = OwnPlayColors.Accent,
                )
            }
        }
    }
}
