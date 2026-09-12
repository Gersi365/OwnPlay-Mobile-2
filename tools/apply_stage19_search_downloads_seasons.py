from pathlib import Path

ROOT = Path(".")
changed = set()

def read(path):
    return (ROOT / path).read_text()

def write(path, content):
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    old = p.read_text() if p.exists() else None
    if old == content:
        raise SystemExit(f"no change for {path}")
    p.write_text(content)
    changed.add(path)

# 1) Top bar: real optional actions + 48dp shared targets.
components = "app/src/main/java/app/ownplay/mobile/design/OwnPlayComponents.kt"
text = read(components)
text = text.replace(
    "import androidx.compose.foundation.background\n",
    "import androidx.compose.foundation.background\nimport androidx.compose.foundation.clickable\n",
    1,
)
text = text.replace(
    "import androidx.compose.runtime.Composable\n",
    "import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.remember\n",
    1,
)
text = text.replace(
    "import androidx.compose.ui.semantics.clearAndSetSemantics\n",
    "import androidx.compose.ui.semantics.Role\nimport androidx.compose.ui.semantics.clearAndSetSemantics\nimport androidx.compose.ui.semantics.contentDescription\nimport androidx.compose.ui.semantics.semantics\n",
    1,
)
old_top = '''@Composable
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
'''
new_top = '''@Composable
fun OwnPlayTopBar(
    modifier: Modifier = Modifier,
    showTagline: Boolean = true,
    onSearchClick: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
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
        onSearchClick?.let { action ->
            TopActionGlyph(
                kind = TopActionKind.Search,
                contentDescription = "Search",
                onClick = action,
            )
        }
        onMenuClick?.let { action ->
            Spacer(modifier = Modifier.width(OwnPlaySpacing.Xs))
            TopActionGlyph(
                kind = TopActionKind.Menu,
                contentDescription = "More options",
                onClick = action,
            )
        }
    }
}
'''
if text.count(old_top) != 1:
    raise SystemExit("OwnPlayComponents: top bar block mismatch")
text = text.replace(old_top, new_top, 1)
if text.count(".heightIn(min = 44.dp)") != 3:
    raise SystemExit("OwnPlayComponents: expected three 44dp controls")
text = text.replace(".heightIn(min = 44.dp)", ".heightIn(min = 48.dp)")
old_glyph = '''@Composable
private fun TopActionGlyph(kind: TopActionKind) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(22.dp)) {
'''
new_glyph = '''@Composable
private fun TopActionGlyph(
    kind: TopActionKind,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .size(22.dp)
                .clearAndSetSemantics { },
        ) {
'''
if text.count(old_glyph) != 1:
    raise SystemExit("OwnPlayComponents: glyph block mismatch")
text = text.replace(old_glyph, new_glyph, 1)
write(components, text)

search_field = r'''package app.ownplay.mobile.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun OwnPlaySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = OwnPlayColors.SurfaceElevated.copy(alpha = 0.72f),
        shape = OwnPlayShapeTokens.Small,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = OwnPlaySpacing.Md, top = 12.dp, bottom = 12.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = OwnPlayColors.TextPrimary),
                cursorBrush = SolidColor(OwnPlayColors.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isBlank()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = OwnPlayColors.TextMuted,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .heightIn(min = 48.dp)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onClose,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "×",
                    style = MaterialTheme.typography.titleMedium,
                    color = OwnPlayColors.TextSecondary,
                )
            }
        }
    }
}
'''
write("app/src/main/java/app/ownplay/mobile/design/OwnPlaySearchField.kt", search_field)

