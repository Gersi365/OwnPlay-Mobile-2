from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)


def remove_imports(text: str, imports: list[str], label: str) -> str:
    for line in imports:
        text = replace_once(text, line + "\n", "", f"{label}: {line}")
    return text


root = Path(".")

# 1) Pure permission policy: DOWNLOAD/RESUME/RETRY are start/restart actions.
path = root / "app/src/main/java/app/ownplay/mobile/downloads/domain/DownloadPermissionPolicy.kt"
text = path.read_text()
text = replace_once(
    text,
    """        else -> DownloadPermissionPrompt.NONE\n    }\n}\n""",
    """        else -> DownloadPermissionPrompt.NONE\n    }\n\n    fun requiresPermissionCheck(action: DownloadAction): Boolean = when (action) {\n        DownloadAction.DOWNLOAD,\n        DownloadAction.RESUME,\n        DownloadAction.RETRY,\n        -> true\n\n        DownloadAction.PAUSE,\n        DownloadAction.REMOVE,\n        DownloadAction.PLAY_OFFLINE,\n        DownloadAction.RESUME_OFFLINE,\n        -> false\n    }\n}\n""",
    "permission action policy",
)
path.write_text(text)

# 2) Add focused policy coverage.
path = root / "app/src/test/java/app/ownplay/mobile/downloads/domain/DownloadPermissionPolicyTest.kt"
text = path.read_text()
insert = """
    @Test
    fun `permission check is limited to download start and restart actions`() {
        assertEquals(true, DownloadPermissionPolicy.requiresPermissionCheck(DownloadAction.DOWNLOAD))
        assertEquals(true, DownloadPermissionPolicy.requiresPermissionCheck(DownloadAction.RESUME))
        assertEquals(true, DownloadPermissionPolicy.requiresPermissionCheck(DownloadAction.RETRY))
        assertEquals(false, DownloadPermissionPolicy.requiresPermissionCheck(DownloadAction.PAUSE))
        assertEquals(false, DownloadPermissionPolicy.requiresPermissionCheck(DownloadAction.REMOVE))
        assertEquals(false, DownloadPermissionPolicy.requiresPermissionCheck(DownloadAction.PLAY_OFFLINE))
        assertEquals(false, DownloadPermissionPolicy.requiresPermissionCheck(DownloadAction.RESUME_OFFLINE))
    }
"""
text = replace_once(text, "\n}\n", insert + "\n}\n", "permission policy test class close")
path.write_text(text)

# 3) Shared Android/Compose runtime permission dispatcher.
path = root / "app/src/main/java/app/ownplay/mobile/downloads/ui/DownloadPermissionDispatcher.kt"
if path.exists():
    raise RuntimeError("DownloadPermissionDispatcher.kt already exists")
path.write_text("""package app.ownplay.mobile.downloads.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.ownplay.mobile.data.prefs.SettingsPreferences
import app.ownplay.mobile.downloads.domain.DownloadAction
import app.ownplay.mobile.downloads.domain.DownloadPermissionPolicy
import app.ownplay.mobile.downloads.domain.DownloadPermissionPrompt
import kotlinx.coroutines.launch

private const val LEGACY_STORAGE_BLOCKED_MESSAGE =
    "Storage permission is required to save public downloads on Android 8 or 9."

private class PendingDownloadPermissionRequest {
    var action: DownloadAction? = null
    var onAllowed: ((DownloadAction) -> Unit)? = null
    var notificationPromptedInSession: Boolean = false

    val active: Boolean
        get() = action != null

    fun set(action: DownloadAction, onAllowed: (DownloadAction) -> Unit) {
        this.action = action
        this.onAllowed = onAllowed
    }

    fun take(): Pair<DownloadAction, (DownloadAction) -> Unit>? {
        val currentAction = action ?: return null
        val currentCallback = onAllowed ?: return null
        action = null
        onAllowed = null
        return currentAction to currentCallback
    }

    fun clear() {
        action = null
        onAllowed = null
    }
}

@Composable
internal fun rememberDownloadPermissionDispatcher(
    onBlocked: (String) -> Unit,
): (DownloadAction, (DownloadAction) -> Unit) -> Unit {
    val context = LocalContext.current
    val permissionPreferences = remember(context) { SettingsPreferences(context.applicationContext) }
    val notificationPermissionPrompted by permissionPreferences.downloadNotificationPermissionPrompted
        .collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    val pending = remember { PendingDownloadPermissionRequest() }
    val currentOnBlocked by rememberUpdatedState(onBlocked)

    fun completeAllowed() {
        val request = pending.take() ?: return
        request.second(request.first)
    }

    fun completeBlocked(message: String) {
        pending.clear()
        currentOnBlocked(message)
    }

    val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            completeAllowed()
        } else {
            completeBlocked(LEGACY_STORAGE_BLOCKED_MESSAGE)
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        // Notification permission is advisory for downloads: denial must not block the work.
        completeAllowed()
    }

    return { action, onAllowed ->
        when {
            !DownloadPermissionPolicy.requiresPermissionCheck(action) -> onAllowed(action)
            pending.active -> Unit
            else -> {
                val legacyStorageGranted = Build.VERSION.SDK_INT > Build.VERSION_CODES.P ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    ) == PackageManager.PERMISSION_GRANTED
                val notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED

                when (
                    DownloadPermissionPolicy.nextPrompt(
                        sdkInt = Build.VERSION.SDK_INT,
                        legacyStorageGranted = legacyStorageGranted,
                        notificationsGranted = notificationsGranted,
                        notificationPermissionPrompted = notificationPermissionPrompted ||
                            pending.notificationPromptedInSession,
                    )
                ) {
                    DownloadPermissionPrompt.NONE -> onAllowed(action)
                    DownloadPermissionPrompt.LEGACY_PUBLIC_STORAGE -> {
                        pending.set(action, onAllowed)
                        runCatching {
                            legacyStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }.onFailure {
                            completeBlocked(LEGACY_STORAGE_BLOCKED_MESSAGE)
                        }
                    }
                    DownloadPermissionPrompt.NOTIFICATIONS -> {
                        pending.set(action, onAllowed)
                        pending.notificationPromptedInSession = true
                        scope.launch {
                            runCatching { permissionPreferences.markDownloadNotificationPermissionPrompted() }
                        }
                        runCatching {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }.onFailure {
                            // A notification prompt failure is still non-blocking for the download.
                            completeAllowed()
                        }
                    }
                }
            }
        }
    }
}
""")

