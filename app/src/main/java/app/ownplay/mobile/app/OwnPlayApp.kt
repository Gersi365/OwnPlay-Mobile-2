package app.ownplay.mobile.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.feature.library.ui.LibraryScreen
import app.ownplay.mobile.feature.live.ui.LiveScreen
import app.ownplay.mobile.feature.settings.ui.SettingsScreen

@Composable
fun OwnPlayApp() {
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
                onSelected = { selectedName = it.name },
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
