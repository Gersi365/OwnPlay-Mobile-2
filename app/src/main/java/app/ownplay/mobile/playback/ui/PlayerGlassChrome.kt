package app.ownplay.mobile.playback.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayShapeTokens
import app.ownplay.mobile.design.OwnPlaySpacing

@Composable
fun PlayerGlassScrims(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
  modifier = Modifier
      .align(Alignment.TopCenter)
      .fillMaxWidth()
      .height(136.dp)
      .background(
Brush.verticalGradient(
    colors = listOf(
        Color.Black.copy(alpha = 0.72f),
        Color.Transparent,
    ),
),
      ),
        )
        Box(
  modifier = Modifier
      .align(Alignment.BottomCenter)
      .fillMaxWidth()
      .height(260.dp)
      .background(
Brush.verticalGradient(
    colors = listOf(
        Color.Transparent,
        Color.Black.copy(alpha = 0.82f),
    ),
),
      ),
        )
    }
}

private fun playerGlassAccessibilityLabel(label: String): String = when (label) {
    "‹" -> "Back"
    "−10" -> "Rewind 10 seconds"
    "Ⅱ" -> "Pause"
    "▶" -> "Play"
    "+10" -> "Forward 10 seconds"
    else -> label
}

@Composable
fun PlayerGlassCircleAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val size = if (emphasized) 68.dp else 54.dp
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(size)
            .semantics {
                contentDescription = playerGlassAccessibilityLabel(label)
            },
        shape = CircleShape,
        color = if (emphasized) {
  OwnPlayColors.AccentStrong.copy(alpha = 0.90f)
        } else {
  OwnPlayColors.Background.copy(alpha = 0.56f)
        },
        border = BorderStroke(
  width = 1.dp,
  color = if (emphasized) {
      Color.White.copy(alpha = 0.20f)
  } else {
      Color.White.copy(alpha = 0.14f)
  },
        ),
        tonalElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
  Text(
      text = label,
      modifier = Modifier.clearAndSetSemantics { },
      style = if (emphasized) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge,
      color = Color.White,
      fontWeight = FontWeight.SemiBold,
  )
        }
    }
}

@Composable
fun PlayerGlassPillAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = OwnPlayShapeTokens.Action,
        color = if (emphasized) {
  OwnPlayColors.Accent.copy(alpha = 0.22f)
        } else {
  OwnPlayColors.Background.copy(alpha = 0.52f)
        },
        border = BorderStroke(
  width = 1.dp,
  color = if (emphasized) {
      OwnPlayColors.Accent.copy(alpha = 0.64f)
  } else {
      Color.White.copy(alpha = 0.12f)
  },
        ),
        tonalElevation = 0.dp,
    ) {
        Text(
  text = text,
  modifier = Modifier.padding(
      horizontal = OwnPlaySpacing.Md,
      vertical = OwnPlaySpacing.Sm,
  ),
  style = MaterialTheme.typography.labelLarge,
  color = if (emphasized) OwnPlayColors.Accent else Color.White,
  fontWeight = FontWeight.Medium,
        )
    }
}
