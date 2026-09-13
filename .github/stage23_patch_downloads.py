from pathlib import Path


def replace_once(path_str: str, old: str, new: str) -> None:
    path = Path(path_str)
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected 1 match, found {count}: {old[:100]!r}")
    path.write_text(text.replace(old, new, 1))


actions = "app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryActions.kt"
replace_once(
    actions,
    '''internal enum class LibraryActionGlyph {
    PLAY,
    RESTART,
    BACK,
}''',
    '''internal enum class LibraryActionGlyph {
    PLAY,
    RESTART,
    BACK,
    DISMISS,
}''',
)
replace_once(
    actions,
    '''internal fun LibraryShelfSection(
    title: String,
    actionLabel: String? = null,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {''',
    '''internal fun LibraryShelfSection(
    title: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {''',
)
replace_once(
    actions,
    '''            title = title,
            actionLabel = actionLabel,
            prominent = prominent,
''',
    '''            title = title,
            actionLabel = actionLabel,
            onActionClick = onActionClick,
            prominent = prominent,
''',
)
replace_once(
    actions,
    '''internal fun LibraryShelfHeader(
    title: String,
    actionLabel: String? = null,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
) {''',
    '''internal fun LibraryShelfHeader(
    title: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
) {''',
)
replace_once(
    actions,
    '''        actionLabel?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = OwnPlayColors.TextMuted.copy(alpha = 0.72f),
                maxLines = 1,
            )
        }
''',
    '''        actionLabel?.let { label ->
            Text(
                text = label,
                modifier = if (onActionClick != null) {
                    Modifier
                        .clickable(role = Role.Button, onClick = onActionClick)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                } else {
                    Modifier
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (onActionClick != null) {
                    OwnPlayColors.Accent
                } else {
                    OwnPlayColors.TextMuted.copy(alpha = 0.72f)
                },
                fontWeight = if (onActionClick != null) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
        }
''',
)
replace_once(
    actions,
    '''        LibraryActionGlyph.PLAY -> "▶"
        LibraryActionGlyph.RESTART -> "↺"
        LibraryActionGlyph.BACK -> "‹"
''',
    '''        LibraryActionGlyph.PLAY -> "▶"
        LibraryActionGlyph.RESTART -> "↺"
        LibraryActionGlyph.BACK -> "‹"
        LibraryActionGlyph.DISMISS -> "×"
''',
)

offline = "app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryOfflineControls.kt"
replace_once(
    offline,
    '''internal fun LibraryOfflineControls(
    item: DownloadItem,
    onAction: (DownloadAction) -> Unit,
    modifier: Modifier = Modifier,
) {''',
    '''internal fun LibraryOfflineControls(
    item: DownloadItem,
    onAction: (DownloadAction) -> Unit,
    onHideFromLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {''',
)
replace_once(
    offline,
    '''            LibrarySecondaryAction(
                text = "Remove",
                onClick = { onAction(DownloadAction.REMOVE) },
                modifier = Modifier.weight(0.66f),
            )
''',
    '''            LibrarySecondaryAction(
                text = "Hide",
                onClick = onHideFromLibrary,
                modifier = Modifier.weight(0.66f),
            )
''',
)

