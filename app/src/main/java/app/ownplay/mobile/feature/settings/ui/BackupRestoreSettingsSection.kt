package app.ownplay.mobile.feature.settings.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayShapes
import app.ownplay.mobile.feature.settings.backup.domain.BackupExportResult
import app.ownplay.mobile.feature.settings.backup.domain.BackupRestorePlan
import app.ownplay.mobile.feature.settings.backup.domain.BackupRestorePreview
import app.ownplay.mobile.feature.settings.backup.domain.BackupRestoreRepository
import app.ownplay.mobile.feature.settings.backup.domain.BackupRestoreResult
import app.ownplay.mobile.feature.settings.backup.domain.BackupSourceRestoreAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_BACKUP_CHARACTERS = 5_000_000

@Composable
internal fun BackupRestoreSection(
    repository: BackupRestoreRepository,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingJson by remember { mutableStateOf<String?>(null) }
    var pendingPlan by remember { mutableStateOf<BackupRestorePlan?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) scope.launch {
            when (val result = repository.exportJson()) {
                is BackupExportResult.Success -> onMessage(
                    if (writeBackupJson(context, uri, result.json)) "Backup exported." else "Backup export failed.",
                )
                BackupExportResult.Failure -> onMessage("Backup export failed.")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) scope.launch {
            val json = readBackupJson(context, uri)
            if (json == null) {
                onMessage("Backup file could not be read.")
                return@launch
            }
            when (val preview = repository.previewRestore(json)) {
                is BackupRestorePreview.Ready -> {
                    pendingJson = json
                    pendingPlan = preview.plan
                }
                is BackupRestorePreview.Conflicted -> onMessage(
                    "Restore blocked by ${preview.plan.sourceResolutions.count { it.action == BackupSourceRestoreAction.CONFLICT }} source conflict(s).",
                )
                is BackupRestorePreview.Rejected -> onMessage(
                    "Backup rejected: ${preview.issues.firstOrNull()?.code ?: "invalid data"}.",
                )
                BackupRestorePreview.StorageFailure -> onMessage("Restore preview could not read local state.")
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = OwnPlayShapes.Medium,
        color = OwnPlayColors.SurfaceRaised,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Backup & restore", color = OwnPlayColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(
                "Backup includes source definitions, settings and personalization. Credentials and provider cache are excluded.",
                color = OwnPlayColors.TextSecondary,
            )
            Text(
                "Sources restored without matching local credentials are created disabled until credentials are re-entered.",
                color = OwnPlayColors.TextMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { exportLauncher.launch("ownplay-backup.json") }) {
                    Text("Export backup")
                }
                TextButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }) {
                    Text("Import backup")
                }
            }
        }
    }

    val plan = pendingPlan
    if (plan != null && pendingJson != null) {
        RestoreConfirmationDialog(
            plan = plan,
            onDismiss = {
                pendingJson = null
                pendingPlan = null
            },
            onConfirm = {
                val json = pendingJson ?: return@RestoreConfirmationDialog
                pendingJson = null
                pendingPlan = null
                scope.launch {
                    onMessage(restoreMessage(repository.restore(json)))
                }
            },
        )
    }
}

@Composable
private fun RestoreConfirmationDialog(
    plan: BackupRestorePlan,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val merged = plan.sourceResolutions.count { it.action == BackupSourceRestoreAction.MERGE_EXISTING }
    val created = plan.sourceResolutions.count { it.action == BackupSourceRestoreAction.CREATE_DISABLED }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore backup?") },
        text = {
            Text(
                "$merged source(s) will merge with existing local sources. " +
                    "$created source(s) will be created disabled without credentials. " +
                    "Provider cache, playback state and download files are not restored.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Restore") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun restoreMessage(result: BackupRestoreResult): String = when (result) {
    is BackupRestoreResult.Success -> {
        val report = result.report
        val skipped = report.skippedChannelPersonalization +
            report.skippedLivePlacements + report.skippedCustomGroups +
            report.skippedCustomGroupMemberships
        buildString {
            append("Restore complete: ${report.mergedSources} merged, ")
            append("${report.createdDisabledSources} created disabled")
            if (skipped > 0) append(", $skipped personalization item(s) skipped")
            if (report.refreshScheduleSyncFailures > 0) {
                append(", ${report.refreshScheduleSyncFailures} refresh schedule(s) need retry")
            }
            append('.')
        }
    }
    is BackupRestoreResult.Conflicted -> "Restore blocked by source conflicts."
    is BackupRestoreResult.Rejected ->
        "Backup rejected: ${result.issues.firstOrNull()?.code ?: "invalid data"}."
    BackupRestoreResult.StorageFailure -> "Restore failed without completing durable state changes."
}

private suspend fun writeBackupJson(context: Context, uri: Uri, json: String): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { writer ->
                writer.write(json)
            } ?: error("Output stream unavailable")
        }.isSuccess
    }

private suspend fun readBackupJson(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val output = StringBuilder()
                val buffer = CharArray(8 * 1024)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    if (output.length + count > MAX_BACKUP_CHARACTERS) {
                        error("Backup file is too large")
                    }
                    output.append(buffer, 0, count)
                }
                output.toString()
            } ?: error("Input stream unavailable")
        }.getOrNull()
    }
