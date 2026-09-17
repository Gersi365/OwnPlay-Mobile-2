package app.ownplay.mobile.app

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.ownplay.mobile.design.OwnPlayColors

@Composable
internal fun OwnPlayBottomBar(
    selected: AppDestination,
    onSelected: (AppDestination) -> Unit,
) {
    NavigationBar(
        containerColor = OwnPlayColors.Surface,
    ) {
        AppDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = selected == destination,
                onClick = { onSelected(destination) },
                icon = {
                    Text(
                        text = destination.label.take(1),
                        color = if (selected == destination) {
                            OwnPlayColors.Accent
                        } else {
                            OwnPlayColors.TextMuted
                        },
                    )
                },
                label = {
                    Text(destination.label)
                },
            )
        }
    }
}
