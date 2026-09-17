package app.ownplay.mobile.feature.live.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.ownplay.mobile.design.OwnPlayFeaturePlaceholder

@Composable
fun LiveScreen(
    modifier: Modifier = Modifier,
) {
    OwnPlayFeaturePlaceholder(
        title = "Live",
        message = "Add a source in Settings to start watching live channels.",
        modifier = modifier,
    )
}
