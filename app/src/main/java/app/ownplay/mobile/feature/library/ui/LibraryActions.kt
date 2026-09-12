package app.ownplay.mobile.feature.library.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val contentColor = if (primary) Color.White else OwnPlayColors.TextSecondary
    Box(
        modifier = modifier
            .height(48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {},
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            shape = OwnPlayShapeTokens.Small,
            color = if (primary) OwnPlayColors.AccentStrong else Color.Transparent,
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
) {
    Box(
        modifier = modifier
            .width(48.dp)
            .height(48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .width(40.dp)
                .height(40.dp),
            shape = OwnPlayShapeTokens.Action,
            color = if (emphasized) OwnPlayColors.AccentStrong else Color.Black.copy(alpha = 0.52f),
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
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(OwnPlayShapeHeaderSpacing),
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            color = OwnPlayColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        actionLabel?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = OwnPlayColors.TextMuted,
                maxLines = 1,
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
    Box(
        modifier = modifier
            .height(44.dp)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) OwnPlayColors.Accent else OwnPlayColors.TextMuted,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(if (selected) 18.dp else 1.dp)
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
