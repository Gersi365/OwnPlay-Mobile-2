package app.ownplay.mobile.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.ownplay.mobile.OwnPlayApplication
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.feature.library.ui.LibraryScreen
import app.ownplay.mobile.feature.live.ui.LiveScreen
import app.ownplay.mobile.feature.playback.domain.PlaybackPresentation
import app.ownplay.mobile.feature.playback.ui.PlaybackVideoSurface
import app.ownplay.mobile.feature.settings.ui.SettingsScreen

@Composable
fun OwnPlayApp() {
    val application = LocalContext.current.applicationContext as OwnPlayApplication
    val services = remember(application) { application.services }
    val playbackState by services.playbackSessionController.state.collectAsState()
    val activeSourceFlow = remember(services.sourceRepository) {
        services.sourceRepository.observeActiveSource()
    }

    LaunchedEffect(activeSourceFlow, services.playbackSessionController) {
        activeSourceFlow.collect { activeSource ->
            services.playbackSessionController.reconcileActiveSource(activeSource?.sourceId)
        }
    }

    if (
        playbackState.target != null &&
        playbackState.presentation == PlaybackPresentation.PICTURE_IN_PICTURE
    ) {
        PlaybackVideoSurface(
            playbackEngine = services.playbackEngine,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    var selectedName by rememberSaveable {
        mutableStateOf(AppDestination.LIVE.name)
    }
    val selected = AppDestination.entries.firstOrNull { it.name == selectedName }
        ?: AppDestination.LIVE

    Scaffold(
        containerColor = OwnPlayColors.Background,
        bottomBar = {
            OwnPlayBottomBar(
                selected = selected,
                onSelected = { destination ->
                    if (destination != selected) {
                        services.playbackSessionController.clear()
                        selectedName = destination.name
                    }
                },
            )
        },
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        when (selected) {
            AppDestination.LIVE -> LiveScreen(modifier)
            AppDestination.LIBRARY -> LibraryScreen(modifier)
            AppDestination.SETTINGS -> SettingsScreen(modifier)
        }
    }
}
