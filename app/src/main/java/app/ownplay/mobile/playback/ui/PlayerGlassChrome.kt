package app.ownplay.mobile.playback.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
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
                .height(96.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.58f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(176.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.72f),
                        ),
                    ),
                ),
        )
    }
}

enum class PlayerGlassGlyph {
    BACK,
    REWIND_10,
    PLAY,
    PAUSE,
    FORWARD_10,
}

@Composable
fun PlayerGlassIconAction(
    glyph: PlayerGlassGlyph,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val touchSize = if (emphasized) 56.dp else 48.dp
    val visualSize = if (emphasized) 50.dp else 40.dp
    Box(
        modifier = modifier
            .size(touchSize)
            .semantics { this.contentDescription = contentDescription }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(visualSize),
            shape = CircleShape,
            color = if (emphasized) {
                OwnPlayColors.AccentStrong.copy(alpha = 0.86f)
            } else {
                Color.Black.copy(alpha = 0.34f)
            },
            border = BorderStroke(
                width = 1.dp,
                color = if (emphasized) {
                    Color.White.copy(alpha = 0.18f)
                } else {
                    Color.White.copy(alpha = 0.10f)
                },
            ),
            tonalElevation = 0.dp,
        ) {
            PlayerGlassGlyphContent(glyph = glyph, emphasized = emphasized)
        }
    }
}

@Composable
private fun PlayerGlassGlyphContent(glyph: PlayerGlassGlyph, emphasized: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        when (glyph) {
            PlayerGlassGlyph.REWIND_10 -> Text(
                text = "−10",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )

            PlayerGlassGlyph.FORWARD_10 -> Text(
                text = "+10",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )

            else -> Canvas(modifier = Modifier.size(if (emphasized) 24.dp else 20.dp)) {
                val strokeWidth = if (emphasized) 2.4.dp.toPx() else 2.dp.toPx()
                when (glyph) {
                    PlayerGlassGlyph.BACK -> {
                        drawLine(
                            color = Color.White,
                            start = Offset(size.width * 0.64f, size.height * 0.18f),
                            end = Offset(size.width * 0.34f, size.height * 0.50f),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round,
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(size.width * 0.34f, size.height * 0.50f),
                            end = Offset(size.width * 0.64f, size.height * 0.82f),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round,
                        )
                    }

                    PlayerGlassGlyph.PLAY -> {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(size.width * 0.38f, size.height * 0.24f)
                            lineTo(size.width * 0.38f, size.height * 0.76f)
                            lineTo(size.width * 0.76f, size.height * 0.50f)
                            close()
                        }
                        drawPath(path = path, color = Color.White)
                    }

                    PlayerGlassGlyph.PAUSE -> {
                        val barWidth = size.width * 0.15f
                        val barHeight = size.height * 0.52f
                        val top = size.height * 0.24f
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(size.width * 0.31f, top),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barWidth * 0.20f),
                        )
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(size.width * 0.55f, top),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barWidth * 0.20f),
                        )
                    }

                    PlayerGlassGlyph.REWIND_10,
                    PlayerGlassGlyph.FORWARD_10,
                    -> Unit
                }
            }
        }
    }
}

@Composable
fun PlayerGlassSeekBar(
    fraction: Float,
    onFractionChange: (Float) -> Unit,
    onChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    val safeFraction = fraction.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(widthPx) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun updateAt(x: Float) {
                        onFractionChange((x / widthPx).coerceIn(0f, 1f))
                    }
                    updateAt(down.position.x)
                    down.consume()
                    var pressed = true
                    while (pressed) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        pressed = change.pressed
                        if (pressed) {
                            updateAt(change.position.x)
                            change.consume()
                        }
                    }
                    onChangeFinished()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackHeight = 3.dp.toPx()
            val y = center.y
            val radius = trackHeight / 2f
            drawRoundRect(
                color = Color.White.copy(alpha = 0.24f),
                topLeft = Offset(0f, y - radius),
                size = Size(size.width, trackHeight),
                cornerRadius = CornerRadius(radius),
            )
            val activeWidth = size.width * safeFraction
            if (activeWidth > 0f) {
                drawRoundRect(
                    color = OwnPlayColors.Accent,
                    topLeft = Offset(0f, y - radius),
                    size = Size(activeWidth, trackHeight),
                    cornerRadius = CornerRadius(radius),
                )
            }
            drawCircle(
                color = OwnPlayColors.Accent,
                radius = 4.5.dp.toPx(),
                center = Offset(activeWidth.coerceIn(0f, size.width), y),
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
        modifier = modifier.heightIn(min = 40.dp),
        shape = OwnPlayShapeTokens.Action,
        color = if (emphasized) {
            OwnPlayColors.Accent.copy(alpha = 0.18f)
        } else {
            Color.Black.copy(alpha = 0.34f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (emphasized) {
                OwnPlayColors.Accent.copy(alpha = 0.48f)
            } else {
                Color.White.copy(alpha = 0.10f)
            },
        ),
        tonalElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = 14.dp,
                vertical = OwnPlaySpacing.Sm,
            ),
            style = MaterialTheme.typography.labelLarge,
            color = if (emphasized) OwnPlayColors.Accent else Color.White,
            fontWeight = FontWeight.Medium,
        )
    }
}
