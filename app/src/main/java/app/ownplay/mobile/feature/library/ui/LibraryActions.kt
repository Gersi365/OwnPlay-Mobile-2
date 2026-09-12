package app.ownplay.mobile.feature.library.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayShapeTokens

internal enum class LibraryActionGlyph {
    PLAY,
    RESTART,
    BACK,
}

@Composable
internal fun LibraryPrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: LibraryActionGlyph? = null,
) {
    LibraryActionButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        primary = true,
        glyph = glyph,
    )
}

@Composable
internal fun LibrarySecondaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: LibraryActionGlyph? = null,
) {
    LibraryActionButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        primary = false,
        glyph = glyph,
    )
}

@Composable
private fun LibraryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    primary: Boolean,
    glyph: LibraryActionGlyph?,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val contentColor = if (primary) Color.White else OwnPlayColors.TextSecondary
    val containerColor by animateColorAsState(
        targetValue = when {
            primary && isPressed -> OwnPlayColors.Accent
            primary -> OwnPlayColors.AccentStrong
            isPressed -> OwnPlayColors.SurfaceElevated.copy(alpha = 0.72f)
            else -> Color.Transparent
        },
        label = "libraryActionContainer",
    )
    Box(
        modifier = modifier
            .height(48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {},
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            shape = OwnPlayShapeTokens.Small,
            color = containerColor,
            border = null,
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = if (primary) 14.dp else 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                glyph?.let {
                    Text(
                        text = it.symbol,
                        modifier = Modifier.clearAndSetSemantics {},
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun LibraryIconAction(
    glyph: LibraryActionGlyph,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    visualSize: Dp = 40.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val containerColor by animateColorAsState(
        targetValue = when {
            emphasized && isPressed -> OwnPlayColors.Accent
            emphasized -> OwnPlayColors.AccentStrong
            isPressed -> Color.Black.copy(alpha = 0.68f)
            else -> Color.Black.copy(alpha = 0.52f)
        },
        label = "libraryIconContainer",
    )
    Box(
        modifier = modifier
            .width(48.dp)
            .height(48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .width(visualSize)
                .height(visualSize),
            shape = OwnPlayShapeTokens.Action,
            color = containerColor,
            border = if (emphasized) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            tonalElevation = 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = glyph.symbol,
                    modifier = Modifier.clearAndSetSemantics {},
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
internal fun LibraryShelfHeader(
    title: String,
    actionLabel: String? = null,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(OwnPlayShapeHeaderSpacing),
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = if (prominent) 21.sp else 18.sp,
                lineHeight = if (prominent) 25.sp else 22.sp,
            ),
            color = OwnPlayColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        actionLabel?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = OwnPlayColors.TextMuted.copy(alpha = 0.72f),
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun LibraryShelfState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(34.dp)
                .background(
                    color = if (loading) {
                        OwnPlayColors.Accent.copy(alpha = 0.64f)
                    } else {
                        OwnPlayColors.Divider.copy(alpha = 0.82f)
                    },
                    shape = OwnPlayShapeTokens.Small,
                ),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = OwnPlayColors.TextSecondary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = OwnPlayColors.TextMuted,
            )
        }
    }
}

@Composable
internal fun LibraryFilterTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .height(48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    selected -> OwnPlayColors.TextPrimary
                    isPressed -> OwnPlayColors.TextSecondary
                    else -> OwnPlayColors.TextMuted
                },
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .width(if (selected) 22.dp else 1.dp)
                    .height(2.dp)
                    .background(
                        color = if (selected) OwnPlayColors.Accent else Color.Transparent,
                        shape = OwnPlayShapeTokens.Small,
                    ),
            )
        }
    }
}

private val LibraryActionGlyph.symbol: String
    get() = when (this) {
        LibraryActionGlyph.PLAY -> "▶"
        LibraryActionGlyph.RESTART -> "↺"
        LibraryActionGlyph.BACK -> "‹"
    }

private val OwnPlayShapeHeaderSpacing = 12.dp
