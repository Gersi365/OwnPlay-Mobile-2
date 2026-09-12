package app.ownplay.mobile.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlaySpacing

@Composable
fun OwnPlayBottomBar(
    selectedDestination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars),
        color = OwnPlayColors.Background,
        tonalElevation = 0.dp,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(OwnPlayColors.Divider.copy(alpha = 0.56f)),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = OwnPlaySpacing.Md),
            ) {
                primaryDestinations.forEach { destination ->
                    val selected = destination == selectedDestination
                    val contentColor = if (selected) OwnPlayColors.Accent else OwnPlayColors.TextMuted
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .semantics { this.selected = selected }
                            .clickable(role = Role.Tab) { onDestinationSelected(destination) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        OwnPlayNavIcon(
                            destination = destination,
                            color = contentColor,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = contentColor,
                        )
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .width(30.dp)
                                .height(2.dp)
                                .background(
                                    color = if (selected) OwnPlayColors.Accent else Color.Transparent,
                                    shape = RoundedCornerShape(2.dp),
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OwnPlayNavIcon(
    destination: AppDestination,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val stroke = 1.8.dp.toPx()
        when (destination) {
            AppDestination.Live -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.16f, size.height * 0.24f),
                    size = Size(size.width * 0.68f, size.height * 0.55f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.5.dp.toPx()),
                    style = Stroke(width = stroke),
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.36f, size.height * 0.19f),
                    end = Offset(size.width * 0.27f, size.height * 0.08f),
                    strokeWidth = stroke,
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.64f, size.height * 0.19f),
                    end = Offset(size.width * 0.73f, size.height * 0.08f),
                    strokeWidth = stroke,
                )
            }

            AppDestination.Library -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.18f, size.height * 0.28f),
                    size = Size(size.width * 0.64f, size.height * 0.50f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                    style = Stroke(width = stroke),
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.27f, size.height * 0.18f),
                    end = Offset(size.width * 0.73f, size.height * 0.18f),
                    strokeWidth = stroke,
                )
                val play = Path().apply {
                    moveTo(size.width * 0.43f, size.height * 0.41f)
                    lineTo(size.width * 0.43f, size.height * 0.65f)
                    lineTo(size.width * 0.62f, size.height * 0.53f)
                    close()
                }
                drawPath(play, color = color)
            }

            AppDestination.Settings -> {
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.30f,
                    style = Stroke(width = stroke),
                )
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.11f,
                    style = Stroke(width = stroke),
                )
                val armStart = size.minDimension * 0.33f
                val armEnd = size.minDimension * 0.45f
                listOf(
                    Offset(0f, -1f),
                    Offset(1f, 0f),
                    Offset(0f, 1f),
                    Offset(-1f, 0f),
                ).forEach { direction ->
                    drawLine(
                        color = color,
                        start = Offset(
                            center.x + direction.x * armStart,
                            center.y + direction.y * armStart,
                        ),
                        end = Offset(
                            center.x + direction.x * armEnd,
                            center.y + direction.y * armEnd,
                        ),
                        strokeWidth = stroke,
                    )
                }
            }
        }
    }
}