downloads = "app/src/main/java/app/ownplay/mobile/feature/settings/ui/DownloadManagementScreen.kt"
replace_once(
    downloads,
    "import app.ownplay.mobile.design.OwnPlayTopBar\nimport app.ownplay.mobile.downloads.domain.DownloadItem",
    "import app.ownplay.mobile.design.OwnPlayTopBar\nimport app.ownplay.mobile.data.prefs.LibraryVisibilityPreferences\nimport app.ownplay.mobile.data.prefs.LibraryVisibilitySnapshot\nimport app.ownplay.mobile.downloads.domain.DownloadItem",
)
replace_once(
    downloads,
    '''fun DownloadManagementScreen(
    downloadRepository: DownloadRepository,
    onPlayOffline: (String) -> Unit,
''',
    '''fun DownloadManagementScreen(
    downloadRepository: DownloadRepository,
    libraryVisibilityPreferences: LibraryVisibilityPreferences,
    onPlayOffline: (String) -> Unit,
''',
)
replace_once(
    downloads,
    '''    val downloadsFlow = remember(downloadRepository) { downloadRepository.observeDownloads() }
    val downloads by downloadsFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
''',
    '''    val downloadsFlow = remember(downloadRepository) { downloadRepository.observeDownloads() }
    val downloads by downloadsFlow.collectAsState(initial = emptyList())
    val visibilityFlow = remember(libraryVisibilityPreferences) { libraryVisibilityPreferences.visibility }
    val visibility by visibilityFlow.collectAsState(initial = LibraryVisibilitySnapshot())
    val scope = rememberCoroutineScope()
''',
)
replace_once(
    downloads,
    '''        OwnPlayModal(
            title = "Remove download?",
            message = "Remove ${item.title} from this device and OwnPlay Downloads?",
            confirmLabel = "Remove",
''',
    '''        OwnPlayModal(
            title = "Delete download?",
            message = "Delete ${item.title} from this device and OwnPlay Downloads? This removes the offline file.",
            confirmLabel = "Delete",
''',
)
replace_once(
    downloads,
    '''                    DownloadManagementRow(
                        item = item,
                        onPrimary = {
''',
    '''                    DownloadManagementRow(
                        item = item,
                        hiddenFromLibrary = visibility.isDownloadHidden(item.downloadId),
                        onPrimary = {
''',
)
replace_once(
    downloads,
    '''                        },
                        onRemove = { pendingRemoval = item },
                    )
''',
    '''                        },
                        onToggleLibraryVisibility = {
                            scope.launch {
                                if (visibility.isDownloadHidden(item.downloadId)) {
                                    libraryVisibilityPreferences.showDownload(item.downloadId)
                                } else {
                                    libraryVisibilityPreferences.hideDownload(item.downloadId)
                                }
                            }
                        },
                        onRemove = { pendingRemoval = item },
                    )
''',
)
replace_once(
    downloads,
    '''private fun DownloadManagementRow(
    item: DownloadItem,
    onPrimary: () -> Unit,
    onRemove: () -> Unit,
) {''',
    '''private fun DownloadManagementRow(
    item: DownloadItem,
    hiddenFromLibrary: Boolean,
    onPrimary: () -> Unit,
    onToggleLibraryVisibility: () -> Unit,
    onRemove: () -> Unit,
) {''',
)
replace_once(downloads, '                    text = downloadStatus(item),', '                    text = downloadStatus(item, hiddenFromLibrary),')
replace_once(
    downloads,
    '''                    DownloadManagementAction(
                        text = "Remove",
                        emphasized = false,
                        onClick = onRemove,
                    )
''',
    '''                    if (item.state == DownloadState.COMPLETED) {
                        DownloadManagementAction(
                            text = if (hiddenFromLibrary) "Show in Library" else "Hide from Library",
                            emphasized = false,
                            onClick = onToggleLibraryVisibility,
                        )
                    }
                    DownloadManagementAction(
                        text = "Delete",
                        emphasized = false,
                        onClick = onRemove,
                    )
''',
)
replace_once(
    downloads,
    '''private fun downloadStatus(item: DownloadItem): String {
    val progress = item.progressFraction?.let { " · ${(it * 100).toInt()}%" }.orEmpty()
    return when (item.state) {''',
    '''private fun downloadStatus(item: DownloadItem, hiddenFromLibrary: Boolean): String {
    val progress = item.progressFraction?.let { " · ${(it * 100).toInt()}%" }.orEmpty()
    val libraryVisibility = if (item.state == DownloadState.COMPLETED && hiddenFromLibrary) {
        " · hidden from Library"
    } else {
        ""
    }
    return when (item.state) {''',
)
replace_once(
    downloads,
    '        DownloadState.COMPLETED -> "Downloaded · verified offline"',
    '        DownloadState.COMPLETED -> "Downloaded · verified offline$libraryVisibility"',
)
