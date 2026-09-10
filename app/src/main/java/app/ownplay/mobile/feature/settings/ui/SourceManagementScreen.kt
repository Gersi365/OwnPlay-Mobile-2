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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayPanel
import app.ownplay.mobile.design.OwnPlayPrimaryButton
import app.ownplay.mobile.design.OwnPlaySecondaryButton
import app.ownplay.mobile.design.OwnPlaySpacing
import app.ownplay.mobile.design.OwnPlayStatePanel
import app.ownplay.mobile.design.OwnPlayWordmark
import app.ownplay.mobile.feature.settings.domain.BackupRepository
import app.ownplay.mobile.feature.settings.domain.BackupResult
import app.ownplay.mobile.sources.domain.NewSource
import app.ownplay.mobile.sources.domain.Source
import app.ownplay.mobile.sources.domain.SourceConnectionUpdate
import app.ownplay.mobile.sources.domain.SourceCredential
import app.ownplay.mobile.sources.domain.SourceRepository
import app.ownplay.mobile.sources.domain.SourceResult
import app.ownplay.mobile.sources.domain.SourceType
import app.ownplay.mobile.sources.domain.SourceUpdate
import kotlinx.coroutines.launch

private data class SourceEditorState(
    val sourceId: String?,
    val type: SourceType,
    val displayName: String,
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val playlistUrl: String = "",
    val requiresCredentials: Boolean = false,
)

