package app.ownplay.mobile.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OwnPlayWordmark(
    modifier: Modifier = Modifier,
    showTagline: Boolean = true,
) {
    Column(modifier = modifier) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = OwnPlayColors.TextPrimary)) {
                    append("Own")
                }
                withStyle(SpanStyle(color = OwnPlayColors.Accent)) {
                    append("Play")
                }
            },
            fontSize = 30.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
        )
        if (showTagline) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Your Channels. Your Way.",
                style = MaterialTheme.typography.bodyMedium,
                color = OwnPlayColors.TextSecondary,
            )
        }
    }
}

@Composable
fun OwnPlayTopBar(
    modifier: Modifier = Modifier,
    showTagline: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OwnPlayWordmark(
            modifier = Modifier.weight(1f),
            showTagline = showTagline,
        )
        TopActionGlyph(kind = TopActionKind.Search)
        Spacer(modifier = Modifier.width(OwnPlaySpacing.Sm))
        TopActionGlyph(kind = TopActionKind.Menu)
    }
}

@Composable
fun OwnPlaySectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = OwnPlayColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = OwnPlayColors.TextSecondary,
            )
        }
    }
}

@Composable
fun OwnPlayPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = OwnPlayColors.Surface,
        shape = OwnPlayShapeTokens.Medium,
        border = BorderStroke(1.dp, OwnPlayColors.Divider),
        tonalElevation = 0.dp,
        content = content,
    )
}

@Composable
fun OwnPlayPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = OwnPlayShapeTokens.Action,
        colors = ButtonDefaults.buttonColors(
            containerColor = OwnPlayColors.AccentStrong,
            contentColor = OwnPlayColors.TextPrimary,
        ),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun OwnPlaySecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = OwnPlayShapeTokens.Action,
        border = BorderStroke(1.dp, OwnPlayColors.Divider),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = OwnPlayColors.TextPrimary,
        ),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun OwnPlayStatePanel(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    OwnPlayPanel(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(OwnPlaySpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = OwnPlayColors.TextPrimary,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = OwnPlayColors.TextSecondary,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(OwnPlaySpacing.Xs))
                OwnPlayPrimaryButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}

private enum class TopActionKind {
    Search,
    Menu,
}

@Composable
private fun TopActionGlyph(kind: TopActionKind) {
    Box(
        modifier = Modifier.size(44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val strokeWidth = 2.dp.toPx()
            when (kind) {
                TopActionKind.Search -> {
                    drawCircle(
                        color = OwnPlayColors.TextPrimary,
                        radius = size.minDimension * 0.30f,
                        center = center.copy(
                            x = center.x - size.width * 0.08f,
                            y = center.y - size.height * 0.08f,
                        ),
                        style = Stroke(width = strokeWidth),
                    )
                    drawLine(
                        color = OwnPlayColors.TextPrimary,
                        start = center.copy(
                            x = center.x + size.width * 0.14f,
                            y = center.y + size.height * 0.14f,
                        ),
                        end = center.copy(
                            x = center.x + size.width * 0.34f,
                            y = center.y + size.height * 0.34f,
                        ),
                        strokeWidth = strokeWidth,
                    )
                }

                TopActionKind.Menu -> {
                    val radius = 1.8.dp.toPx()
                    drawCircle(OwnPlayColors.TextPrimary, radius, center.copy(y = size.height * 0.25f))
                    drawCircle(OwnPlayColors.TextPrimary, radius, center)
                    drawCircle(OwnPlayColors.TextPrimary, radius, center.copy(y = size.height * 0.75f))
                }
            }
        }
    }
}