# 2) Compact detail/episode download controls.
download_controls = r'''package app.ownplay.mobile.downloads.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayPrimaryButton
import app.ownplay.mobile.design.OwnPlaySecondaryButton
import app.ownplay.mobile.design.OwnPlayShapeTokens
import app.ownplay.mobile.design.OwnPlaySpacing
import app.ownplay.mobile.downloads.domain.DownloadAction
import app.ownplay.mobile.downloads.domain.DownloadItem
import app.ownplay.mobile.downloads.domain.DownloadState
import app.ownplay.mobile.downloads.domain.DownloadStatePolicy
import kotlin.math.roundToInt

@Composable
fun DownloadControls(
    item: DownloadItem?,
    onAction: (DownloadAction) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val primaryAction = DownloadStatePolicy.primaryAction(item)
    Column(
        modifier = if (compact) modifier else modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else OwnPlaySpacing.Sm),
    ) {
        if (item != null) {
            Text(
                text = statusLabel(item),
                style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                color = if (item.state == DownloadState.FAILED) OwnPlayColors.TextSecondary else OwnPlayColors.Accent,
            )
            item.progressFraction?.let { progress ->
                Box(
                    modifier = (if (compact) Modifier.width(132.dp) else Modifier.fillMaxWidth())
                        .height(if (compact) 2.dp else 3.dp)
                        .background(OwnPlayColors.Divider, OwnPlayShapeTokens.Small),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(if (compact) 2.dp else 3.dp)
                            .background(OwnPlayColors.Accent, OwnPlayShapeTokens.Small),
                    )
                }
            }
        }

        Row(
            modifier = if (compact) Modifier else Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 2.dp else OwnPlaySpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (compact) {
                DownloadCompactAction(
                    text = compactActionLabel(primaryAction),
                    emphasized = primaryAction != DownloadAction.DOWNLOAD,
                    onClick = { onAction(primaryAction) },
                )
                if (item != null) {
                    DownloadCompactAction(
                        text = "Remove",
                        emphasized = false,
                        onClick = { onAction(DownloadAction.REMOVE) },
                    )
                }
            } else {
                OwnPlayPrimaryButton(
                    text = actionLabel(primaryAction),
                    onClick = { onAction(primaryAction) },
                    modifier = Modifier.weight(1f),
                )
                if (item != null) {
                    OwnPlaySecondaryButton(
                        text = "Remove",
                        onClick = { onAction(DownloadAction.REMOVE) },
                        modifier = Modifier.weight(0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadCompactAction(
    text: String,
    emphasized: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
        shape = OwnPlayShapeTokens.Small,
        color = if (emphasized) OwnPlayColors.Accent.copy(alpha = 0.10f) else Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (emphasized) OwnPlayColors.Accent else OwnPlayColors.TextMuted,
                maxLines = 1,
            )
        }
    }
}

private fun statusLabel(item: DownloadItem): String = when (item.state) {
    DownloadState.QUEUED -> "Queued"
    DownloadState.DOWNLOADING -> item.progressFraction?.let { progress ->
        "Downloading ${(progress * 100f).roundToInt()}%"
    } ?: "Downloading ${formatBytes(item.bytesDownloaded)}"

    DownloadState.PAUSED -> "Paused • ${formatBytes(item.bytesDownloaded)} saved"
    DownloadState.FAILED -> "Download needs attention"
    DownloadState.COMPLETED -> if (item.resumePositionMs != null) {
        "Downloaded • resume available"
    } else {
        "Downloaded • verified offline"
    }
}

private fun compactActionLabel(action: DownloadAction): String = when (action) {
    DownloadAction.DOWNLOAD -> "Download"
    DownloadAction.PAUSE -> "Pause"
    DownloadAction.RESUME -> "Resume"
    DownloadAction.RETRY -> "Retry"
    DownloadAction.REMOVE -> "Remove"
    DownloadAction.PLAY_OFFLINE -> "Offline"
    DownloadAction.RESUME_OFFLINE -> "Offline"
}

private fun actionLabel(action: DownloadAction): String = when (action) {
    DownloadAction.DOWNLOAD -> "Download"
    DownloadAction.PAUSE -> "Pause"
    DownloadAction.RESUME -> "Resume Download"
    DownloadAction.RETRY -> "Retry"
    DownloadAction.REMOVE -> "Remove"
    DownloadAction.PLAY_OFFLINE -> "Play Offline"
    DownloadAction.RESUME_OFFLINE -> "Resume Offline"
}

private fun formatBytes(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    val mebibytes = safeBytes.toDouble() / (1024.0 * 1024.0)
    return if (mebibytes >= 1.0) {
        "%.1f MB".format(mebibytes)
    } else {
        "${safeBytes / 1024L} KB"
    }
}
'''
write("app/src/main/java/app/ownplay/mobile/downloads/ui/DownloadControls.kt", download_controls)

