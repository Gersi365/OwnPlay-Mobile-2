package app.ownplay.mobile.playback.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import app.ownplay.mobile.playback.domain.PlaybackSpeedPolicy
import app.ownplay.mobile.playback.domain.PlaybackSubtitleSelection
import app.ownplay.mobile.playback.domain.PlaybackSubtitleTrack
import app.ownplay.mobile.playback.domain.SubtitleTrackPolicy

@Composable
fun PlaybackOptionsPanel(
    audioTracks: List<PlaybackAudioTrack>,
    subtitleTracks: List<PlaybackSubtitleTrack>,
    subtitleSelection: PlaybackSubtitleSelection,
    playbackSpeed: Float,
    allowSpeed: Boolean,
    onSelectAudio: (String?) -> Unit,
    onSelectSubtitle: (PlaybackSubtitleSelection) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)

    Surface(
        modifier = modifier
            .fillMaxWidth(0.86f)
            .widthIn(max = 320.dp),
        color = Color.Black.copy(alpha = 0.82f),
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
                    text = "Options",
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
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs),
            ) {
                if (audioTracks.isNotEmpty()) {
                    item(key = "audio-header") { OptionSectionLabel("Audio") }
                    item(key = "audio-auto") {
                        PlaybackOptionRow(
                            title = "Auto",
                            detail = "Player default",
                            selected = false,
                            supported = true,
                            onClick = { onSelectAudio(null) },
                        )
                    }
                    itemsIndexed(
                        items = audioTracks,
                        key = { _, track -> "audio-${track.selectionId}" },
                    ) { index, track ->
                        PlaybackOptionRow(
                            title = AudioTrackPolicy.primaryLabel(track, index + 1),
                            detail = AudioTrackPolicy.detailLabel(track),
                            selected = track.selected,
                            supported = track.supported,
                            onClick = { onSelectAudio(track.selectionId) },
                        )
                    }
                }

                if (subtitleTracks.isNotEmpty()) {
                    item(key = "subtitle-header") { OptionSectionLabel("Subtitles & CC") }
                    item(key = "subtitle-off") {
                        PlaybackOptionRow(
                            title = "Off",
                            detail = "Hide captions",
                            selected = subtitleSelection == PlaybackSubtitleSelection.Off,
                            supported = true,
                            onClick = { onSelectSubtitle(PlaybackSubtitleSelection.Off) },
                        )
                    }
                    item(key = "subtitle-auto") {
                        PlaybackOptionRow(
                            title = "Auto",
                            detail = "Use stream defaults and forced captions",
                            selected = subtitleSelection == PlaybackSubtitleSelection.Auto,
                            supported = true,
                            onClick = { onSelectSubtitle(PlaybackSubtitleSelection.Auto) },
                        )
                    }
                    itemsIndexed(
                        items = subtitleTracks,
                        key = { _, track -> "subtitle-${track.selectionId}" },
                    ) { index, track ->
                        PlaybackOptionRow(
                            title = SubtitleTrackPolicy.primaryLabel(track, index + 1),
                            detail = SubtitleTrackPolicy.detailLabel(track),
                            selected = subtitleSelection is PlaybackSubtitleSelection.Track &&
                                subtitleSelection.selectionId == track.selectionId,
                            supported = track.supported,
                            onClick = {
                                onSelectSubtitle(PlaybackSubtitleSelection.Track(track.selectionId))
                            },
                        )
                    }
                }

                if (allowSpeed) {
                    item(key = "speed-header") { OptionSectionLabel("Playback speed") }
                    items(
                        count = PlaybackSpeedPolicy.supportedSpeeds.size,
                        key = { index -> "speed-${PlaybackSpeedPolicy.supportedSpeeds[index]}" },
                    ) { index ->
                        val speed = PlaybackSpeedPolicy.supportedSpeeds[index]
                        PlaybackOptionRow(
                            title = PlaybackSpeedPolicy.label(speed),
                            detail = if (speed == 1f) "Normal" else "Video on demand only",
                            selected = PlaybackSpeedPolicy.normalize(playbackSpeed) == speed,
                            supported = true,
                            onClick = { onSelectSpeed(speed) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionSectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = OwnPlaySpacing.Xs, bottom = 2.dp),
        style = MaterialTheme.typography.labelMedium,
        color = OwnPlayColors.Accent,
    )
}

@Composable
private fun PlaybackOptionRow(
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
