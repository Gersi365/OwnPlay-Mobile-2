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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayShapeTokens
import app.ownplay.mobile.design.OwnPlaySpacing
import app.ownplay.mobile.playback.domain.AudioTrackPolicy
import app.ownplay.mobile.playback.domain.PlaybackAudioTrack
import app.ownplay.mobile.playback.domain.PlaybackPhase
import app.ownplay.mobile.playback.domain.PlaybackResizeMode
import app.ownplay.mobile.playback.domain.PlaybackSnapshot
import app.ownplay.mobile.playback.domain.PlaybackSpeedPolicy
import app.ownplay.mobile.playback.domain.PlaybackStreamFormat
import app.ownplay.mobile.playback.domain.PlaybackSubtitleSelection
import app.ownplay.mobile.playback.domain.PlaybackSubtitleTrack
import app.ownplay.mobile.playback.domain.SubtitleTrackPolicy
import kotlin.math.abs

@Composable
fun PlaybackOptionsPanel(
    audioTracks: List<PlaybackAudioTrack>,
    subtitleTracks: List<PlaybackSubtitleTrack>,
    subtitleSelection: PlaybackSubtitleSelection,
    playbackSpeed: Float,
    resizeMode: PlaybackResizeMode,
    playbackSnapshot: PlaybackSnapshot,
    allowSpeed: Boolean,
    onSelectAudio: (String?) -> Unit,
    onSelectSubtitle: (PlaybackSubtitleSelection) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onSelectResizeMode: (PlaybackResizeMode) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)
    val streamRows = streamInfoRows(playbackSnapshot)

    Surface(
        modifier = modifier
            .fillMaxWidth(0.86f)
            .widthIn(max = 320.dp),
        color = Color.Black.copy(alpha = 0.84f),
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
                modifier = Modifier.heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs),
            ) {
                item(key = "picture-header") { OptionSectionLabel("Picture") }
                item(key = "picture-fit") {
                    PlaybackOptionRow(
                        title = "Fit",
                        detail = "Show the full frame",
                        selected = resizeMode == PlaybackResizeMode.FIT,
                        supported = true,
                        onClick = { onSelectResizeMode(PlaybackResizeMode.FIT) },
                    )
                }
                item(key = "picture-fill") {
                    PlaybackOptionRow(
                        title = "Fill",
                        detail = "Fill the screen; edges may crop",
                        selected = resizeMode == PlaybackResizeMode.FILL,
                        supported = true,
                        onClick = { onSelectResizeMode(PlaybackResizeMode.FILL) },
                    )
                }
                item(key = "picture-zoom") {
                    PlaybackOptionRow(
                        title = "Zoom",
                        detail = "Closer crop for letterboxed video",
                        selected = resizeMode == PlaybackResizeMode.ZOOM,
                        supported = true,
                        onClick = { onSelectResizeMode(PlaybackResizeMode.ZOOM) },
                    )
                }

                if (audioTracks.isNotEmpty()) {
                    item(key = "audio-header") { OptionSectionLabel("Audio") }
                    item(key = "audio-auto") {
                        PlaybackOptionRow(
                            title = "Auto",
                            detail = "Player default",
                            selected = audioTracks.none { it.selected },
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

                item(key = "stream-header") { OptionSectionLabel("Stream info") }
                items(
                    items = streamRows,
                    key = { row -> "stream-${row.label}" },
                ) { row ->
                    StreamInfoRow(row)
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
        fontWeight = FontWeight.SemiBold,
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

private data class StreamInfo(
    val label: String,
    val value: String,
)

@Composable
private fun StreamInfoRow(row: StreamInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OwnPlaySpacing.Sm, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.label,
            modifier = Modifier.weight(0.38f),
            style = MaterialTheme.typography.bodySmall,
            color = OwnPlayColors.TextMuted,
        )
        Text(
            text = row.value,
            modifier = Modifier.weight(0.62f),
            style = MaterialTheme.typography.bodySmall,
            color = OwnPlayColors.TextPrimary,
            maxLines = 2,
        )
    }
}

private fun streamInfoRows(snapshot: PlaybackSnapshot): List<StreamInfo> = listOf(
    StreamInfo(
        label = "Format",
        value = when (snapshot.streamFormat) {
            PlaybackStreamFormat.HLS -> "HLS"
            PlaybackStreamFormat.AUTO -> "Auto-detected"
            null -> diagnosticPendingValue(snapshot)
        },
    ),
    StreamInfo(
        label = "Video",
        value = buildVideoSummary(snapshot),
    ),
    StreamInfo(
        label = "Codec",
        value = buildCodecSummary(snapshot),
    ),
    StreamInfo(
        label = "Audio",
        value = buildAudioSummary(snapshot),
    ),
)

private fun buildVideoSummary(snapshot: PlaybackSnapshot): String {
    val parts = mutableListOf<String>()
    val width = snapshot.videoWidth
    val height = snapshot.videoHeight
    if (width != null && height != null) {
        parts += "${width}×${height}"
    }
    snapshot.videoFrameRate?.let { frameRate ->
        parts += "${formatFrameRate(frameRate)} fps"
    }
    return parts.joinToString(" • ").ifBlank { diagnosticPendingValue(snapshot) }
}

private fun buildCodecSummary(snapshot: PlaybackSnapshot): String {
    val parts = mutableListOf<String>()
    codecLabel(snapshot.videoCodecs, snapshot.videoMimeType)?.let(parts::add)
    snapshot.videoBitrate?.takeIf { it > 0 }?.let { bitrate ->
        parts += formatBitrate(bitrate)
    }
    return parts.joinToString(" • ").ifBlank { diagnosticPendingValue(snapshot) }
}

private fun buildAudioSummary(snapshot: PlaybackSnapshot): String {
    val parts = mutableListOf<String>()
    codecLabel(snapshot.audioCodecs, snapshot.audioMimeType)?.let(parts::add)
    snapshot.audioChannelCount?.takeIf { it > 0 }?.let { channels ->
        parts += "$channels ch"
    }
    snapshot.audioSampleRate?.takeIf { it > 0 }?.let { sampleRate ->
        val khz = sampleRate / 1_000f
        parts += if (abs(khz - khz.toInt()) < 0.01f) {
            "${khz.toInt()} kHz"
        } else {
            "${"%.1f".format(khz)} kHz"
        }
    }
    return parts.joinToString(" • ").ifBlank { diagnosticPendingValue(snapshot) }
}

private fun codecLabel(codecs: String?, mimeType: String?): String? =
    codecs?.trim()?.takeIf { it.isNotEmpty() }
        ?: mimeType?.substringAfter('/')?.uppercase()?.takeIf { it.isNotEmpty() }

private fun formatFrameRate(frameRate: Float): String =
    if (abs(frameRate - frameRate.toInt()) < 0.05f) {
        frameRate.toInt().toString()
    } else {
        "%.1f".format(frameRate)
    }

private fun formatBitrate(bitrate: Int): String = when {
    bitrate >= 1_000_000 -> "%.1f Mbps".format(bitrate / 1_000_000f)
    bitrate >= 1_000 -> "${bitrate / 1_000} kbps"
    else -> "$bitrate bps"
}

private fun diagnosticPendingValue(snapshot: PlaybackSnapshot): String = when (snapshot.phase) {
    PlaybackPhase.IDLE,
    PlaybackPhase.BUFFERING,
    -> "Detecting…"
    else -> "Unknown"
}
