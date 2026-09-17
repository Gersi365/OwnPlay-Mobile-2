package app.ownplay.mobile.feature.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.feature.library.domain.LibraryContentKind
import app.ownplay.mobile.feature.library.domain.LibraryDownloadedMediaItem

@Composable
internal fun LibraryDownloadedMediaRow(
    item: LibraryDownloadedMediaItem,
) {
    val kindLabel = when (item.contentKind) {
        LibraryContentKind.MOVIE -> "Movie"
        LibraryContentKind.EPISODE -> "Episode"
        LibraryContentKind.SERIES -> "Series"
    }

    Surface(
        color = OwnPlayColors.Surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = item.title,
                color = OwnPlayColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "$kindLabel • Downloaded",
                color = OwnPlayColors.TextSecondary,
            )
        }
    }
}
