package app.ownplay.mobile.feature.settings.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.mobile.data.prefs.SettingsPreferences
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayPanel
import app.ownplay.mobile.design.OwnPlayShapeTokens
import app.ownplay.mobile.design.OwnPlaySpacing
import app.ownplay.mobile.design.OwnPlayTopBar
import app.ownplay.mobile.downloads.domain.DownloadRepository
import app.ownplay.mobile.feature.live.domain.LiveRepository
import app.ownplay.mobile.feature.settings.domain.BackupRepository
import app.ownplay.mobile.feature.settings.domain.ProviderRefreshInterval
import app.ownplay.mobile.feature.settings.domain.SettingsSnapshot
import app.ownplay.mobile.sources.domain.SourceRepository
import kotlinx.coroutines.launch

private enum class SettingsPage {
    MAIN,
    SOURCES,
    BACKUP_RESTORE,
    MANAGE_LIVE,
    MANAGE_DOWNLOADS,
    HELP,
    PRIVACY,
}

private sealed interface SettingTrailing {
    data class Toggle(val checked: Boolean) : SettingTrailing
    data object Chevron : SettingTrailing
}

private data class SettingRowModel(
    val title: String,
    val summary: String,
    val trailing: SettingTrailing,
    val onClick: () -> Unit,
)

