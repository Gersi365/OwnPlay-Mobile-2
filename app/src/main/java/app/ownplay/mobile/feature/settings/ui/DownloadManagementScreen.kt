package app.ownplay.mobile.feature.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayPanel
import app.ownplay.mobile.design.OwnPlayPrimaryButton
import app.ownplay.mobile.design.OwnPlaySecondaryButton
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
    onOpenLibrary: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val downloadsFlow = remember(downloadRepository) { downloadRepository.observeDownloads() }
    val downloads by downloadsFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)

    Column(modifier = modifier.fillMaxSize()) {
        OwnPlayTopBar(showTagline = false)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs),
        ) {
            Text("‹ Settings", modifier = Modifier.clickable(onClick = onBack), style = MaterialTheme.typography.labelLarge, color = OwnPlayColors.Accent)
            Text("Manage downloads", style = MaterialTheme.typography.headlineMedium, color = OwnPlayColors.TextPrimary)
            Text("Pause, resume, retry, remove, or open completed media.", style = MaterialTheme.typography.bodyMedium, color = OwnPlayColors.TextSecondary)
        }
        if (errorMessage != null) {
            OwnPlayStatePanel(
                title = "Download action unavailable",
                message = errorMessage!!,
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
                verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
            ) {
                items(downloads, key = { it.downloadId }) { item ->
                    DownloadManagementRow(
                        item = item,
                        onPrimary = {
                            if (item.state == DownloadState.COMPLETED) {
                                onOpenLibrary()
                            } else {
                                scope.launch {
                                    errorMessage = when (val result = primaryAction(downloadRepository, item)) {
                                        is DownloadOperationResult.Failure -> result.safeMessage
                                        is DownloadOperationResult.Success -> null
                                    }
                                }
                            }
                        },
                        onRemove = {
                            scope.launch {
                                errorMessage = when (val result = downloadRepository.remove(item.downloadId)) {
                                    is DownloadOperationResult.Failure -> result.safeMessage
                                    is DownloadOperationResult.Success -> null
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

private suspend fun primaryAction(repository: DownloadRepository, item: DownloadItem): DownloadOperationResult =
    when (item.state) {
        DownloadState.DOWNLOADING, DownloadState.QUEUED -> repository.pause(item.downloadId)
        DownloadState.PAUSED -> repository.resume(item.downloadId)
        DownloadState.FAILED -> repository.retry(item.downloadId)
        DownloadState.COMPLETED -> DownloadOperationResult.Success(item)
    }

@Composable
private fun DownloadManagementRow(item: DownloadItem, onPrimary: () -> Unit, onRemove: () -> Unit) {
    OwnPlayPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(OwnPlaySpacing.Md),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
        ) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, color = OwnPlayColors.TextPrimary)
            Text(downloadStatus(item), style = MaterialTheme.typography.bodyMedium, color = OwnPlayColors.TextSecondary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm)) {
                OwnPlayPrimaryButton(primaryLabel(item.state), onPrimary, Modifier.weight(1f))
                OwnPlaySecondaryButton("Remove", onRemove, Modifier.weight(0.7f))
            }
        }
    }
}

private fun primaryLabel(state: DownloadState): String = when (state) {
    DownloadState.DOWNLOADING, DownloadState.QUEUED -> "Pause"
    DownloadState.PAUSED -> "Resume"
    DownloadState.FAILED -> "Retry"
    DownloadState.COMPLETED -> "Open Library"
}

private fun downloadStatus(item: DownloadItem): String {
    val progress = item.progressFraction?.let { " · ${(it * 100).toInt()}%" }.orEmpty()
    return when (item.state) {
        DownloadState.QUEUED -> "Queued$progress"
        DownloadState.DOWNLOADING -> "Downloading$progress"
        DownloadState.PAUSED -> "Paused$progress"
        DownloadState.FAILED -> "Needs attention"
        DownloadState.COMPLETED -> "Completed · available from Library"
    }
}
