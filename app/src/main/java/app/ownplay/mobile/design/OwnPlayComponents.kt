package app.ownplay.mobile.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun OwnPlayWordmark(
    modifier: Modifier = Modifier,
    showTagline: Boolean = true,
) {
    Column(modifier = modifier) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = OwnPlayColors.TextPrimary)) { append("Own") }
                withStyle(SpanStyle(color = OwnPlayColors.Accent)) { append("Play") }
            },
            fontSize = 28.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.SemiBold,
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
            .padding(horizontal = OwnPlaySpacing.Lg, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OwnPlayWordmark(
            modifier = Modifier.weight(1f),
            showTagline = showTagline,
        )
        TopActionGlyph(kind = TopActionKind.Search)
        Spacer(modifier = Modifier.width(OwnPlaySpacing.Xs))
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
                style = MaterialTheme.typography.labelLarge,
                color = OwnPlayColors.TextMuted,
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
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        shape = OwnPlayShapeTokens.Action,
        color = OwnPlayColors.AccentStrong,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = OwnPlayColors.TextPrimary,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun OwnPlaySecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        shape = OwnPlayShapeTokens.Action,
        color = OwnPlayColors.SurfaceElevated,
        border = BorderStroke(1.dp, OwnPlayColors.Divider.copy(alpha = 0.82f)),
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = OwnPlayColors.TextPrimary,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun OwnPlayFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        shape = OwnPlayShapeTokens.Small,
        color = if (selected) OwnPlayColors.Accent.copy(alpha = 0.10f) else Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) OwnPlayColors.TextPrimary else OwnPlayColors.TextSecondary,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(5.dp))
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(2.dp)
                    .background(
                        color = if (selected) OwnPlayColors.Accent else Color.Transparent,
                        shape = OwnPlayShapeTokens.Small,
                    ),
            )
        }
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = OwnPlayColors.SurfaceElevated.copy(alpha = 0.72f),
        shape = OwnPlayShapeTokens.Medium,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(OwnPlaySpacing.Lg),
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

@Composable
fun OwnPlayModal(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = OwnPlayColors.SurfaceElevated,
            shape = OwnPlayShapeTokens.Large,
            border = BorderStroke(1.dp, OwnPlayColors.Divider.copy(alpha = 0.74f)),
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(OwnPlaySpacing.Xl),
                verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = OwnPlayColors.TextPrimary,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = OwnPlayColors.TextSecondary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm, Alignment.End),
                ) {
                    OwnPlaySecondaryButton(text = dismissLabel, onClick = onDismiss)
                    OwnPlayPrimaryButton(text = confirmLabel, onClick = onConfirm)
                }
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
        modifier = Modifier
            .size(40.dp)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(22.dp)) {
            val strokeWidth = 1.8.dp.toPx()
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
                    val radius = 1.55.dp.toPx()
                    drawCircle(OwnPlayColors.TextPrimary, radius, center.copy(y = size.height * 0.25f))
                    drawCircle(OwnPlayColors.TextPrimary, radius, center)
                    drawCircle(OwnPlayColors.TextPrimary, radius, center.copy(y = size.height * 0.75f))
                }
            }
        }
    }
}