@Composable
fun SettingsShell(
    sourceRepository: SourceRepository,
    settingsPreferences: SettingsPreferences,
    backupRepository: BackupRepository,
    liveRepository: LiveRepository,
    downloadRepository: DownloadRepository,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pageName by rememberSaveable { mutableStateOf(SettingsPage.MAIN.name) }
    val page = runCatching { SettingsPage.valueOf(pageName) }.getOrDefault(SettingsPage.MAIN)

    BackHandler(enabled = page != SettingsPage.MAIN) {
        pageName = SettingsPage.MAIN.name
    }

    when (page) {
        SettingsPage.MAIN -> MainSettings(
            settingsPreferences = settingsPreferences,
            onOpenSources = { pageName = SettingsPage.SOURCES.name },
            onOpenBackupRestore = { pageName = SettingsPage.BACKUP_RESTORE.name },
            onManageLive = { pageName = SettingsPage.MANAGE_LIVE.name },
            onManageDownloads = { pageName = SettingsPage.MANAGE_DOWNLOADS.name },
            onHelp = { pageName = SettingsPage.HELP.name },
            onPrivacy = { pageName = SettingsPage.PRIVACY.name },
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

        SettingsPage.MANAGE_LIVE -> LiveManagementScreen(
            liveRepository = liveRepository,
            onBack = { pageName = SettingsPage.MAIN.name },
            modifier = modifier,
        )

        SettingsPage.MANAGE_DOWNLOADS -> DownloadManagementScreen(
            downloadRepository = downloadRepository,
            onOpenLibrary = onOpenLibrary,
            onBack = { pageName = SettingsPage.MAIN.name },
            modifier = modifier,
        )

        SettingsPage.HELP -> SettingsInfoScreen(
            title = "Help & support",
            paragraphs = listOf(
                "Add and refresh providers from Settings › Sources. Live and Library use the active source.",
                "In Live, tap a channel once for inline Preview and tap the selected channel again for fullscreen. Back returns fullscreen to Preview, then Preview to browsing.",
                "Use Settings › Manage Live channels to hide or reorder categories and individual channels. Hold the visible drag grip, then move vertically.",
                "Backup & restore is versioned and deliberately excludes provider credentials; reconnect credentials after restoring when required.",
            ),
            onBack = { pageName = SettingsPage.MAIN.name },
            modifier = modifier,
        )

        SettingsPage.PRIVACY -> SettingsInfoScreen(
            title = "Privacy",
            paragraphs = listOf(
                "Provider credentials are kept outside the OwnPlay backup document and are not exported by Backup & restore.",
                "Playback and provider requests are made for the sources you configure. OwnPlay does not display credentials in playback diagnostics.",
                "Removing a source removes its local catalog relationship. Use Backup & restore before destructive device-level actions when you need portability.",
            ),
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
    onManageLive: () -> Unit,
    onManageDownloads: () -> Unit,
    onHelp: () -> Unit,
    onPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settingsFlow = remember(settingsPreferences) { settingsPreferences.settings }
    val settings by settingsFlow.collectAsState(initial = SettingsSnapshot())
    val scope = rememberCoroutineScope()
    var intervalChooserVisible by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = intervalChooserVisible) {
        intervalChooserVisible = false
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        OwnPlayTopBar(showTagline = true)
        Column(
            modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xl),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Settings", style = MaterialTheme.typography.headlineMedium, color = OwnPlayColors.TextPrimary)
                Text(
                    "Personalize your viewing experience",
                    style = MaterialTheme.typography.bodyLarge,
                    color = OwnPlayColors.TextSecondary,
                )
            }

            SettingsSection(
                title = "Playback",
                subtitle = "How OwnPlay behaves while media is playing",
                marker = "▶",
                rows = listOf(
                    SettingRowModel(
                        "Picture in Picture",
                        "Keep watching while using other apps",
                        SettingTrailing.Toggle(settings.pictureInPictureEnabled),
                    ) { scope.launch { settingsPreferences.setPictureInPictureEnabled(!settings.pictureInPictureEnabled) } },
                    SettingRowModel(
                        "Resume playback",
                        "Prefer Resume when saved progress exists",
                        SettingTrailing.Toggle(settings.resumePlaybackEnabled),
                    ) { scope.launch { settingsPreferences.setResumePlaybackEnabled(!settings.resumePlaybackEnabled) } },
                ),
            )

            SettingsSection(
                title = "Live & EPG",
                subtitle = "Providers, channel visibility, and guide behavior",
                marker = "●",
                rows = listOf(
                    SettingRowModel("Sources", "Add, edit, select, remove, and refresh providers", SettingTrailing.Chevron, onOpenSources),
                    SettingRowModel("Manage Live channels", "Show, hide, and reorder categories or channels", SettingTrailing.Chevron, onManageLive),
                    SettingRowModel(
                        "Auto-refresh providers",
                        "Refresh configured sources when a network is available",
                        SettingTrailing.Toggle(settings.autoRefreshProviders),
                    ) { scope.launch { settingsPreferences.setAutoRefreshProviders(!settings.autoRefreshProviders) } },
                    SettingRowModel(
                        "Update interval",
                        settings.providerRefreshInterval.summary,
                        SettingTrailing.Chevron,
                    ) { intervalChooserVisible = !intervalChooserVisible },
                    SettingRowModel(
                        "Show channel logos",
                        "Use provider artwork in Live browsing",
                        SettingTrailing.Toggle(settings.showChannelLogos),
                    ) { scope.launch { settingsPreferences.setShowChannelLogos(!settings.showChannelLogos) } },
                ),
            )

            if (intervalChooserVisible) {
                ProviderRefreshIntervalChooser(
                    selected = settings.providerRefreshInterval,
                    onSelect = { interval ->
                        scope.launch { settingsPreferences.setProviderRefreshInterval(interval) }
                        intervalChooserVisible = false
                    },
                )
            }

            SettingsSection(
                title = "Downloads",
                subtitle = "Offline media",
                marker = "↓",
                rows = listOf(
                    SettingRowModel("Manage downloads", "Pause, resume, retry, remove, or open completed media", SettingTrailing.Chevron, onManageDownloads),
                ),
            )

            SettingsSection(
                title = "Data",
                subtitle = "Portability and recovery",
                marker = "↕",
                rows = listOf(
                    SettingRowModel(
                        "Backup & restore",
                        "Versioned backup without provider credentials",
                        SettingTrailing.Chevron,
                        onOpenBackupRestore,
                    ),
                ),
            )

            SettingsSection(
                title = "About",
                subtitle = "Help and privacy",
                marker = "i",
                rows = listOf(
                    SettingRowModel("Help & support", "Usage guidance for OwnPlay", SettingTrailing.Chevron, onHelp),
                    SettingRowModel("Privacy policy", "How OwnPlay handles configured source data", SettingTrailing.Chevron, onPrivacy),
                ),
            )

            Spacer(modifier = Modifier.height(OwnPlaySpacing.Xl))
        }
    }
}

