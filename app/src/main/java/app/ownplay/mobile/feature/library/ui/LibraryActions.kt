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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayShapeTokens

internal enum class LibraryActionGlyph {
    PLAY,
    RESTART,
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
    val contentColor = if (primary) OwnPlayColors.TextPrimary else OwnPlayColors.TextSecondary
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
            shape = OwnPlayShapeTokens.Action,
            color = if (primary) OwnPlayColors.AccentStrong else OwnPlayColors.SurfaceElevated.copy(alpha = 0.66f),
            border = if (primary) null else BorderStroke(1.dp, OwnPlayColors.Divider.copy(alpha = 0.52f)),
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                glyph?.let {
                    Text(
                        text = when (it) {
                            LibraryActionGlyph.PLAY -> "▶"
                            LibraryActionGlyph.RESTART -> "↺"
                        },
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
internal fun LibraryFilterTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(48.dp)
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
                modifier = Modifier.padding(horizontal = 10.dp),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) OwnPlayColors.TextPrimary else OwnPlayColors.TextSecondary,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(4.dp))
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
