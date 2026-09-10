package app.ownplay.mobile.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.mobile.data.prefs.SettingsPreferences
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayPanel
import app.ownplay.mobile.design.OwnPlayShapeTokens
import app.ownplay.mobile.design.OwnPlaySpacing
import app.ownplay.mobile.design.OwnPlayTopBar
import app.ownplay.mobile.feature.settings.domain.BackupRepository
import app.ownplay.mobile.feature.settings.domain.SettingsSnapshot
import app.ownplay.mobile.sources.domain.SourceRepository
import kotlinx.coroutines.launch

private enum class SettingsPage {
    MAIN,
    SOURCES,
    BACKUP_RESTORE,
}

private sealed interface SettingTrailing {
    data class Toggle(val checked: Boolean) : SettingTrailing
    data object Chevron : SettingTrailing
    data object None : SettingTrailing
}

private data class SettingRowModel(
    val title: String,
    val summary: String,
    val trailing: SettingTrailing,
    val onClick: (() -> Unit)? = null,
)

@Composable
fun SettingsShell(
    sourceRepository: SourceRepository,
    settingsPreferences: SettingsPreferences,
    backupRepository: BackupRepository,
    modifier: Modifier = Modifier,
) {
    var pageName by rememberSaveable { mutableStateOf(SettingsPage.MAIN.name) }
    val page = runCatching { SettingsPage.valueOf(pageName) }.getOrDefault(SettingsPage.MAIN)

    when (page) {
        SettingsPage.MAIN -> MainSettings(
            settingsPreferences = settingsPreferences,
            onOpenSources = { pageName = SettingsPage.SOURCES.name },
            onOpenBackupRestore = { pageName = SettingsPage.BACKUP_RESTORE.name },
            modifier = modifier,
        )

        SettingsPage.SOURCES -> SourceManagementScreen(
            sourceRepository = sourceRepository,
            backupRepository = backupRepository,
            onBack = { pageName = SettingsPage.MAIN.name },
            modifier = modifier,
        )

        SettingsPage.BACKUP_RESTORE -> BackupRestoreScreen(
            backupRepository = backupRepository,
            onBack = { pageName = SettingsPage.MAIN.name },
            modifier = modifier,
        )
    }
}

@Composable
private fun MainSettings(
    settingsPreferences: SettingsPreferences,
    onOpenSources: () -> Unit,
    onOpenBackupRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settingsFlow = remember(settingsPreferences) { settingsPreferences.settings }
    val settings by settingsFlow.collectAsState(initial = SettingsSnapshot())
    val scope = rememberCoroutineScope()

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

            SettingsSection(
                title = "Playback",
                subtitle = "Control how your content plays",
                marker = "▶",
                rows = listOf(
                    SettingRowModel(
                        title = "Picture in Picture",
                        summary = "Keep watching while using other apps",
                        trailing = SettingTrailing.Toggle(settings.pictureInPictureEnabled),
                        onClick = {
                            scope.launch {
                                settingsPreferences.setPictureInPictureEnabled(!settings.pictureInPictureEnabled)
                            }
                        },
                    ),
                    SettingRowModel(
                        title = "Resume playback",
                        summary = "Prefer your saved position when playback resumes",
                        trailing = SettingTrailing.Toggle(settings.resumePlaybackEnabled),
                        onClick = {
                            scope.launch {
                                settingsPreferences.setResumePlaybackEnabled(!settings.resumePlaybackEnabled)
                            }
                        },
                    ),
                    SettingRowModel(
                        title = "Default playback quality",
                        summary = "Auto (Best Available)",
                        trailing = SettingTrailing.None,
                    ),
                ),
            )

            SettingsSection(
                title = "Live & EPG",
                subtitle = "Keep your channels up to date",
                marker = "●",
                rows = listOf(
                    SettingRowModel(
                        title = "Sources",
                        summary = "Add, edit, select, remove, and refresh providers",
                        trailing = SettingTrailing.Chevron,
                        onClick = onOpenSources,
                    ),
                    SettingRowModel(
                        title = "Auto-refresh providers",
                        summary = "Refresh configured sources when a network is available",
                        trailing = SettingTrailing.Toggle(settings.autoRefreshProviders),
                        onClick = {
                            scope.launch {
                                settingsPreferences.setAutoRefreshProviders(!settings.autoRefreshProviders)
                            }
                        },
                    ),
                    SettingRowModel(
                        title = "Update interval",
                        summary = settings.providerRefreshInterval.summary,
                        trailing = SettingTrailing.Chevron,
                        onClick = {
                            scope.launch {
                                settingsPreferences.setProviderRefreshInterval(settings.providerRefreshInterval.next())
                            }
                        },
                    ),
                    SettingRowModel(
                        title = "Show channel logos",
                        summary = "Display provider channel artwork when available",
                        trailing = SettingTrailing.Toggle(settings.showChannelLogos),
                        onClick = {
                            scope.launch {
                                settingsPreferences.setShowChannelLogos(!settings.showChannelLogos)
                            }
                        },
                    ),
                ),
            )

            SettingsSection(
                title = "Downloads",
                subtitle = "Watch offline, on your terms",
                marker = "↓",
                rows = listOf(
                    SettingRowModel("Download quality", "Original source", SettingTrailing.None),
                    SettingRowModel("Storage location", "Internal app storage", SettingTrailing.None),
                    SettingRowModel("Manage downloads", "Library · Downloaded Media", SettingTrailing.None),
                ),
            )

            SettingsSection(
                title = "Appearance",
                subtitle = "OwnPlay visual system",
                marker = "◐",
                rows = listOf(
                    SettingRowModel(
                        title = "Theme",
                        summary = "OwnPlay Dark",
                        trailing = SettingTrailing.None,
                    ),
                    SettingRowModel(
                        title = "Accent",
                        summary = "OwnPlay blue",
                        trailing = SettingTrailing.None,
                    ),
                ),
            )

            SettingsSection(
                title = "About",
                subtitle = "App information and portability",
                marker = "i",
                rows = listOf(
                    SettingRowModel("App version", "0.1.0-dev", SettingTrailing.None),
                    SettingRowModel(
                        title = "Backup & restore",
                        summary = "Versioned backup without provider credentials",
                        trailing = SettingTrailing.Chevron,
                        onClick = onOpenBackupRestore,
                    ),
                    SettingRowModel("Help & support", "Support surface reserved", SettingTrailing.None),
                    SettingRowModel("Privacy policy", "Privacy surface reserved", SettingTrailing.None),
                ),
            )

            Spacer(modifier = Modifier.height(OwnPlaySpacing.Xl))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    subtitle: String,
    marker: String,
    rows: List<SettingRowModel>,
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
                SettingRow(row)
            }
        }
    }
}

@Composable
private fun SettingRow(row: SettingRowModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = row.onClick != null) { row.onClick?.invoke() }
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
            is SettingTrailing.Toggle -> OwnPlayToggle(checked = trailing.checked)
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
private fun OwnPlayToggle(checked: Boolean) {
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
