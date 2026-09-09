package app.ownplay.mobile.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayTheme
import app.ownplay.mobile.feature.library.ui.LibraryShell
import app.ownplay.mobile.feature.live.ui.LiveShell
import app.ownplay.mobile.feature.settings.ui.SettingsShell

@Composable
fun OwnPlayApp() {
    OwnPlayTheme {
        var selectedDestination by rememberSaveable {
            mutableStateOf(AppDestination.Live)
        }

        Scaffold(
            containerColor = OwnPlayColors.Background,
            bottomBar = {
                OwnPlayBottomBar(
                    selectedDestination = selectedDestination,
                    onDestinationSelected = { selectedDestination = it },
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                when (selectedDestination) {
                    AppDestination.Live -> LiveShell()
                    AppDestination.Library -> LibraryShell()
                    AppDestination.Settings -> SettingsShell()
                }
            }
        }
    }
}