# 3) Manage Downloads redesign + direct offline handoff + confirm removal.
download_management = r'''package app.ownplay.mobile.feature.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayModal
import app.ownplay.mobile.design.OwnPlayShapeTokens
import app.ownplay.mobile.design.OwnPlaySpacing
import app.ownplay.mobile.design.OwnPlayStatePanel
import app.ownplay.mobile.design.OwnPlayTopBar
import app.ownplay.mobile.downloads.domain.DownloadItem
import app.ownplay.mobile.downloads.domain.DownloadOperationResult
import app.ownplay.mobile.downloads.domain.DownloadRepository
import app.ownplay.mobile.downloads.domain.DownloadState
import kotlinx.coroutines.launch

@Composable
fun DownloadManagementScreen(
    downloadRepository: DownloadRepository,
    onPlayOffline: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val downloadsFlow = remember(downloadRepository) { downloadRepository.observeDownloads() }
    val downloads by downloadsFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingRemoval by remember { mutableStateOf<DownloadItem?>(null) }
    BackHandler(onBack = onBack)

    pendingRemoval?.let { item ->
        OwnPlayModal(
            title = "Remove download?",
            message = "Remove ${item.title} from this device and OwnPlay Downloads?",
            confirmLabel = "Remove",
            dismissLabel = "Cancel",
            onConfirm = {
                pendingRemoval = null
                scope.launch {
                    errorMessage = when (val result = downloadRepository.remove(item.downloadId)) {
                        is DownloadOperationResult.Failure -> result.safeMessage
                        is DownloadOperationResult.Success -> null
                    }
                }
            },
            onDismiss = { pendingRemoval = null },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        OwnPlayTopBar(showTagline = false)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "‹ Settings",
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(vertical = 12.dp),
                style = MaterialTheme.typography.labelLarge,
                color = OwnPlayColors.Accent,
            )
            Text(
                "Manage downloads",
                style = MaterialTheme.typography.headlineSmall,
                color = OwnPlayColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Downloads are saved under Download/OwnPlay Downloads.",
                style = MaterialTheme.typography.bodyMedium,
                color = OwnPlayColors.TextSecondary,
            )
        }

        errorMessage?.let { message ->
            OwnPlayStatePanel(
                title = "Download action unavailable",
                message = message,
                modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg),
            )
        }

        if (downloads.isEmpty()) {
            OwnPlayStatePanel(
                title = "No downloads",
                message = "Start a movie or episode download from Library and it will appear here.",
                modifier = Modifier.padding(OwnPlaySpacing.Lg),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(downloads, key = { it.downloadId }) { item ->
                    DownloadManagementRow(
                        item = item,
                        onPrimary = {
                            if (item.state == DownloadState.COMPLETED) {
                                errorMessage = null
                                onPlayOffline(item.downloadId)
                            } else {
                                scope.launch {
                                    errorMessage = when (val result = primaryAction(downloadRepository, item)) {
                                        is DownloadOperationResult.Failure -> result.safeMessage
                                        is DownloadOperationResult.Success -> null
                                    }
                                }
                            }
                        },
                        onRemove = { pendingRemoval = item },
                    )
                }
            }
        }
    }
}

private suspend fun primaryAction(
    repository: DownloadRepository,
    item: DownloadItem,
): DownloadOperationResult = when (item.state) {
    DownloadState.DOWNLOADING, DownloadState.QUEUED -> repository.pause(item.downloadId)
    DownloadState.PAUSED -> repository.resume(item.downloadId)
    DownloadState.FAILED -> repository.retry(item.downloadId)
    DownloadState.COMPLETED -> DownloadOperationResult.Success(item)
}

@Composable
private fun DownloadManagementRow(
    item: DownloadItem,
    onPrimary: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        shape = OwnPlayShapeTokens.Small,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(48.dp)
                    .background(
                        color = when (item.state) {
                            DownloadState.COMPLETED -> OwnPlayColors.Accent
                            DownloadState.FAILED -> OwnPlayColors.AccentStrong.copy(alpha = 0.72f)
                            else -> OwnPlayColors.Divider
                        },
                        shape = OwnPlayShapeTokens.Small,
                    ),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = OwnPlayColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
                Text(
                    text = downloadStatus(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.state == DownloadState.COMPLETED) {
                        OwnPlayColors.Accent
                    } else {
                        OwnPlayColors.TextSecondary
                    },
                )
                item.progressFraction
                    ?.takeIf { item.state != DownloadState.COMPLETED }
                    ?.let { progress ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(OwnPlayColors.Divider, OwnPlayShapeTokens.Small),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .height(2.dp)
                                    .background(OwnPlayColors.Accent, OwnPlayShapeTokens.Small),
                            )
                        }
                    }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DownloadManagementAction(
                        text = primaryLabel(item.state),
                        emphasized = item.state == DownloadState.COMPLETED ||
                            item.state == DownloadState.PAUSED ||
                            item.state == DownloadState.FAILED,
                        onClick = onPrimary,
                    )
                    DownloadManagementAction(
                        text = "Remove",
                        emphasized = false,
                        onClick = onRemove,
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadManagementAction(
    text: String,
    emphasized: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
        color = if (emphasized) OwnPlayColors.Accent.copy(alpha = 0.10f) else Color.Transparent,
        shape = OwnPlayShapeTokens.Small,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (emphasized) OwnPlayColors.Accent else OwnPlayColors.TextMuted,
                maxLines = 1,
            )
        }
    }
}

private fun primaryLabel(state: DownloadState): String = when (state) {
    DownloadState.DOWNLOADING, DownloadState.QUEUED -> "Pause"
    DownloadState.PAUSED -> "Resume"
    DownloadState.FAILED -> "Retry"
    DownloadState.COMPLETED -> "Play Offline"
}

private fun downloadStatus(item: DownloadItem): String {
    val progress = item.progressFraction?.let { " · ${(it * 100).toInt()}%" }.orEmpty()
    return when (item.state) {
        DownloadState.QUEUED -> "Queued$progress"
        DownloadState.DOWNLOADING -> "Downloading$progress"
        DownloadState.PAUSED -> "Paused$progress"
        DownloadState.FAILED -> "Needs attention"
        DownloadState.COMPLETED -> "Downloaded · verified offline"
    }
}
'''
write("app/src/main/java/app/ownplay/mobile/feature/settings/ui/DownloadManagementScreen.kt", download_management)

# 4) Offline playback handoff via existing Library owner.
settings = "app/src/main/java/app/ownplay/mobile/feature/settings/ui/SettingsShell.kt"
text = read(settings)
old = '''    downloadRepository: DownloadRepository,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {'''
new = '''    downloadRepository: DownloadRepository,
    onPlayOffline: (String) -> Unit,
    modifier: Modifier = Modifier,
) {'''
if text.count(old) != 1:
    raise SystemExit("SettingsShell signature mismatch")
