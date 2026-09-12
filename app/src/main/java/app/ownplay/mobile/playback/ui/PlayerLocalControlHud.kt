package app.ownplay.mobile.playback.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayShapeTokens
import app.ownplay.mobile.design.OwnPlaySpacing
import app.ownplay.mobile.playback.domain.PlayerLocalControlPolicy
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest

internal enum class PlayerLocalControlHudKind {
    VOLUME,
    BRIGHTNESS,
}

internal data class PlayerLocalControlHudEvent(
    val kind: PlayerLocalControlHudKind,
    val level: Float,
)

object PlayerLocalControlHud {
    private val mutableEvents = MutableSharedFlow<PlayerLocalControlHudEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    internal val events = mutableEvents.asSharedFlow()

    fun showVolume(level: Float) {
        show(PlayerLocalControlHudKind.VOLUME, level)
    }

    fun showBrightness(level: Float) {
        show(PlayerLocalControlHudKind.BRIGHTNESS, level)
    }

    private fun show(kind: PlayerLocalControlHudKind, level: Float) {
        mutableEvents.tryEmit(
            PlayerLocalControlHudEvent(
                kind = kind,
                level = level.coerceIn(0f, PlayerLocalControlPolicy.MAX_LEVEL),
            ),
        )
    }
}

@Composable
fun PlayerLocalControlHudOverlay(modifier: Modifier = Modifier) {
    var event by remember { mutableStateOf<PlayerLocalControlHudEvent?>(null) }

    LaunchedEffect(Unit) {
        PlayerLocalControlHud.events.collectLatest { next ->
            event = next
            delay(1_200)
            event = null
        }
    }

    val visible = event ?: return
    Surface(
        modifier = modifier,
        color = OwnPlayColors.Surface.copy(alpha = 0.92f),
        shape = OwnPlayShapeTokens.Medium,
        border = BorderStroke(1.dp, OwnPlayColors.Divider),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 112.dp)
                .padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = when (visible.kind) {
                    PlayerLocalControlHudKind.VOLUME -> "VOLUME"
                    PlayerLocalControlHudKind.BRIGHTNESS -> "BRIGHTNESS"
                },
                style = MaterialTheme.typography.labelLarge,
                color = OwnPlayColors.TextSecondary,
            )
            Text(
                text = "${PlayerLocalControlPolicy.levelPercent(visible.level)}%",
                style = MaterialTheme.typography.headlineSmall,
                color = OwnPlayColors.TextPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
