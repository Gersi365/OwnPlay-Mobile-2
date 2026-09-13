package app.ownplay.mobile.downloads.ui

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