text = text.replace(old, new, 1)
old = '''        SettingsPage.MANAGE_DOWNLOADS -> DownloadManagementScreen(
            downloadRepository = downloadRepository,
            onOpenLibrary = onOpenLibrary,
            onBack = { pageName = SettingsPage.MAIN.name },
            modifier = modifier,
        )'''
new = '''        SettingsPage.MANAGE_DOWNLOADS -> DownloadManagementScreen(
            downloadRepository = downloadRepository,
            onPlayOffline = onPlayOffline,
            onBack = { pageName = SettingsPage.MAIN.name },
            modifier = modifier,
        )'''
if text.count(old) != 1:
    raise SystemExit("SettingsShell manage downloads mismatch")
text = text.replace(old, new, 1)
write(settings, text)

app = "app/src/main/java/app/ownplay/mobile/app/OwnPlayApp.kt"
text = read(app)
old = '''        var contentFullscreen by rememberSaveable { mutableStateOf(false) }
        var exitConfirmationVisible by rememberSaveable { mutableStateOf(false) }
        val scope = rememberCoroutineScope()'''
new = '''        var contentFullscreen by rememberSaveable { mutableStateOf(false) }
        var exitConfirmationVisible by rememberSaveable { mutableStateOf(false) }
        var pendingOfflineDownloadId by rememberSaveable { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()'''
if text.count(old) != 1:
    raise SystemExit("OwnPlayApp state mismatch")
text = text.replace(old, new, 1)
old = '''                    AppDestination.Library -> LibraryShell(
                        libraryRepository = services.libraryRepository,
                        downloadRepository = services.downloadRepository,
                        playbackController = services.playbackController,
                        resumePlaybackEnabled = settings.resumePlaybackEnabled,
                        onFullscreenChanged = ::setContentFullscreen,
                    )'''
new = '''                    AppDestination.Library -> LibraryShell(
                        libraryRepository = services.libraryRepository,
                        downloadRepository = services.downloadRepository,
                        playbackController = services.playbackController,
                        resumePlaybackEnabled = settings.resumePlaybackEnabled,
                        initialOfflineDownloadId = pendingOfflineDownloadId,
                        onInitialOfflineConsumed = { pendingOfflineDownloadId = null },
                        onFullscreenChanged = ::setContentFullscreen,
                    )'''
if text.count(old) != 1:
    raise SystemExit("OwnPlayApp LibraryShell mismatch")
text = text.replace(old, new, 1)
old = '''                        downloadRepository = services.downloadRepository,
                        onOpenLibrary = { selectedDestination = AppDestination.Library },
                    )'''
new = '''                        downloadRepository = services.downloadRepository,
                        onPlayOffline = { downloadId ->
                            pendingOfflineDownloadId = downloadId
                            selectedDestination = AppDestination.Library
                        },
                    )'''
if text.count(old) != 1:
    raise SystemExit("OwnPlayApp SettingsShell mismatch")
text = text.replace(old, new, 1)
write(app, text)

# 5) Library search, direct offline handoff, season navigation, display cleanup.
library = "app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryShell.kt"
text = read(library)
fullscreen_marker = "@Composable\nprivate fun LibraryFullscreenPlayer("
fullscreen_suffix = text[text.index(fullscreen_marker):]
text = text.replace(
    "import app.ownplay.mobile.design.OwnPlayColors\n",
    "import app.ownplay.mobile.design.OwnPlayColors\nimport app.ownplay.mobile.design.OwnPlaySearchField\n",
    1,
)
old = '''    playbackController: PlaybackController,
    resumePlaybackEnabled: Boolean,
    onFullscreenChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {'''
new = '''    playbackController: PlaybackController,
    resumePlaybackEnabled: Boolean,
    initialOfflineDownloadId: String? = null,
    onInitialOfflineConsumed: () -> Unit = {},
    onFullscreenChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {'''
if text.count(old) != 1:
    raise SystemExit("LibraryShell signature mismatch")
text = text.replace(old, new, 1)
anchor = '''    LaunchedEffect(selectedSeriesId) {
        val seriesId = selectedSeriesId'''
insertion = '''    LaunchedEffect(initialOfflineDownloadId) {
        val downloadId = initialOfflineDownloadId ?: return@LaunchedEffect
        resolutionError = null
        acceptResolution(
            downloadRepository.resolveOfflinePlayback(
                downloadId = downloadId,
                startMode = LibraryStartMode.RESUME,
            ),
        )
        onInitialOfflineConsumed()
    }

    LaunchedEffect(selectedSeriesId) {
        val seriesId = selectedSeriesId'''
if text.count(anchor) != 1:
    raise SystemExit("LibraryShell initial offline anchor mismatch")
text = text.replace(anchor, insertion, 1)
old = '''    var selectedMovieCategoryKey by remember(catalog?.activeSourceId) { mutableStateOf<String?>(null) }
    var selectedSeriesCategoryKey by remember(catalog?.activeSourceId) { mutableStateOf<String?>(null) }
    val rawMovieCategories = catalog?.movieCategories.orEmpty()'''
