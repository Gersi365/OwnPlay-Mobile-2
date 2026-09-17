package app.ownplay.mobile.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.ownplay.mobile.design.OwnPlayFeaturePlaceholder

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
) {
    OwnPlayFeaturePlaceholder(
        title = "Settings",
        message = "Sources, playback, downloads, backup and application preferences will be managed here.",
        modifier = modifier,
    )
}
