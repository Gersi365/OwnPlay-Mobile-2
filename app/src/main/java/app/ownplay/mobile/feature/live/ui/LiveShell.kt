package app.ownplay.mobile.feature.live.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayPanel
import app.ownplay.mobile.design.OwnPlaySectionHeader
import app.ownplay.mobile.design.OwnPlayShapeTokens
import app.ownplay.mobile.design.OwnPlaySpacing
import app.ownplay.mobile.design.OwnPlayTopBar

private data class ChannelShellItem(
    val number: String,
    val name: String,
    val subtitle: String,
    val now: String,
    val next: String,
)

private val channelShellItems = listOf(
    ChannelShellItem("001", "Sports 1", "Live Sports Coverage", "Match Day Live", "Post Match"),
    ChannelShellItem("002", "News 24", "News That Matters", "World Today", "The Daily Brief"),
    ChannelShellItem("003", "Cinema Plus", "Great Movies, Every Day", "Movie Classics", "Modern Hits"),
    ChannelShellItem("004", "Kids Zone", "Fun for Every Imagination", "Cartoon Galaxy", "Kids Club"),
    ChannelShellItem("005", "Music TV", "Good Vibes All Day", "Top 100", "Fresh Beats"),
)

@Composable
fun LiveShell(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        OwnPlayTopBar(showTagline = false)

        Column(
            modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Lg),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(OwnPlayShapeTokens.Medium)
                    .background(OwnPlayColors.SurfaceElevated),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Live Preview",
                        style = MaterialTheme.typography.titleLarge,
                        color = OwnPlayColors.TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(OwnPlaySpacing.Xs))
                    Text(
                        text = "Video surface reserved for the Live stage",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OwnPlayColors.TextSecondary,
                    )
                }
            }

            OwnPlayPanel(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(OwnPlaySpacing.Lg),
                    horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Lg),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Now Playing",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OwnPlayColors.TextSecondary,
                        )
                        Text(
                            text = "World Today",
                            style = MaterialTheme.typography.titleLarge,
                            color = OwnPlayColors.TextPrimary,
                        )
                        Text(
                            text = "10:00 – 11:00",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OwnPlayColors.TextSecondary,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(86.dp)
                            .background(OwnPlayColors.Divider),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Next",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OwnPlayColors.TextSecondary,
                        )
                        Text(
                            text = "The Daily Brief",
                            style = MaterialTheme.typography.titleMedium,
                            color = OwnPlayColors.TextPrimary,
                        )
                        Text(
                            text = "11:00 – 12:00",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OwnPlayColors.TextSecondary,
                        )
                    }
                }
            }

            OwnPlaySectionHeader(
                title = "All Channels",
                actionLabel = "Tap a channel to preview",
            )

            Column(verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm)) {
                channelShellItems.forEachIndexed { index, channel ->
                    ChannelShellRow(
                        item = channel,
                        selected = index == 1,
                    )
                }
            }

            Spacer(modifier = Modifier.height(OwnPlaySpacing.Xl))
        }
    }
}

@Composable
private fun ChannelShellRow(
    item: ChannelShellItem,
    selected: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (selected) OwnPlayColors.SurfaceSelected else OwnPlayColors.Surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) OwnPlayColors.Accent else OwnPlayColors.Divider,
        ),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.number,
                style = MaterialTheme.typography.bodyMedium,
                color = OwnPlayColors.TextSecondary,
                modifier = Modifier.width(44.dp),
            )
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(OwnPlayShapeTokens.Small)
                    .background(
                        if (selected) OwnPlayColors.AccentStrong else OwnPlayColors.SurfaceElevated,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item.name.take(1),
                    style = MaterialTheme.typography.titleMedium,
                    color = OwnPlayColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.width(OwnPlaySpacing.Md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = OwnPlayColors.TextPrimary,
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OwnPlayColors.TextSecondary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Now: ${item.now}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OwnPlayColors.TextSecondary,
                )
                Text(
                    text = "Next: ${item.next}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OwnPlayColors.TextSecondary,
                )
            }
            Spacer(modifier = Modifier.width(OwnPlaySpacing.Sm))
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = if (selected) OwnPlayColors.TextPrimary else OwnPlayColors.TextSecondary,
            )
        }
    }
}