new = '''    var selectedMovieCategoryKey by remember(catalog?.activeSourceId) { mutableStateOf<String?>(null) }
    var selectedSeriesCategoryKey by remember(catalog?.activeSourceId) { mutableStateOf<String?>(null) }
    var searchVisible by remember(catalog?.activeSourceId) { mutableStateOf(false) }
    var searchQuery by remember(catalog?.activeSourceId) { mutableStateOf("") }
    val normalizedSearchQuery = searchQuery.trim()
    val searchActive = normalizedSearchQuery.isNotEmpty()
    val rawMovieCategories = catalog?.movieCategories.orEmpty()'''
if text.count(old) != 1:
    raise SystemExit("Library search state mismatch")
text = text.replace(old, new, 1)
old = '''    val visibleMovies = catalog?.movies.orEmpty().let { movies ->
        activeMovieCategoryKey?.let { key -> movies.filter { it.categoryKey == key } } ?: movies
    }
    val visibleSeries = catalog?.series.orEmpty().let { series ->
        activeSeriesCategoryKey?.let { key -> series.filter { it.categoryKey == key } } ?: series
    }'''
new = '''    val visibleMovies = catalog?.movies.orEmpty().let { movies ->
        if (searchActive) {
            movies.filter { it.name.contains(normalizedSearchQuery, ignoreCase = true) }
        } else {
            activeMovieCategoryKey?.let { key -> movies.filter { it.categoryKey == key } } ?: movies
        }
    }
    val visibleSeries = catalog?.series.orEmpty().let { series ->
        if (searchActive) {
            series.filter { it.name.contains(normalizedSearchQuery, ignoreCase = true) }
        } else {
            activeSeriesCategoryKey?.let { key -> series.filter { it.categoryKey == key } } ?: series
        }
    }'''
if text.count(old) != 1:
    raise SystemExit("Library visible media mismatch")
text = text.replace(old, new, 1)
old = '''        OwnPlayTopBar(showTagline = false)

        Column(
            modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Lg),
        ) {
            if (errorMessage != null) {'''
new = '''        OwnPlayTopBar(
            showTagline = false,
            onSearchClick = {
                searchVisible = !searchVisible
                if (!searchVisible) searchQuery = ""
            },
        )

        Column(
            modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Lg),
        ) {
            if (searchVisible) {
                OwnPlaySearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClose = {
                        searchQuery = ""
                        searchVisible = false
                    },
                    placeholder = "Search movies and series",
                )
            }
            if (errorMessage != null) {'''
if text.count(old) != 1:
    raise SystemExit("Library top bar mismatch")
text = text.replace(old, new, 1)
old = '''                    LibraryShelfSection(
                        title = "Movies",
                        actionLabel = catalog.movies.size
                            .takeIf { it > 0 }
                            ?.let { "${compactLibraryCount(it)} titles" },
                    ) {
                        if (movieCategories.isNotEmpty()) {'''
new = '''                    LibraryShelfSection(
                        title = "Movies",
                        actionLabel = if (searchActive) {
                            "${compactLibraryCount(visibleMovies.size)} matches"
                        } else {
                            catalog.movies.size
                                .takeIf { it > 0 }
                                ?.let { "${compactLibraryCount(it)} titles" }
                        },
                    ) {
                        if (!searchActive && movieCategories.isNotEmpty()) {'''
if text.count(old) != 1:
    raise SystemExit("Movies header mismatch")
text = text.replace(old, new, 1)
old = '''                            else -> LibraryShelfState(
                                title = "No movies in this category",
                                message = "Choose another provider category.",
                            )'''
new = '''                            else -> LibraryShelfState(
                                title = if (searchActive) "No movie matches" else "No movies in this category",
                                message = if (searchActive) {
                                    "Try another title or close search to browse categories."
                                } else {
                                    "Choose another provider category."
                                },
                            )'''
if text.count(old) != 1:
    raise SystemExit("Movies empty mismatch")
text = text.replace(old, new, 1)
old = '''                    LibraryShelfSection(
                        title = "Series",
                        actionLabel = catalog.series.size
                            .takeIf { it > 0 }
                            ?.let { "${compactLibraryCount(it)} titles" },
                    ) {
                        if (seriesCategories.isNotEmpty()) {'''
new = '''                    LibraryShelfSection(
                        title = "Series",
                        actionLabel = if (searchActive) {
                            "${compactLibraryCount(visibleSeries.size)} matches"
                        } else {
                            catalog.series.size
                                .takeIf { it > 0 }
                                ?.let { "${compactLibraryCount(it)} titles" }
                        },
                    ) {
                        if (!searchActive && seriesCategories.isNotEmpty()) {'''
if text.count(old) != 1:
    raise SystemExit("Series header mismatch")
text = text.replace(old, new, 1)
old = '''                            else -> LibraryShelfState(
                                title = "No series in this category",
                                message = "Choose another provider category.",
                            )'''
new = '''                            else -> LibraryShelfState(
                                title = if (searchActive) "No series matches" else "No series in this category",
                                message = if (searchActive) {
                                    "Try another title or close search to browse categories."
                                } else {
                                    "Choose another provider category."
                                },
                            )'''
if text.count(old) != 1:
    raise SystemExit("Series empty mismatch")
