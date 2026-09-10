package app.ownplay.mobile.feature.settings.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayPanel
import app.ownplay.mobile.design.OwnPlayPrimaryButton
import app.ownplay.mobile.design.OwnPlaySecondaryButton
import app.ownplay.mobile.design.OwnPlaySpacing
import app.ownplay.mobile.design.OwnPlayStatePanel
import app.ownplay.mobile.design.OwnPlayWordmark
import app.ownplay.mobile.feature.settings.domain.BackupExport
import app.ownplay.mobile.feature.settings.domain.BackupRepository
import app.ownplay.mobile.feature.settings.domain.BackupResult
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_RESTORE_BYTES = 10_000_000

@Composable
internal fun BackupRestoreScreen(
    backupRepository: BackupRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingExport by remember { mutableStateOf<BackupExport?>(null) }
    var statusTitle by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val createDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        val export = pendingExport
        pendingExport = null
        if (uri == null || export == null) {
            busy = false
            return@rememberLauncherForActivityResult
        }
        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { writer ->
                    writer.write(export.content)
                } ?: error("Output stream unavailable")
            }
            withContext(Dispatchers.Main) {
                busy = false
                statusTitle = if (result.isSuccess) "Backup created" else "Backup failed"
                statusMessage = if (result.isSuccess) {
                    "The versioned backup was written to the selected location."
                } else {
                    "OwnPlay could not write the selected backup file."
                }
            }
        }
    }

    val openDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    input.readUtf8Limited(MAX_RESTORE_BYTES)
                } ?: error("Input stream unavailable")
            }.fold(
                onSuccess = { content -> backupRepository.restoreBackup(content) },
                onFailure = {
                    BackupResult.Failure(
                        code = "BACKUP_READ_FAILED",
                        safeMessage = "The selected backup file could not be read safely.",
                    )
                },
            )
            withContext(Dispatchers.Main) {
                busy = false
                when (result) {
                    is BackupResult.Success -> {
                        val summary = result.value
                        statusTitle = "Backup restored"
                        statusMessage = buildString {
                            append("Restored ${summary.sourcesAdded + summary.sourcesUpdated} source profiles, ")
                            append("${summary.favoritesRestored} favorites, and ${summary.progressRestored} progress records.")
                            if (summary.pendingItems > 0) {
                                append(" ${summary.pendingItems} channel personalization items will apply after matching sources are reconnected and refreshed.")
                            }
                        }
                    }
                    is BackupResult.Failure -> {
                        statusTitle = "Restore failed"
                        statusMessage = result.safeMessage
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = OwnPlaySpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Lg),
    ) {
        Spacer(modifier = Modifier.height(OwnPlaySpacing.Sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "‹ Back",
                modifier = Modifier.clickable(onClick = onBack),
                style = MaterialTheme.typography.labelLarge,
                color = OwnPlayColors.Accent,
            )
            Spacer(modifier = Modifier.weight(1f))
            OwnPlayWordmark(showTagline = false)
        }

        Column(verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs)) {
            Text(
                text = "Backup & Restore",
                style = MaterialTheme.typography.headlineMedium,
                color = OwnPlayColors.TextPrimary,
            )
            Text(
                text = "Transfer non-secret OwnPlay settings and personalization without exporting provider credentials.",
                style = MaterialTheme.typography.bodyLarge,
                color = OwnPlayColors.TextSecondary,
            )
        }

        OwnPlayPanel(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(OwnPlaySpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
            ) {
                Text(
                    text = "Included",
                    style = MaterialTheme.typography.titleMedium,
                    color = OwnPlayColors.TextPrimary,
                )
                Text(
                    text = "Settings, safe source profiles, favorites, playback progress, custom groups, and non-secret channel personalization.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OwnPlayColors.TextSecondary,
                )
                Text(
                    text = "Never included",
                    style = MaterialTheme.typography.titleMedium,
                    color = OwnPlayColors.TextPrimary,
                )
                Text(
                    text = "Usernames, passwords, M3U remote URLs, stream locators, downloaded media, local download paths, refresh state, or custom logo locators.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OwnPlayColors.TextSecondary,
                )
                Text(
                    text = "Restored source profiles without existing secure credentials remain disabled until you reconnect them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OwnPlayColors.TextMuted,
                )
            }
        }

        OwnPlayPrimaryButton(
            text = if (busy) "Working…" else "Create backup",
            onClick = {
                if (!busy) {
                    busy = true
                    statusTitle = null
                    statusMessage = null
                    scope.launch {
                        when (val result = backupRepository.createBackup()) {
                            is BackupResult.Success -> {
                                pendingExport = result.value
                                createDocument.launch(result.value.fileName)
                            }
                            is BackupResult.Failure -> {
                                busy = false
                                statusTitle = "Backup failed"
                                statusMessage = result.safeMessage
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        OwnPlaySecondaryButton(
            text = "Restore backup",
            onClick = {
                if (!busy) {
                    statusTitle = null
                    statusMessage = null
                    openDocument.launch(arrayOf("application/json", "text/plain"))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (statusTitle != null && statusMessage != null) {
            OwnPlayStatePanel(
                title = statusTitle.orEmpty(),
                message = statusMessage.orEmpty(),
            )
        }

        Spacer(modifier = Modifier.height(OwnPlaySpacing.Xl))
    }
}

private fun InputStream.readUtf8Limited(maxBytes: Int): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8_192)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        require(total <= maxBytes) { "Backup file is too large." }
        output.write(buffer, 0, read)
    }
    return output.toString(Charsets.UTF_8.name())
}