@Composable
private fun ProviderRefreshIntervalChooser(
    selected: ProviderRefreshInterval,
    onSelect: (ProviderRefreshInterval) -> Unit,
) {
    OwnPlayPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(OwnPlaySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
        ) {
            Text(
                "Provider refresh interval",
                style = MaterialTheme.typography.titleMedium,
                color = OwnPlayColors.TextPrimary,
            )
            Text(
                "Choose when OwnPlay should refresh configured providers automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = OwnPlayColors.TextSecondary,
            )
            ProviderRefreshInterval.values().forEach { interval ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.RadioButton) { onSelect(interval) }
                        .padding(vertical = OwnPlaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (interval == selected) "●" else "○",
                        modifier = Modifier.width(28.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (interval == selected) OwnPlayColors.Accent else OwnPlayColors.TextMuted,
                    )
                    Text(
                        interval.summary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = OwnPlayColors.TextPrimary,
                    )
                }
            }
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
    Column(verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(OwnPlayShapeTokens.Small)
                    .background(OwnPlayColors.Accent.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    marker,
                    style = MaterialTheme.typography.labelLarge,
                    color = OwnPlayColors.Accent,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.width(OwnPlaySpacing.Md))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = OwnPlayColors.TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = OwnPlayColors.TextMuted)
            }
        }
        OwnPlayPanel(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = OwnPlaySpacing.Md, vertical = OwnPlaySpacing.Xs)) {
                rows.forEach { row -> SettingRow(row) }
            }
        }
    }
}

@Composable
private fun SettingRow(row: SettingRowModel) {
    val interactionModifier = when (val trailing = row.trailing) {
        is SettingTrailing.Toggle -> Modifier.toggleable(
            value = trailing.checked,
            role = Role.Switch,
            onValueChange = { row.onClick() },
        )
        SettingTrailing.Chevron -> Modifier.clickable(role = Role.Button, onClick = row.onClick)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(interactionModifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(row.title, style = MaterialTheme.typography.bodyLarge, color = OwnPlayColors.TextPrimary)
            Text(row.summary, style = MaterialTheme.typography.bodyMedium, color = OwnPlayColors.TextSecondary)
        }
        Spacer(modifier = Modifier.width(OwnPlaySpacing.Md))
        when (val trailing = row.trailing) {
            is SettingTrailing.Toggle -> OwnPlayToggle(trailing.checked)
            SettingTrailing.Chevron -> Text("›", style = MaterialTheme.typography.titleLarge, color = OwnPlayColors.TextMuted)
        }
    }
}

@Composable
private fun OwnPlayToggle(checked: Boolean) {
    Box(
        modifier = Modifier
            .width(46.dp)
            .height(26.dp)
            .clip(CircleShape)
            .background(if (checked) OwnPlayColors.AccentStrong else OwnPlayColors.Divider)
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(OwnPlayColors.TextPrimary))
    }
}

@Composable
private fun SettingsInfoScreen(
    title: String,
    paragraphs: List<String>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        OwnPlayTopBar(showTagline = false)
        Column(
            modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
        ) {
            Text(
                "‹ Settings",
                modifier = Modifier.clickable(onClick = onBack).padding(vertical = OwnPlaySpacing.Sm),
                style = MaterialTheme.typography.labelLarge,
                color = OwnPlayColors.Accent,
            )
            Text(title, style = MaterialTheme.typography.headlineMedium, color = OwnPlayColors.TextPrimary)
            OwnPlayPanel(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(OwnPlaySpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
                ) {
                    paragraphs.forEach { paragraph ->
                        Text(paragraph, style = MaterialTheme.typography.bodyLarge, color = OwnPlayColors.TextSecondary)
                    }
                }
            }
        }
    }
}