text = text.replace(old, new, 1)

series_anchor = '''    BackHandler(onBack = onBack)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {'''
series_replacement = '''    BackHandler(onBack = onBack)
    val episodes = detail?.episodes.orEmpty()
    val seasons = remember(episodes) {
        episodes.map { it.seasonNumber }.distinct().sorted()
    }
    var selectedSeasonNumber by remember(series.seriesId) { mutableStateOf<Int?>(null) }
    LaunchedEffect(seasons) {
        if (selectedSeasonNumber !in seasons) {
            selectedSeasonNumber = seasons.firstOrNull()
        }
    }
    val visibleEpisodes = selectedSeasonNumber?.let { selectedSeason ->
        episodes.filter { it.seasonNumber == selectedSeason }
    } ?: episodes

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {'''
parts = text.split(series_anchor)
if len(parts) != 3:
    raise SystemExit(f"SeriesDetail anchor expected twice, found {len(parts)-1}")
text = parts[0] + series_anchor + parts[1] + series_replacement + parts[2]
old = '''            LibraryShelfHeader(
                title = "Episodes",
                actionLabel = detail?.episodes?.size?.takeIf { it > 0 }?.let { "${it} episodes" },
            )
            when {'''
new = '''            LibraryShelfHeader(
                title = "Episodes",
                actionLabel = visibleEpisodes.size.takeIf { it > 0 }?.let { "${it} episodes" },
            )
            if (seasons.size > 1) {
                SeasonStrip(
                    seasons = seasons,
                    selectedSeasonNumber = selectedSeasonNumber,
                    onSelected = { selectedSeasonNumber = it },
                )
            }
            when {'''
if text.count(old) != 1:
    raise SystemExit("Episode header mismatch")
text = text.replace(old, new, 1)
old = '''                else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    detail.episodes.forEach { episode ->'''
new = '''                else -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    visibleEpisodes.forEach { episode ->'''
if text.count(old) != 1:
    raise SystemExit("Episode iteration mismatch")
text = text.replace(old, new, 1)
episode_marker = '''@Composable
private fun EpisodeRow(
'''
season_strip = '''@Composable
private fun SeasonStrip(
    seasons: List<Int>,
    selectedSeasonNumber: Int?,
    onSelected: (Int) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(seasons, key = { it }) { seasonNumber ->
            LibraryFilterTab(
                label = "Season $seasonNumber",
                selected = selectedSeasonNumber == seasonNumber,
                onClick = { onSelected(seasonNumber) },
            )
        }
    }
}

private fun episodeDisplayTitle(episode: LibraryEpisode): String {
    val original = episode.title.trim()
    if (original.isBlank()) return "Episode ${episode.episodeNumber}"
    val seasonToken = "S${episode.seasonNumber.toString().padStart(2, '0')}" +
        "E${episode.episodeNumber.toString().padStart(2, '0')}"
    var candidate = original
    if (candidate.startsWith(episode.seriesName, ignoreCase = true)) {
        candidate = candidate.drop(episode.seriesName.length)
            .trimStart(' ', '-', '–', '—', '•', ':')
    }
    val tokenIndex = candidate.indexOf(seasonToken, ignoreCase = true)
    if (tokenIndex in 0..8) {
        candidate = candidate.substring(tokenIndex + seasonToken.length)
            .trimStart(' ', '-', '–', '—', '•', ':')
    }
    return candidate.ifBlank { original }
}

@Composable
private fun EpisodeRow(
'''
if text.count(episode_marker) != 1:
    raise SystemExit("EpisodeRow marker mismatch")
text = text.replace(episode_marker, season_strip, 1)
old = '''    val primaryIsResume = hasProgress && preferResume
    val primaryLabel = if (primaryIsResume) "Resume" else "Play"
    val primaryAction = if (primaryIsResume) onResume else onBeginning

    Surface('''
new = '''    val primaryIsResume = hasProgress && preferResume
    val primaryLabel = if (primaryIsResume) "Resume" else "Play"
    val primaryAction = if (primaryIsResume) onResume else onBeginning
    val displayTitle = episodeDisplayTitle(episode)

    Surface('''
if text.count(old) != 1:
    raise SystemExit("Episode display title anchor mismatch")
text = text.replace(old, new, 1)
if text.count("text = episode.title,") != 1:
    raise SystemExit("Episode text count mismatch")
text = text.replace("text = episode.title,", "text = displayTitle,", 1)
if text.count('contentDescription = "$primaryLabel ${episode.title}",') != 1:
    raise SystemExit("Episode content description mismatch")
text = text.replace(
    'contentDescription = "$primaryLabel ${episode.title}",',
    'contentDescription = "$primaryLabel $displayTitle",',
    1,
)
new_suffix = text[text.index(fullscreen_marker):]
if new_suffix != fullscreen_suffix:
    raise SystemExit("Library fullscreen suffix changed unexpectedly")
write(library, text)