@Composable
internal fun SourceManagementScreen(
    sourceRepository: SourceRepository,
    backupRepository: BackupRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sourcesFlow = remember(sourceRepository) { sourceRepository.observeSources() }
    val activeSourceFlow = remember(sourceRepository) { sourceRepository.observeActiveSource() }
    val sources by sourcesFlow.collectAsState(initial = emptyList())
    val activeSource by activeSourceFlow.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    var editor by remember { mutableStateOf<SourceEditorState?>(null) }
    var pendingRemoval by remember { mutableStateOf<Source?>(null) }
    var busySourceId by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var statusTitle by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun showStatus(title: String, message: String) {
        statusTitle = title
        statusMessage = message
    }

    fun openEditor(source: Source) {
        editor = SourceEditorState(
            sourceId = source.sourceId,
            type = source.type,
            displayName = source.displayName,
            baseUrl = if (source.type == SourceType.XTREAM) source.baseLocator else "",
            requiresCredentials = source.requiresCredentials,
        )
        statusTitle = null
        statusMessage = null
    }

    if (editor != null) {
        SourceEditor(
            state = editor!!,
            saving = saving,
            onStateChange = { editor = it },
            onCancel = { if (!saving) editor = null },
            onSave = {
                if (saving) return@SourceEditor
                val state = editor ?: return@SourceEditor
                val name = state.displayName.trim()
                if (name.isBlank()) {
                    showStatus("Check source", "Source name is required.")
                    return@SourceEditor
                }

                val operation: suspend () -> SourceResult<*> = when {
                    state.sourceId == null && state.type == SourceType.XTREAM -> {
                        val baseUrl = state.baseUrl.trim()
                        val username = state.username.trim()
                        val password = state.password
                        if (baseUrl.isBlank() || username.isBlank() || password.isBlank()) {
                            showStatus("Check source", "Base URL, username, and password are required for Xtream.")
                            return@SourceEditor
                        }
                        {
                            sourceRepository.addSource(
                                NewSource.Xtream(
                                    displayName = name,
                                    baseUrl = baseUrl,
                                    credential = SourceCredential.Xtream(username, password),
                                ),
                            )
                        }
                    }

                    state.sourceId == null && state.type == SourceType.M3U -> {
                        val playlistUrl = state.playlistUrl.trim()
                        if (playlistUrl.isBlank()) {
                            showStatus("Check source", "A remote M3U or M3U8 URL is required.")
                            return@SourceEditor
                        }
                        {
                            sourceRepository.addSource(
                                NewSource.M3u(
                                    displayName = name,
                                    credential = SourceCredential.M3uRemoteLocator(playlistUrl),
                                ),
                            )
                        }
                    }

                    state.sourceId != null && state.type == SourceType.XTREAM -> {
                        val baseUrl = state.baseUrl.trim()
                        val username = state.username.trim()
                        val password = state.password
                        if (baseUrl.isBlank()) {
                            showStatus("Check source", "Base URL is required for Xtream.")
                            return@SourceEditor
                        }
                        val hasAnyCredentialInput = username.isNotBlank() || password.isNotBlank()
                        if (hasAnyCredentialInput && (username.isBlank() || password.isBlank())) {
                            showStatus("Check source", "Enter both username and password, or leave both blank to keep the existing secure credentials.")
                            return@SourceEditor
                        }
                        if (state.requiresCredentials && !hasAnyCredentialInput) {
                            showStatus("Reconnect source", "This restored source needs a username and password before it can be enabled.")
                            return@SourceEditor
                        }
                        val replacement = if (hasAnyCredentialInput) {
                            SourceCredential.Xtream(username, password)
                        } else {
                            null
                        }
                        {
                            sourceRepository.updateSource(
                                SourceUpdate(
                                    sourceId = requireNotNull(state.sourceId),
                                    displayName = name,
                                    enabled = if (state.requiresCredentials && replacement != null) true else null,
                                    connection = SourceConnectionUpdate.Xtream(
                                        baseUrl = baseUrl,
                                        credential = replacement,
                                    ),
                                ),
                            )
                        }
                    }

                    state.sourceId != null && state.type == SourceType.M3U -> {
                        val playlistUrl = state.playlistUrl.trim()
                        if (state.requiresCredentials && playlistUrl.isBlank()) {
                            showStatus("Reconnect source", "This restored source needs its remote M3U or M3U8 URL before it can be enabled.")
                            return@SourceEditor
                        }
                        val connection = playlistUrl.takeIf(String::isNotBlank)?.let { url ->
                            SourceConnectionUpdate.M3u(SourceCredential.M3uRemoteLocator(url))
                        }
                        {
                            sourceRepository.updateSource(
                                SourceUpdate(
                                    sourceId = requireNotNull(state.sourceId),
                                    displayName = name,
                                    enabled = if (state.requiresCredentials && connection != null) true else null,
                                    connection = connection,
                                ),
                            )
                        }
                    }

                    else -> error("Unsupported source editor state")
                }

                saving = true
                scope.launch {
                    when (val result = operation()) {
                        is SourceResult.Success -> {
                            editor = null
                            showStatus(
                                if (state.requiresCredentials) "Source reconnected" else "Source saved",
                                if (state.requiresCredentials) {
                                    "Secure connection details were stored locally. Refresh the source to restore matching personalization."
                                } else {
                                    "Source changes were saved."
                                },
                            )
                        }
                        is SourceResult.Failure -> showStatus("Source not saved", result.error.safeMessage)
                    }
                    saving = false
                }
            },
            statusTitle = statusTitle,
            statusMessage = statusMessage,
            onClearStatus = {
                statusTitle = null
                statusMessage = null
            },
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = OwnPlaySpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Lg),
    ) {
        SettingsSubpageHeader(title = "Sources", onBack = onBack)

        Column(verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs)) {
            Text(
                text = "Your providers",
                style = MaterialTheme.typography.headlineMedium,
                color = OwnPlayColors.TextPrimary,
            )
            Text(
                text = "Add legitimate Xtream-compatible accounts or remote M3U/M3U8 playlists. OwnPlay does not provide media.",
                style = MaterialTheme.typography.bodyLarge,
                color = OwnPlayColors.TextSecondary,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
        ) {
            OwnPlayPrimaryButton(
                text = "Add Xtream",
                onClick = {
                    editor = SourceEditorState(
                        sourceId = null,
                        type = SourceType.XTREAM,
                        displayName = "",
                    )
                },
                modifier = Modifier.weight(1f),
            )
            OwnPlaySecondaryButton(
                text = "Add M3U",
                onClick = {
                    editor = SourceEditorState(
                        sourceId = null,
                        type = SourceType.M3U,
                        displayName = "",
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }

        if (sources.isEmpty()) {
            OwnPlayStatePanel(
                title = "No sources yet",
                message = "Add a source to populate Live and Library. Credentials stay in Android Keystore-backed storage.",
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md)) {
                sources.forEach { source ->
                    SourceCard(
                        source = source,
                        active = activeSource?.sourceId == source.sourceId,
                        busy = busySourceId == source.sourceId,
                        onSelect = {
                            if (busySourceId == null && source.enabled && !source.requiresCredentials) {
                                busySourceId = source.sourceId
                                scope.launch {
                                    when (val result = sourceRepository.selectSource(source.sourceId)) {
                                        is SourceResult.Success -> showStatus("Active source changed", "${source.displayName} is now the active source.")
                                        is SourceResult.Failure -> showStatus("Source not selected", result.error.safeMessage)
                                    }
                                    busySourceId = null
                                }
                            }
                        },
                        onRefresh = {
                            if (busySourceId == null && source.enabled && !source.requiresCredentials) {
                                busySourceId = source.sourceId
                                scope.launch {
                                    when (val result = sourceRepository.refresh(source.sourceId)) {
                                        is SourceResult.Success -> {
                                            val deferred = when (val pending = backupRepository.applyPendingForSource(source.sourceId)) {
                                                is BackupResult.Success -> pending.value
                                                is BackupResult.Failure -> 0
                                            }
                                            val summary = result.value
                                            showStatus(
                                                "Source refreshed",
                                                buildString {
                                                    append("Updated ${summary.liveChannels} live channels, ${summary.movies} movies, and ${summary.series} series.")
                                                    if (deferred > 0) append(" Restored $deferred matching personalization items.")
                                                    if (summary.warnings.isNotEmpty()) append(" Some provider sections kept their last known data.")
                                                },
                                            )
                                        }
                                        is SourceResult.Failure -> showStatus("Refresh failed", result.error.safeMessage)
                                    }
                                    busySourceId = null
                                }
                            }
                        },
                        onEdit = { openEditor(source) },
                        onRemove = { pendingRemoval = source },
                    )
                }
            }
        }

        if (pendingRemoval != null) {
            val source = pendingRemoval!!
            OwnPlayPanel(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(OwnPlaySpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
                ) {
                    Text(
                        text = "Remove ${source.displayName}?",
                        style = MaterialTheme.typography.titleMedium,
                        color = OwnPlayColors.TextPrimary,
                    )
                    Text(
                        text = "This removes the source and its provider catalog from this device. Secure credentials for this source are also removed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OwnPlayColors.TextSecondary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
                    ) {
                        OwnPlaySecondaryButton(
                            text = "Cancel",
                            onClick = { pendingRemoval = null },
                            modifier = Modifier.weight(1f),
                        )
                        OwnPlayPrimaryButton(
                            text = "Remove",
                            onClick = {
                                if (busySourceId == null) {
                                    pendingRemoval = null
                                    busySourceId = source.sourceId
                                    scope.launch {
                                        when (val result = sourceRepository.removeSource(source.sourceId)) {
                                            is SourceResult.Success -> showStatus("Source removed", "${source.displayName} was removed from this device.")
                                            is SourceResult.Failure -> showStatus("Source not removed", result.error.safeMessage)
                                        }
                                        busySourceId = null
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        if (statusTitle != null && statusMessage != null) {
            OwnPlayStatePanel(
                title = statusTitle.orEmpty(),
                message = statusMessage.orEmpty(),
            )
        }

        Spacer(modifier = Modifier.height(OwnPlaySpacing.Xl))
    }
}

@Composable
private fun SourceCard(
    source: Source,
    active: Boolean,
    busy: Boolean,
    onSelect: () -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    OwnPlayPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(OwnPlaySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(
                            color = if (active) OwnPlayColors.AccentSoft else OwnPlayColors.SurfaceElevated,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = OwnPlaySpacing.Sm, vertical = OwnPlaySpacing.Xs),
                ) {
                    Text(
                        text = when {
                            active -> "ACTIVE"
                            source.requiresCredentials -> "RECONNECT"
                            !source.enabled -> "DISABLED"
                            else -> "READY"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active || source.requiresCredentials) OwnPlayColors.Accent else OwnPlayColors.TextSecondary,
                    )
                }
                Spacer(modifier = Modifier.width(OwnPlaySpacing.Md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = source.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = OwnPlayColors.TextPrimary,
                    )
                    Text(
                        text = when (source.type) {
                            SourceType.XTREAM -> "Xtream-compatible account"
                            SourceType.M3U -> "Remote M3U/M3U8 playlist"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = OwnPlayColors.TextSecondary,
                    )
                }
            }

            if (source.requiresCredentials) {
                Text(
                    text = "Connection secrets are intentionally absent. Edit this source to reconnect it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OwnPlayColors.TextMuted,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
            ) {
                if (!active) {
                    SourceAction(
                        text = "Select",
                        enabled = source.enabled && !source.requiresCredentials && !busy,
                        onClick = onSelect,
                    )
                }
                SourceAction(
                    text = if (busy) "Working…" else "Refresh",
                    enabled = source.enabled && !source.requiresCredentials && !busy,
                    onClick = onRefresh,
                )
                SourceAction(text = "Edit", enabled = !busy, onClick = onEdit)
                SourceAction(text = "Remove", enabled = !busy, onClick = onRemove)
            }
        }
    }
}

@Composable
private fun SourceAction(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = OwnPlaySpacing.Sm),
        style = MaterialTheme.typography.labelLarge,
        color = if (enabled) OwnPlayColors.Accent else OwnPlayColors.TextMuted,
    )
}

@Composable
private fun SourceEditor(
    state: SourceEditorState,
    saving: Boolean,
    onStateChange: (SourceEditorState) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    statusTitle: String?,
    statusMessage: String?,
    onClearStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = OwnPlaySpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Lg),
    ) {
        SettingsSubpageHeader(
            title = if (state.sourceId == null) "Add source" else "Edit source",
            onBack = onCancel,
        )

        Column(verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs)) {
            Text(
                text = when (state.type) {
                    SourceType.XTREAM -> "Xtream-compatible source"
                    SourceType.M3U -> "M3U / M3U8 source"
                },
                style = MaterialTheme.typography.headlineMedium,
                color = OwnPlayColors.TextPrimary,
            )
            Text(
                text = if (state.sourceId == null) {
                    "Connection details are stored locally. OwnPlay does not provide media or provider accounts."
                } else {
                    "Secret fields are never prefilled. Leave them blank to keep existing secure credentials when reconnect is not required."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = OwnPlayColors.TextSecondary,
            )
        }

        OwnPlayPanel(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(OwnPlaySpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
            ) {
                SourceTextField(
                    value = state.displayName,
                    onValueChange = {
                        onClearStatus()
                        onStateChange(state.copy(displayName = it))
                    },
                    label = "Source name",
                )

                when (state.type) {
                    SourceType.XTREAM -> {
                        SourceTextField(
                            value = state.baseUrl,
                            onValueChange = {
                                onClearStatus()
                                onStateChange(state.copy(baseUrl = it))
                            },
                            label = "Base URL",
                            keyboardType = KeyboardType.Uri,
                        )
                        SourceTextField(
                            value = state.username,
                            onValueChange = {
                                onClearStatus()
                                onStateChange(state.copy(username = it))
                            },
                            label = if (state.requiresCredentials) "Username" else "Username (leave blank to keep)",
                        )
                        SourceTextField(
                            value = state.password,
                            onValueChange = {
                                onClearStatus()
                                onStateChange(state.copy(password = it))
                            },
                            label = if (state.requiresCredentials) "Password" else "Password (leave blank to keep)",
                            password = true,
                        )
                    }

                    SourceType.M3U -> SourceTextField(
                        value = state.playlistUrl,
                        onValueChange = {
                            onClearStatus()
                            onStateChange(state.copy(playlistUrl = it))
                        },
                        label = if (state.requiresCredentials || state.sourceId == null) {
                            "Remote M3U / M3U8 URL"
                        } else {
                            "Remote M3U / M3U8 URL (leave blank to keep)"
                        },
                        keyboardType = KeyboardType.Uri,
                        password = true,
                    )
                }
            }
        }

        if (statusTitle != null && statusMessage != null) {
            OwnPlayStatePanel(title = statusTitle, message = statusMessage)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
        ) {
            OwnPlaySecondaryButton(
                text = "Cancel",
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            )
            OwnPlayPrimaryButton(
                text = if (saving) "Saving…" else if (state.requiresCredentials) "Reconnect" else "Save",
                onClick = onSave,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(OwnPlaySpacing.Xl))
    }
}

@Composable
private fun SourceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = OwnPlayColors.TextPrimary,
            unfocusedTextColor = OwnPlayColors.TextPrimary,
            focusedBorderColor = OwnPlayColors.Accent,
            unfocusedBorderColor = OwnPlayColors.Divider,
            focusedLabelColor = OwnPlayColors.Accent,
            unfocusedLabelColor = OwnPlayColors.TextSecondary,
            cursorColor = OwnPlayColors.Accent,
        ),
    )
}

@Composable
internal fun SettingsSubpageHeader(
    title: String,
    onBack: () -> Unit,
) {
    Spacer(modifier = Modifier.height(OwnPlaySpacing.Sm))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "‹ Back",
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(vertical = OwnPlaySpacing.Sm),
            style = MaterialTheme.typography.labelLarge,
            color = OwnPlayColors.Accent,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = OwnPlayColors.TextSecondary,
        )
        Spacer(modifier = Modifier.width(OwnPlaySpacing.Md))
        OwnPlayWordmark(showTagline = false)
    }
}
