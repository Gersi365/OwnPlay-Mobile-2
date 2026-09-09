package app.ownplay.mobile.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayPanel
import app.ownplay.mobile.design.OwnPlayShapeTokens
import app.ownplay.mobile.design.OwnPlaySpacing
import app.ownplay.mobile.design.OwnPlayTopBar

private sealed interface SettingTrailing {
    data class Toggle(val checked: Boolean) : SettingTrailing
    data object Chevron : SettingTrailing
    data object None : SettingTrailing
}

private data class SettingShellRow(
    val title: String,
    val summary: String,
    val trailing: SettingTrailing,
)

@Composable
fun SettingsShell(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        OwnPlayTopBar(showTagline = true)

        Column(
            modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = OwnPlayColors.TextPrimary,
            )
            Text(
                text = "Personalize your viewing experience",
                style = MaterialTheme.typography.bodyLarge,
                color = OwnPlayColors.TextSecondary,
            )

            SettingsSectionShell(
                title = "Playback",
                subtitle = "Control how your content plays",
                marker = "▶",
                rows = listOf(
                    SettingShellRow("Picture in Picture", "Keep watching while using other apps", SettingTrailing.Toggle(true)),
                    SettingShellRow("Resume playback", "Automatically resume where you left off", SettingTrailing.Toggle(true)),
                    SettingShellRow("Default playback quality", "Auto (Best Available)", SettingTrailing.Chevron),
                ),
            )
            SettingsSectionShell(
                title = "Live & EPG",
                subtitle = "Keep your channels up to date",
                marker = "●",
                rows = listOf(
                    SettingShellRow("Auto-refresh providers", "Automatically check for updates", SettingTrailing.Toggle(true)),
                    SettingShellRow("Update interval", "Every 6 hours", SettingTrailing.Chevron),
                    SettingShellRow("Show channel logos", "Display channel logos in guide", SettingTrailing.Toggle(true)),
                ),
            )
            SettingsSectionShell(
                title = "Downloads",
                subtitle = "Watch offline, on your terms",
                marker = "↓",
                rows = listOf(
                    SettingShellRow("Download quality", "High (720p)", SettingTrailing.Chevron),
                    SettingShellRow("Storage location", "Internal Storage", SettingTrailing.Chevron),
                    SettingShellRow("Manage downloads", "View and delete downloaded content", SettingTrailing.Chevron),
                ),
            )
            SettingsSectionShell(
                title = "Appearance",
                subtitle = "Make OwnPlay yours",
                marker = "◐",
                rows = listOf(
                    SettingShellRow("Theme", "System Default", SettingTrailing.Chevron),
                    SettingShellRow("Use dynamic colors", "Match your device theme", SettingTrailing.Toggle(false)),
                ),
            )
            SettingsSectionShell(
                title = "About",
                subtitle = "App information and support",
                marker = "i",
                rows = listOf(
                    SettingShellRow("App version", "0.1.0-dev", SettingTrailing.None),
                    SettingShellRow("Help & support", "Support surface reserved", SettingTrailing.Chevron),
                    SettingShellRow("Privacy policy", "Privacy surface reserved", SettingTrailing.Chevron),
                ),
            )

            Spacer(modifier = Modifier.height(OwnPlaySpacing.Xl))
        }
    }
}

@Composable
private fun SettingsSectionShell(
    title: String,
    subtitle: String,
    marker: String,
    rows: List<SettingShellRow>,
) {
    OwnPlayPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = OwnPlaySpacing.Md)) {
            Row(
                modifier = Modifier.padding(vertical = OwnPlaySpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(OwnPlayShapeTokens.Small)
                        .background(OwnPlayColors.SurfaceElevated),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = marker,
                        style = MaterialTheme.typography.titleMedium,
                        color = OwnPlayColors.Accent,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.width(OwnPlaySpacing.Md))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = OwnPlayColors.TextPrimary,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OwnPlayColors.TextSecondary,
                    )
                }
            }
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(OwnPlayColors.Divider),
                    )
                }
                SettingRowShell(row)
            }
        }
    }
}

@Composable
private fun SettingRowShell(row: SettingShellRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = OwnPlaySpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyLarge,
                color = OwnPlayColors.TextPrimary,
            )
            Text(
                text = row.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = OwnPlayColors.TextSecondary,
            )
        }
        Spacer(modifier = Modifier.width(OwnPlaySpacing.Md))
        when (val trailing = row.trailing) {
            is SettingTrailing.Toggle -> StaticToggle(checked = trailing.checked)
            SettingTrailing.Chevron -> Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = OwnPlayColors.TextSecondary,
            )
            SettingTrailing.None -> Unit
        }
    }
}

@Composable
private fun StaticToggle(checked: Boolean) {
    Box(
        modifier = Modifier
            .width(52.dp)
            .height(30.dp)
            .clip(CircleShape)
            .background(if (checked) OwnPlayColors.AccentStrong else OwnPlayColors.Divider)
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(OwnPlayColors.TextPrimary),
        )
    }
}