# 6) Live search across channels, no change to fullscreen implementation.
live = "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt"
text = read(live)
fullscreen_live_marker = "@Composable\nprivate fun FullscreenLive("
fullscreen_live_suffix = text[text.index(fullscreen_live_marker):]
text = text.replace(
    "import app.ownplay.mobile.design.OwnPlaySectionHeader\n",
    "import app.ownplay.mobile.design.OwnPlaySearchField\nimport app.ownplay.mobile.design.OwnPlaySectionHeader\n",
    1,
)
old = '''    var fallbackLoadRequest by remember { mutableStateOf<PlaybackLoadRequest?>(null) }
    var waitingForInitialChannels by remember(catalog?.activeSourceId) { mutableStateOf(false) }
    var orientationFullscreenArmed by remember { mutableStateOf(true) }'''
new = '''    var fallbackLoadRequest by remember { mutableStateOf<PlaybackLoadRequest?>(null) }
    var waitingForInitialChannels by remember(catalog?.activeSourceId) { mutableStateOf(false) }
    var orientationFullscreenArmed by remember { mutableStateOf(true) }
    var searchVisible by remember(catalog?.activeSourceId) { mutableStateOf(false) }
    var searchQuery by remember(catalog?.activeSourceId) { mutableStateOf("") }'''
if text.count(old) != 1:
    raise SystemExit("Live search state mismatch")
text = text.replace(old, new, 1)
old = '''    val activeCategoryKey = LiveBrowsePolicy.activeCategoryKey(categories, selectedCategoryKey)
    val visibleChannels = activeCategoryKey?.let { key ->
        channels.filter { channel -> channel.categoryKey == key }
    } ?: channels
    val selectedChannel = channels.firstOrNull { it.channelId == presentationState.selectedChannelId }'''
new = '''    val activeCategoryKey = LiveBrowsePolicy.activeCategoryKey(categories, selectedCategoryKey)
    val normalizedSearchQuery = searchQuery.trim()
    val searchActive = normalizedSearchQuery.isNotEmpty()
    val categoryChannels = activeCategoryKey?.let { key ->
        channels.filter { channel -> channel.categoryKey == key }
    } ?: channels
    val visibleChannels = if (searchActive) {
        channels.filter { channel -> channel.name.contains(normalizedSearchQuery, ignoreCase = true) }
    } else {
        categoryChannels
    }
    val showCategories = categories.isNotEmpty() && !searchActive
    val selectedChannel = channels.firstOrNull { it.channelId == presentationState.selectedChannelId }'''
if text.count(old) != 1:
    raise SystemExit("Live visible channels mismatch")
text = text.replace(old, new, 1)
old = '''            channels = visibleChannels,
            categories = categories,
            liveRepository = liveRepository,
            selectedCategoryKey = activeCategoryKey,
            onCategorySelected = { categoryKey ->'''
new = '''            channels = visibleChannels,
            categories = categories,
            liveRepository = liveRepository,
            selectedCategoryKey = activeCategoryKey,
            searchVisible = searchVisible,
            searchQuery = searchQuery,
            showCategories = showCategories,
            onSearchToggle = {
                searchVisible = !searchVisible
                if (!searchVisible) searchQuery = ""
            },
            onSearchQueryChange = { searchQuery = it },
            onCategorySelected = { categoryKey ->'''
if text.count(old) != 1:
    raise SystemExit("LiveBrowse call mismatch")
text = text.replace(old, new, 1)
old = '''    liveRepository: LiveRepository,
    selectedCategoryKey: String?,
    onCategorySelected: (String?) -> Unit,
    selectedChannel: LiveChannel?,'''
new = '''    liveRepository: LiveRepository,
    selectedCategoryKey: String?,
    searchVisible: Boolean,
    searchQuery: String,
    showCategories: Boolean,
    onSearchToggle: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    selectedChannel: LiveChannel?,'''
if text.count(old) != 1:
    raise SystemExit("LiveBrowse signature mismatch")
text = text.replace(old, new, 1)
old = '''        val fixedItemsBeforeChannels = 2 + if (categories.isNotEmpty()) 1 else 0'''
new = '''        val fixedItemsBeforeChannels = 2 + if (showCategories) 1 else 0'''
if text.count(old) != 1:
    raise SystemExit("Live fixed item count mismatch")
text = text.replace(old, new, 1)
old = '''        item { OwnPlayTopBar(showTagline = false) }

        item {
            Box(modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg)) {
                OwnPlaySectionHeader(
                    title = "Live",
                    actionLabel = catalog?.channels?.size?.takeIf { it > 0 }?.let { "$it channels" },
                )
            }
        }

        if (categories.isNotEmpty()) {'''
new = '''        item {
            OwnPlayTopBar(
                showTagline = false,
                onSearchClick = onSearchToggle,
            )
        }

        item {
            Column(
                modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs),
            ) {
                OwnPlaySectionHeader(
                    title = "Live",
                    actionLabel = if (searchQuery.isNotBlank()) {
                        "${channels.size} matches"
                    } else {
                        catalog?.channels?.size?.takeIf { it > 0 }?.let { "$it channels" }
                    },
                )
                if (searchVisible) {
                    OwnPlaySearchField(
                        query = searchQuery,
                        onQueryChange = onSearchQueryChange,
                        onClose = {
                            onSearchQueryChange("")
                            onSearchToggle()
                        },
                        placeholder = "Search channels",
                    )
                }
            }
        }

        if (showCategories) {'''
