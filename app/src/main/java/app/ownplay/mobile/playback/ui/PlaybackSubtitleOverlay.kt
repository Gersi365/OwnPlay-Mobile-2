package app.ownplay.mobile.playback.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ownplay.mobile.design.OwnPlayShapeTokens
import app.ownplay.mobile.playback.domain.PlaybackSubtitleCue

@Composable
fun PlaybackSubtitleOverlay(
    cues: List<PlaybackSubtitleCue>,
    modifier: Modifier = Modifier,
) {
    if (cues.isEmpty()) return
    val text = cues.joinToString(separator = "\n") { cue -> cue.text }.trim()
    if (text.isEmpty()) return

    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.66f),
        shape = OwnPlayShapeTokens.Small,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}
