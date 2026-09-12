from pathlib import Path

BASE_SHA = "4c2494b233b8718df30e60f630a3236cef51d24b"

library_actions = r'''package app.ownplay.mobile.feature.library.ui

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
'''

audit = f'''# Stage 14 Library action source audit

Base source: `{BASE_SHA}` (`stage-13-visual-system-refactor`)

## User-directed scope

Refine button style and size outside the player in Library without changing player geometry or playback behavior.

## Source changes

- Added Library-local action primitives with 48dp interactive targets and 40dp visual chrome.
- Resume/Play remains the dominant blue action; Start Over is visually secondary and narrower in Continue Watching.
- Added small decorative play/restart glyphs excluded from accessibility semantics so spoken labels remain clean.
- Replaced Library category chip presentation with text-led 48dp targets and a restrained 2dp selected underline.
- Applied the same Library-local Play/Resume/Start Over hierarchy to movie and episode details.
- Existing download controls remain behaviorally and structurally unchanged.

## Explicitly unchanged

- fullscreen player and player glass chrome
- PlaybackController/session/surface ownership
- Live UI and Live Preview/fullscreen state machine
- provider parsing/refresh logic
- Room/schema/data migration
- downloads state machine and storage
- Settings and source-management screens
- signing, packaging, version metadata, release/deploy configuration

## Acceptance boundary

Source validation is necessary but not sufficient. Physical-device comparison is still required before visual acceptance.
No APK/AAB generation is authorized by this source-only stage.
'''

root = Path(".")
shell_path = root / "app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryShell.kt"
actions_path = root / "app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryActions.kt"
audit_path = root / "docs/audit/STAGE14_LIBRARY_ACTION_SOURCE_AUDIT.md"

text = shell_path.read_text()

for line in [
    "import app.ownplay.mobile.design.OwnPlayFilterChip\n",
    "import app.ownplay.mobile.design.OwnPlayPrimaryButton\n",
    "import app.ownplay.mobile.design.OwnPlaySecondaryButton\n",
]:
    if text.count(line) != 1:
        raise SystemExit(f"Expected exactly one import: {line.strip()}")
    text = text.replace(line, "")

old_filter = '''            OwnPlayFilterChip(
                label = category.name,
                selected = selectedCategoryKey == category.categoryKey,
                onClick = { onSelected(category.categoryKey) },
            )'''
new_filter = '''            LibraryFilterTab(
                label = category.name,
                selected = selectedCategoryKey == category.categoryKey,
                onClick = { onSelected(category.categoryKey) },
            )'''
if text.count(old_filter) != 1:
    raise SystemExit("Expected exactly one Library category filter call")
text = text.replace(old_filter, new_filter)

old_continue = '''                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OwnPlayPrimaryButton(
                        text = "Resume",
                        onClick = onResume,
                        modifier = Modifier.weight(1f),
                    )
                    OwnPlaySecondaryButton(
                        text = "Start Over",
                        onClick = onBeginning,
                        modifier = Modifier.weight(1f),
                    )
                }'''
new_continue = '''                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LibraryPrimaryAction(
                        text = "Resume",
                        onClick = onResume,
                        modifier = Modifier.weight(1.25f),
                        glyph = LibraryActionGlyph.PLAY,
                    )
                    LibrarySecondaryAction(
                        text = "Start Over",
                        onClick = onBeginning,
                        modifier = Modifier.weight(0.85f),
                        glyph = LibraryActionGlyph.RESTART,
                    )
                }'''
if text.count(old_continue) != 1:
    raise SystemExit("Expected exactly one Continue Watching action row")
text = text.replace(old_continue, new_continue)

old_play = '''                    OwnPlayPrimaryButton(
                        text = "Play",
                        onClick = onBeginning,
                    )'''
new_play = '''                    LibraryPrimaryAction(
                        text = "Play",
                        onClick = onBeginning,
                        glyph = LibraryActionGlyph.PLAY,
                    )'''
if text.count(old_play) != 2:
    raise SystemExit(f"Expected two detail Play actions, found {text.count(old_play)}")
text = text.replace(old_play, new_play)

old_choices = '''    Row(
        horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (preferResume) {
            OwnPlayPrimaryButton("Resume", onResume)
            OwnPlaySecondaryButton("Start Over", onBeginning)
        } else {
            OwnPlayPrimaryButton("Start Over", onBeginning)
            OwnPlaySecondaryButton("Resume", onResume)
        }
    }'''
new_choices = '''    Row(
        horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (preferResume) {
            LibraryPrimaryAction(
                text = "Resume",
                onClick = onResume,
                glyph = LibraryActionGlyph.PLAY,
            )
            LibrarySecondaryAction(
                text = "Start Over",
                onClick = onBeginning,
                glyph = LibraryActionGlyph.RESTART,
            )
        } else {
            LibraryPrimaryAction(
                text = "Start Over",
                onClick = onBeginning,
                glyph = LibraryActionGlyph.RESTART,
            )
            LibrarySecondaryAction(
                text = "Resume",
                onClick = onResume,
                glyph = LibraryActionGlyph.PLAY,
            )
        }
    }'''
if text.count(old_choices) != 1:
    raise SystemExit("Expected exactly one PlaybackChoiceButtons body")
text = text.replace(old_choices, new_choices)

shell_path.write_text(text)
actions_path.write_text(library_actions)
audit_path.parent.mkdir(parents=True, exist_ok=True)
audit_path.write_text(audit)

expected = {
    str(actions_path),
    str(shell_path),
    str(audit_path),
}
print("Stage 14 patch prepared:")
for item in sorted(expected):
    print(item)
