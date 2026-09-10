package app.ownplay.mobile.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ownplay.mobile.core.OwnPlayServices
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayTheme
import app.ownplay.mobile.feature.library.ui.LibraryShell
import app.ownplay.mobile.feature.live.ui.LiveShell
import app.ownplay.mobile.feature.settings.domain.SettingsSnapshot
import app.ownplay.mobile.feature.settings.ui.SettingsShell
import kotlinx.coroutines.launch

@Composable
fun OwnPlayApp(
    services: OwnPlayServices,
    onFullscreenChanged: (Boolean) -> Unit = {},
    onExitConfirmed: () -> Unit = {},
) {
    OwnPlayTheme {
        var selectedDestination by rememberSaveable {
            mutableStateOf(AppDestination.Live)
        }
        var contentFullscreen by rememberSaveable { mutableStateOf(false) }
        var exitConfirmationVisible by rememberSaveable { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val settingsFlow = remember(services.settingsPreferences) { services.settingsPreferences.settings }
        val settings by settingsFlow.collectAsState(initial = SettingsSnapshot())

        fun setContentFullscreen(fullscreen: Boolean) {
            contentFullscreen = fullscreen
            onFullscreenChanged(fullscreen)
        }

        BackHandler {
            exitConfirmationVisible = true
        }

        if (exitConfirmationVisible) {
            AlertDialog(
                onDismissRequest = { exitConfirmationVisible = false },
                title = { Text("Exit OwnPlay?") },
                text = { Text("Do you want to close OwnPlay?") },
                confirmButton = {
                    TextButton(onClick = onExitConfirmed) { Text("Exit") }
                },
                dismissButton = {
                    TextButton(onClick = { exitConfirmationVisible = false }) { Text("Cancel") }
                },
            )
        }

        Scaffold(
            containerColor = OwnPlayColors.Background,
            bottomBar = {
                if (!contentFullscreen) {
                    OwnPlayBottomBar(
                        selectedDestination = selectedDestination,
                        onDestinationSelected = { destination ->
                            if (
                                selectedDestination != destination &&
                                selectedDestination != AppDestination.Settings
                            ) {
                                scope.launch {
                                    services.playbackController.stop(clearMedia = true)
                                }
                                setContentFullscreen(false)
                            }
                            selectedDestination = destination
                        },
                    )
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (contentFullscreen) PaddingValues(0.dp) else innerPadding),
            ) {
                when (selectedDestination) {
                    AppDestination.Live -> LiveShell(
                        liveRepository = services.liveRepository,
                        playbackController = services.playbackController,
                        showChannelLogos = settings.showChannelLogos,
                        onFullscreenChanged = ::setContentFullscreen,
                    )

                    AppDestination.Library -> LibraryShell(
                        libraryRepository = services.libraryRepository,
                        downloadRepository = services.downloadRepository,
                        playbackController = services.playbackController,
                        resumePlaybackEnabled = settings.resumePlaybackEnabled,
                        onFullscreenChanged = ::setContentFullscreen,
                    )

                    AppDestination.Settings -> SettingsShell(
                        sourceRepository = services.sourceRepository,
                        settingsPreferences = services.settingsPreferences,
                        backupRepository = services.backupRepository,
                        liveRepository = services.liveRepository,
                        downloadRepository = services.downloadRepository,
                        onOpenLibrary = { selectedDestination = AppDestination.Library },
                    )
                }
            }
        }
    }
}