if text.count(old) != 1:
    raise SystemExit("Live top/search block mismatch")
text = text.replace(old, new, 1)
old = '''            channels.isEmpty() -> item {
                Box(modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm)) {
                    OwnPlayStatePanel(
                        title = "No channels in this category",
                        message = "Choose another provider category.",
                    )
                }
            }'''
new = '''            channels.isEmpty() -> item {
                Box(modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm)) {
                    OwnPlayStatePanel(
                        title = if (searchQuery.isNotBlank()) "No channel matches" else "No channels in this category",
                        message = if (searchQuery.isNotBlank()) {
                            "Try another channel name or close search to browse categories."
                        } else {
                            "Choose another provider category."
                        },
                    )
                }
            }'''
if text.count(old) != 1:
    raise SystemExit("Live empty state mismatch")
text = text.replace(old, new, 1)
new_live_suffix = text[text.index(fullscreen_live_marker):]
if new_live_suffix != fullscreen_live_suffix:
    raise SystemExit("Live fullscreen suffix changed unexpectedly")
write(live, text)

# 7) Source-management text actions get 48dp targets.
source = "app/src/main/java/app/ownplay/mobile/feature/settings/ui/SourceManagementScreen.kt"
text = read(source)
text = text.replace(
    "import androidx.compose.foundation.layout.height\n",
    "import androidx.compose.foundation.layout.height\nimport androidx.compose.foundation.layout.heightIn\n",
    1,
)
text = text.replace(
    "import androidx.compose.ui.Modifier\n",
    "import androidx.compose.ui.Modifier\nimport androidx.compose.ui.semantics.Role\n",
    1,
)
old = '''        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = OwnPlaySpacing.Sm),'''
new = '''        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = OwnPlaySpacing.Sm),'''
if text.count(old) != 1:
    raise SystemExit("SourceAction mismatch")
text = text.replace(old, new, 1)
old = '''            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(vertical = OwnPlaySpacing.Sm),'''
new = '''            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onBack)
                .padding(vertical = OwnPlaySpacing.Sm),'''
if text.count(old) != 1:
    raise SystemExit("SettingsSubpageHeader mismatch")
text = text.replace(old, new, 1)
write(source, text)

audit = r'''# Stage 19 — Search, Downloads and Episode Navigation Source Audit

Base authority: `stage-18-public-download-storage` at `67c52776261ba6f431006b3d3734ab4ad7a6a903`.

Scope:
- make top-bar Search a real action and remove dead header affordances where no callback exists;
- add local Library search across movies and series;
- add local Live search across channels while preserving the Live reducer and Preview/fullscreen behavior;
- redesign Settings > Manage downloads into compact progress rows;
- route completed Manage Downloads items into the existing Library offline playback owner rather than creating a second player session;
- require confirmation before deleting a managed download;
- add season filtering for series detail and reduce redundant episode-title text;
- make compact download controls and shared action targets respect a 48dp interaction target.

Explicit boundaries:
- no APK/AAB generation is authorized by this stage;
- no Room schema/database version change;
- no Stage 18 public Downloads storage behavior change;
- no provider/auth/backup change;
- no playback-controller/session architecture replacement;
- `LibraryFullscreenPlayer` is preserved byte-for-byte by the deterministic patch guard;
- `FullscreenLive` and everything after it are preserved byte-for-byte by the deterministic patch guard;
- no merge, ready-for-review, release, deployment, signing or version change.

Acceptance:
- exact final HEAD must pass compileDebugKotlin, compileDebugUnitTestKotlin, testDebugUnitTest, lintDebug and Room schema cleanliness through the standard no-APK validation workflow;
- workflow artifacts must remain zero;
- physical visual/interaction acceptance remains NOT_YET_VERIFIED until a later explicitly authorized QA APK is tested on device.
'''
write("docs/audit/STAGE19_SEARCH_DOWNLOADS_SEASONS_SOURCE_AUDIT.md", audit)

expected = {
    "app/src/main/java/app/ownplay/mobile/design/OwnPlayComponents.kt",
    "app/src/main/java/app/ownplay/mobile/design/OwnPlaySearchField.kt",
    "app/src/main/java/app/ownplay/mobile/downloads/ui/DownloadControls.kt",
    "app/src/main/java/app/ownplay/mobile/feature/settings/ui/DownloadManagementScreen.kt",
    "app/src/main/java/app/ownplay/mobile/feature/settings/ui/SettingsShell.kt",
    "app/src/main/java/app/ownplay/mobile/app/OwnPlayApp.kt",
    "app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryShell.kt",
    "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt",
    "app/src/main/java/app/ownplay/mobile/feature/settings/ui/SourceManagementScreen.kt",
    "docs/audit/STAGE19_SEARCH_DOWNLOADS_SEASONS_SOURCE_AUDIT.md",
}
if changed != expected:
    raise SystemExit(
        f"changed file set mismatch; missing={sorted(expected - changed)}, extra={sorted(changed - expected)}"
    )
print("Stage 19 deterministic patch applied")