# 4) Reuse dispatcher in Library/detail DownloadControls for DOWNLOAD/RESUME/RETRY.
path = root / "app/src/main/java/app/ownplay/mobile/downloads/ui/DownloadControls.kt"
text = path.read_text()
text = remove_imports(
    text,
    [
        "import android.Manifest",
        "import android.content.pm.PackageManager",
        "import android.os.Build",
        "import androidx.activity.compose.rememberLauncherForActivityResult",
        "import androidx.activity.result.contract.ActivityResultContracts",
        "import androidx.compose.runtime.collectAsState",
        "import androidx.compose.runtime.rememberCoroutineScope",
        "import androidx.compose.ui.platform.LocalContext",
        "import androidx.core.content.ContextCompat",
        "import app.ownplay.mobile.data.prefs.SettingsPreferences",
        "import app.ownplay.mobile.downloads.domain.DownloadPermissionPolicy",
        "import app.ownplay.mobile.downloads.domain.DownloadPermissionPrompt",
        "import kotlinx.coroutines.launch",
    ],
    "DownloadControls imports",
)
start_marker = "    val context = LocalContext.current\n"
end_marker = "    val primaryAction = DownloadStatePolicy.primaryAction(item)\n"
start = text.find(start_marker)
end = text.find(end_marker)
if start < 0 or end < 0 or end <= start:
    raise RuntimeError("DownloadControls permission block markers not found")
replacement = """    var permissionMessage by remember { mutableStateOf<String?>(null) }
    val permissionDispatcher = rememberDownloadPermissionDispatcher(
        onBlocked = { message -> permissionMessage = message },
    )

    fun dispatchAction(action: DownloadAction) {
        permissionMessage = null
        permissionDispatcher(action, onAction)
    }

"""
text = text[:start] + replacement + text[end:]
path.write_text(text)

# 5) Apply the same gate to Settings > Manage downloads primary actions.
path = root / "app/src/main/java/app/ownplay/mobile/feature/settings/ui/DownloadManagementScreen.kt"
text = path.read_text()
text = replace_once(
    text,
    "import app.ownplay.mobile.downloads.domain.DownloadItem\n",
    "import app.ownplay.mobile.downloads.domain.DownloadAction\nimport app.ownplay.mobile.downloads.domain.DownloadItem\n",
    "DownloadManagement DownloadAction import",
)
text = replace_once(
    text,
    "import app.ownplay.mobile.downloads.domain.DownloadRepository\n",
    "import app.ownplay.mobile.downloads.domain.DownloadRepository\nimport app.ownplay.mobile.downloads.domain.DownloadStatePolicy\nimport app.ownplay.mobile.downloads.ui.rememberDownloadPermissionDispatcher\n",
    "DownloadManagement permission imports",
)
text = replace_once(
    text,
    """    var errorMessage by remember { mutableStateOf<String?>(null) }\n    var pendingRemoval by remember { mutableStateOf<DownloadItem?>(null) }\n    BackHandler(onBack = onBack)\n""",
    """    var errorMessage by remember { mutableStateOf<String?>(null) }\n    var pendingRemoval by remember { mutableStateOf<DownloadItem?>(null) }\n    val permissionDispatcher = rememberDownloadPermissionDispatcher(\n        onBlocked = { message -> errorMessage = message },\n    )\n    BackHandler(onBack = onBack)\n""",
    "DownloadManagement dispatcher state",
)
old_primary = """                        onPrimary = {\n                            if (item.state == DownloadState.COMPLETED) {\n                                errorMessage = null\n                                onPlayOffline(item.downloadId)\n                            } else {\n                                scope.launch {\n                                    errorMessage = when (val result = primaryAction(downloadRepository, item)) {\n                                        is DownloadOperationResult.Failure -> result.safeMessage\n                                        is DownloadOperationResult.Success -> null\n                                    }\n                                }\n                            }\n                        },\n"""
new_primary = """                        onPrimary = {\n                            errorMessage = null\n                            val action = DownloadStatePolicy.primaryAction(item)\n                            permissionDispatcher(action) { allowedAction ->\n                                when (allowedAction) {\n                                    DownloadAction.PLAY_OFFLINE,\n                                    DownloadAction.RESUME_OFFLINE,\n                                    -> onPlayOffline(item.downloadId)\n\n                                    DownloadAction.PAUSE,\n                                    DownloadAction.RESUME,\n                                    DownloadAction.RETRY,\n                                    -> scope.launch {\n                                        errorMessage = when (val result = primaryAction(downloadRepository, item)) {\n                                            is DownloadOperationResult.Failure -> result.safeMessage\n                                            is DownloadOperationResult.Success -> null\n                                        }\n                                    }\n\n                                    DownloadAction.DOWNLOAD,\n                                    DownloadAction.REMOVE,\n                                    -> Unit\n                                }\n                            }\n                        },\n"""
text = replace_once(text, old_primary, new_primary, "DownloadManagement primary action")
path.write_text(text)

print("Stage 29 download permission patch applied")
