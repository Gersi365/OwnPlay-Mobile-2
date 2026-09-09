package app.ownplay.mobile.feature.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayPanel
import app.ownplay.mobile.design.OwnPlayPrimaryButton
import app.ownplay.mobile.design.OwnPlaySecondaryButton
import app.ownplay.mobile.design.OwnPlaySectionHeader
import app.ownplay.mobile.design.OwnPlayShapeTokens
import app.ownplay.mobile.design.OwnPlaySpacing
import app.ownplay.mobile.design.OwnPlayTopBar

@Composable
fun LibraryShell(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        OwnPlayTopBar(showTagline = true)

        Column(
            modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Lg),
        ) {
            OwnPlaySectionHeader(title = "Continue Watching", actionLabel = "See All ›")
            ContinueWatchingShell()

            OwnPlaySectionHeader(title = "Movies", actionLabel = "See All ›")
            PosterShellRow(labels = listOf("Dune", "Oppenheimer", "Adventure", "Comedy"))

            OwnPlaySectionHeader(title = "Series", actionLabel = "See All ›")
            PosterShellRow(labels = listOf("Drama", "Comedy", "History", "Mystery"))

            OwnPlaySectionHeader(title = "Downloaded Media", actionLabel = "See All ›")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
            ) {
                DownloadedShellItem(
                    title = "Top Gun: Maverick",
                    metadata = "Movie  •  2h 10m",
                    modifier = Modifier.weight(1f),
                )
                DownloadedShellItem(
                    title = "The Bear",
                    metadata = "S2 E1  •  32 min",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(OwnPlaySpacing.Xl))
        }
    }
}

@Composable
private fun ContinueWatchingShell() {
    OwnPlayPanel(modifier = Modifier.fillMaxWidth()) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .background(OwnPlayColors.SurfaceElevated)
                    .padding(OwnPlaySpacing.Lg),
                contentAlignment = Alignment.BottomStart,
            ) {
                Column {
                    Text(
                        text = "SERIES",
                        style = MaterialTheme.typography.labelMedium,
                        color = OwnPlayColors.TextSecondary,
                    )
                    Text(
                        text = "The Night Agent",
                        style = MaterialTheme.typography.headlineMedium,
                        color = OwnPlayColors.TextPrimary,
                    )
                    Text(
                        text = "S1 E3  The Fool",
                        style = MaterialTheme.typography.bodyLarge,
                        color = OwnPlayColors.TextSecondary,
                    )
                }
            }
            Column(
                modifier = Modifier.padding(OwnPlaySpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(OwnPlayShapeTokens.Small)
                            .background(OwnPlayColors.Divider),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.42f)
                                .height(5.dp)
                                .background(OwnPlayColors.Accent),
                        )
                    }
                    Spacer(modifier = Modifier.weight(0.05f))
                    Text(
                        text = "42 min left",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OwnPlayColors.TextSecondary,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
                ) {
                    OwnPlayPrimaryButton(
                        text = "Resume",
                        onClick = {},
                        modifier = Modifier.weight(1f),
                    )
                    OwnPlaySecondaryButton(
                        text = "Play from Beginning",
                        onClick = {},
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PosterShellRow(labels: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
    ) {
        labels.forEachIndexed { index, label ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(0.68f)
                    .clip(OwnPlayShapeTokens.Small)
                    .background(
                        if (index % 2 == 0) OwnPlayColors.SurfaceElevated else OwnPlayColors.Surface,
                    )
                    .padding(OwnPlaySpacing.Sm),
                contentAlignment = Alignment.BottomStart,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = OwnPlayColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun DownloadedShellItem(
    title: String,
    metadata: String,
    modifier: Modifier = Modifier,
) {
    OwnPlayPanel(modifier = modifier) {
        Column(
            modifier = Modifier.padding(OwnPlaySpacing.Md),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.45f)
                    .clip(OwnPlayShapeTokens.Small)
                    .background(OwnPlayColors.SurfaceElevated),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = OwnPlayColors.TextPrimary,
            )
            Text(
                text = metadata,
                style = MaterialTheme.typography.bodyMedium,
                color = OwnPlayColors.TextSecondary,
            )
            Text(
                text = "●  Downloaded",
                style = MaterialTheme.typography.labelMedium,
                color = OwnPlayColors.Accent,
            )
        }
    }
}
