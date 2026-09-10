package app.ownplay.mobile.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import app.ownplay.mobile.feature.settings.ui.SettingsShell
import kotlinx.coroutines.launch

@Composable
fun OwnPlayApp(services: OwnPlayServices) {
    OwnPlayTheme {
        var selectedDestination by rememberSaveable {
            mutableStateOf(AppDestination.Live)
        }
        var liveFullscreen by rememberSaveable { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        Scaffold(
            containerColor = OwnPlayColors.Background,
            bottomBar = {
                if (!liveFullscreen) {
                    OwnPlayBottomBar(
                        selectedDestination = selectedDestination,
                        onDestinationSelected = { destination ->
                            if (
                                selectedDestination == AppDestination.Live &&
                                destination != AppDestination.Live
                            ) {
                                scope.launch {
                                    services.playbackController.stop(clearMedia = true)
                                }
                                liveFullscreen = false
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
                    .padding(if (liveFullscreen) PaddingValues(0.dp) else innerPadding),
            ) {
                when (selectedDestination) {
                    AppDestination.Live -> LiveShell(
                        liveRepository = services.liveRepository,
                        playbackController = services.playbackController,
                        onFullscreenChanged = { liveFullscreen = it },
                    )

                    AppDestination.Library -> LibraryShell()
                    AppDestination.Settings -> SettingsShell()
                }
            }
        }
    }
}
