from pathlib import Path
import sys

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('.')
shell_path = root / 'app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryShell.kt'
actions_path = root / 'app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryActions.kt'
audit_path = root / 'docs/audit/STAGE15_LIBRARY_MEDIA_FIRST_SOURCE_AUDIT.md'

shell = shell_path.read_text()
player_marker = '@Composable\nprivate fun LibraryFullscreenPlayer('
if player_marker not in shell:
    raise SystemExit('Library fullscreen player marker not found')
player_before = shell[shell.index(player_marker):]


def replace_block(text: str, start: str, end: str, replacement: str) -> str:
    if text.count(start) != 1:
        raise SystemExit(f'Expected one start marker: {start!r}, found {text.count(start)}')
    if text.count(end) != 1:
        raise SystemExit(f'Expected one end marker: {end!r}, found {text.count(end)}')
    a = text.index(start)
    b = text.index(end, a)
    return text[:a] + replacement.rstrip() + '\n\n' + text[b:]


# Home chrome: quieter brand treatment and Library-local shelf headers.
if shell.count('OwnPlayTopBar(showTagline = true)') != 1:
    raise SystemExit('Expected one Library top bar with tagline')
shell = shell.replace('OwnPlayTopBar(showTagline = true)', 'OwnPlayTopBar(showTagline = false)')
shell = shell.replace('OwnPlaySectionHeader(', 'LibraryShelfHeader(')
shell = shell.replace('import app.ownplay.mobile.design.OwnPlayPanel\n', '')
shell = shell.replace('import app.ownplay.mobile.design.OwnPlaySectionHeader\n', '')

continue_card = r'''@Composable
private fun ContinueWatchingCard(
    item: ContinueWatchingItem,
    onResume: () -> Unit,
    onBeginning: () -> Unit,
) {
    val progress = if (item.durationMs > 0L) {
        (item.positionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val remainingMs = (item.durationMs - item.positionMs).coerceAtLeast(0L)

    Surface(
        modifier = Modifier
            .width(292.dp)
            .clickable(onClick = onResume),
        color = Color.Transparent,
        shape = OwnPlayShapeTokens.Medium,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.72f)
                .clip(OwnPlayShapeTokens.Medium)
                .background(OwnPlayColors.SurfaceElevated),
        ) {
            RemoteArtwork(
                locator = item.artworkUrl,
                contentDescription = "${item.title} artwork",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.08f),
                            0.44f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.92f),
                        ),
                    ),
            )

            LibraryIconAction(
                glyph = LibraryActionGlyph.RESTART,
                contentDescription = "Start ${item.title} over",
                onClick = onBeginning,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, end = 68.dp, bottom = 15.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.mediaKind.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = OwnPlayColors.Accent,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f),
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = "${formatDuration(remainingMs)} left",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.58f),
                        maxLines = 1,
                    )
                }
            }

            LibraryIconAction(
                glyph = LibraryActionGlyph.PLAY,
                contentDescription = "Resume ${item.title}",
                emphasized = true,
                onClick = onResume,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 10.dp, bottom = 10.dp),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.16f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .background(OwnPlayColors.Accent),
                )
            }
        }
    }
}'''
shell = replace_block(
    shell,
    '@Composable\nprivate fun ContinueWatchingCard(',
    '@Composable\nprivate fun MovieRow(',
    continue_card,
)

poster_card = r'''@Composable
private fun PosterCard(
    title: String,
    artworkUrl: String?,
    eyebrow: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(138.dp)
            .aspectRatio(0.68f)
            .clip(OwnPlayShapeTokens.Medium)
            .background(OwnPlayColors.SurfaceElevated)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (artworkUrl.isNullOrBlank()) {
            Text(
                text = title,
                modifier = Modifier.padding(OwnPlaySpacing.Md),
                style = MaterialTheme.typography.labelLarge,
                color = OwnPlayColors.TextSecondary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
            )
        } else {
            RemoteArtwork(
                locator = artworkUrl,
                contentDescription = "$title poster",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.58f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.90f),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelMedium,
                color = OwnPlayColors.Accent,
                maxLines = 1,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
        }
    }
}'''
shell = replace_block(
    shell,
    '@Composable\nprivate fun PosterCard(',
    '@Composable\nprivate fun DownloadedRow(',
    poster_card,
)

downloaded_row = r'''@Composable
private fun DownloadedRow(
    mediaItems: List<LibraryDownloadedMedia>,
    downloads: List<DownloadItem>,
    onAction: (LibraryDownloadedMedia, DownloadItem, DownloadAction) -> Unit,
) {
    val byId = remember(downloads) { downloads.associateBy { it.downloadId } }
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
    ) {
        items(
            items = mediaItems,
            key = { media -> media.downloadId },
        ) { media ->
            Surface(
                modifier = Modifier.width(244.dp),
                color = OwnPlayColors.Surface.copy(alpha = 0.52f),
                shape = OwnPlayShapeTokens.Medium,
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .height(38.dp)
                                .clip(OwnPlayShapeTokens.Action)
                                .background(OwnPlayColors.Accent.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "✓",
                                style = MaterialTheme.typography.titleMedium,
                                color = OwnPlayColors.Accent,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = media.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = OwnPlayColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                            )
                            Text(
                                text = "Available offline",
                                style = MaterialTheme.typography.bodySmall,
                                color = OwnPlayColors.TextMuted,
                            )
                        }
                    }
                    byId[media.downloadId]?.let { item ->
                        DownloadControls(
                            item = item,
                            onAction = { action -> onAction(media, item, action) },
                            compact = true,
                        )
                    }
                }
            }
        }
    }
}'''
shell = replace_block(
    shell,
    '@Composable\nprivate fun DownloadedRow(',
    '@Composable\nprivate fun MovieDetail(',
    downloaded_row,
)

movie_detail = r'''@Composable
private fun MovieDetail(
    movie: LibraryMovie,
    downloadItem: DownloadItem?,
    errorMessage: String?,
    preferResume: Boolean,
    onBack: () -> Unit,
    onResume: () -> Unit,
    onBeginning: () -> Unit,
    onDownloadAction: (DownloadAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        LibraryHero(
            title = movie.name,
            label = "MOVIE",
            rating = movie.rating,
            artworkUrl = movie.backdropUrl ?: movie.posterUrl,
            onBack = onBack,
        )
        Column(
            modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
        ) {
            if (errorMessage != null) {
                OwnPlayStatePanel(title = "Action unavailable", message = errorMessage)
            }
            if (movie.resumePositionMs != null) {
                PlaybackChoiceButtons(
                    preferResume = preferResume,
                    onResume = onResume,
                    onBeginning = onBeginning,
                )
            } else {
                LibraryPrimaryAction(
                    text = "Play",
                    onClick = onBeginning,
                    modifier = Modifier.fillMaxWidth(0.44f),
                    glyph = LibraryActionGlyph.PLAY,
                )
            }
            DownloadControls(item = downloadItem, onAction = onDownloadAction, compact = true)
            Spacer(modifier = Modifier.height(OwnPlaySpacing.Xl))
        }
    }
}'''
shell = replace_block(
    shell,
    '@Composable\nprivate fun MovieDetail(',
    '@Composable\nprivate fun SeriesDetail(',
    movie_detail,
)

series_detail = r'''@Composable
private fun SeriesDetail(
    series: LibrarySeries,
    detail: LibrarySeriesDetail?,
    warning: String?,
    errorMessage: String?,
    preferResume: Boolean,
    onBack: () -> Unit,
    onResumeEpisode: (LibraryEpisode) -> Unit,
    onBeginningEpisode: (LibraryEpisode) -> Unit,
    downloadForEpisode: (LibraryEpisode) -> DownloadItem?,
    onDownloadAction: (LibraryEpisode, DownloadItem?, DownloadAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        LibraryHero(
            title = series.name,
            label = "SERIES",
            rating = series.rating,
            artworkUrl = series.backdropUrl ?: series.posterUrl,
            onBack = onBack,
        )
        Column(
            modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
        ) {
            series.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OwnPlayColors.TextSecondary,
                )
            }
            if (warning != null) {
                OwnPlayStatePanel(title = "Using cached episodes", message = warning)
            }
            if (errorMessage != null) {
                OwnPlayStatePanel(title = "Action unavailable", message = errorMessage)
            }
            LibraryShelfHeader(title = "Episodes")
            when {
                detail == null && errorMessage == null -> OwnPlayStatePanel(
                    title = "Loading episodes",
                    message = "Refreshing episode metadata for ${series.name}.",
                )

                detail?.episodes.isNullOrEmpty() -> OwnPlayStatePanel(
                    title = "No episodes available",
                    message = "The source did not return playable episodes for this series.",
                )

                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    detail?.episodes.orEmpty().forEach { episode ->
                        val downloadItem = downloadForEpisode(episode)
                        EpisodeRow(
                            episode = episode,
                            downloadItem = downloadItem,
                            preferResume = preferResume,
                            onResume = { onResumeEpisode(episode) },
                            onBeginning = { onBeginningEpisode(episode) },
                            onDownloadAction = { action -> onDownloadAction(episode, downloadItem, action) },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(OwnPlaySpacing.Xl))
        }
    }
}'''
shell = replace_block(
    shell,
    '@Composable\nprivate fun SeriesDetail(',
    '@Composable\nprivate fun LibraryHero(',
    series_detail,
)

library_hero = r'''@Composable
private fun LibraryHero(
    title: String,
    label: String,
    rating: String?,
    artworkUrl: String?,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.55f)
            .background(OwnPlayColors.SurfaceElevated),
        contentAlignment = Alignment.BottomStart,
    ) {
        RemoteArtwork(
            locator = artworkUrl,
            contentDescription = "$title backdrop",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.18f),
                        0.44f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.94f),
                    ),
                ),
        )
        LibraryIconAction(
            glyph = LibraryActionGlyph.BACK,
            contentDescription = "Back to Library",
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(OwnPlaySpacing.Md),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = OwnPlayColors.Accent,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
            rating?.let {
                Text(
                    text = "★ $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.72f),
                )
            }
        }
    }
}'''
shell = replace_block(
    shell,
    '@Composable\nprivate fun LibraryHero(',
    '@Composable\nprivate fun RemoteArtwork(',
    library_hero,
)

episode_row = r'''@Composable
private fun EpisodeRow(
    episode: LibraryEpisode,
    downloadItem: DownloadItem?,
    preferResume: Boolean,
    onResume: () -> Unit,
    onBeginning: () -> Unit,
    onDownloadAction: (DownloadAction) -> Unit,
) {
    val hasProgress = episode.resumePositionMs != null
    val primaryIsResume = hasProgress && preferResume
    val primaryLabel = if (primaryIsResume) "Resume" else "Play"
    val primaryAction = if (primaryIsResume) onResume else onBeginning

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = OwnPlayColors.Surface.copy(alpha = 0.40f),
        shape = OwnPlayShapeTokens.Medium,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.width(54.dp)) {
                    Text(
                        text = "S${episode.seasonNumber}",
                        style = MaterialTheme.typography.labelMedium,
                        color = OwnPlayColors.TextMuted,
                    )
                    Text(
                        text = "E${episode.episodeNumber}",
                        style = MaterialTheme.typography.titleMedium,
                        color = OwnPlayColors.Accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = OwnPlayColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                    )
                    episode.durationMs?.let { duration ->
                        Text(
                            text = formatDuration(duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = OwnPlayColors.TextMuted,
                        )
                    }
                }
                LibraryIconAction(
                    glyph = LibraryActionGlyph.PLAY,
                    contentDescription = "$primaryLabel ${episode.title}",
                    emphasized = true,
                    onClick = primaryAction,
                )
            }

            if (hasProgress) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs),
                ) {
                    LibrarySecondaryAction(
                        text = if (primaryIsResume) "Start Over" else "Resume",
                        onClick = if (primaryIsResume) onBeginning else onResume,
                        modifier = Modifier.weight(0.55f),
                        glyph = if (primaryIsResume) LibraryActionGlyph.RESTART else LibraryActionGlyph.PLAY,
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        DownloadControls(item = downloadItem, onAction = onDownloadAction, compact = true)
                    }
                }
            } else {
                DownloadControls(item = downloadItem, onAction = onDownloadAction, compact = true)
            }
        }
    }
}'''
shell = replace_block(
    shell,
    '@Composable\nprivate fun EpisodeRow(',
    '@Composable\nprivate fun PlaybackChoiceButtons(',
    episode_row,
)

# Fullscreen player and playback implementation must remain byte-for-byte unchanged.
player_after = shell[shell.index(player_marker):]
if player_before != player_after:
    raise SystemExit('Stage 15 attempted to alter LibraryFullscreenPlayer or playback code')

shell_path.write_text(shell)

actions_source = r'''package app.ownplay.mobile.feature.library.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayShapeTokens

internal enum class LibraryActionGlyph {
    PLAY,
    RESTART,
    BACK,
}

@Composable
internal fun LibraryPrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: LibraryActionGlyph? = null,
) {
    LibraryActionButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        primary = true,
        glyph = glyph,
    )
}

@Composable
internal fun LibrarySecondaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: LibraryActionGlyph? = null,
) {
    LibraryActionButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        primary = false,
        glyph = glyph,
    )
}

@Composable
private fun LibraryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    primary: Boolean,
    glyph: LibraryActionGlyph?,
) {
    val contentColor = if (primary) Color.White else OwnPlayColors.TextSecondary
    Box(
        modifier = modifier
            .height(48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {},
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            shape = OwnPlayShapeTokens.Small,
            color = if (primary) OwnPlayColors.AccentStrong else Color.Transparent,
            border = null,
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = if (primary) 14.dp else 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                glyph?.let {
                    Text(
                        text = it.symbol,
                        modifier = Modifier.clearAndSetSemantics {},
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun LibraryIconAction(
    glyph: LibraryActionGlyph,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Box(
        modifier = modifier
            .width(48.dp)
            .height(48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .width(40.dp)
                .height(40.dp),
            shape = OwnPlayShapeTokens.Action,
            color = if (emphasized) OwnPlayColors.AccentStrong else Color.Black.copy(alpha = 0.52f),
            border = if (emphasized) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            tonalElevation = 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = glyph.symbol,
                    modifier = Modifier.clearAndSetSemantics {},
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
internal fun LibraryShelfHeader(
    title: String,
    actionLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(OwnPlayShapeHeaderSpacing),
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            color = OwnPlayColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        actionLabel?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = OwnPlayColors.TextMuted,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun LibraryFilterTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) OwnPlayColors.Accent else OwnPlayColors.TextMuted,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(if (selected) 18.dp else 1.dp)
                    .height(2.dp)
                    .background(
                        color = if (selected) OwnPlayColors.Accent else Color.Transparent,
                        shape = OwnPlayShapeTokens.Small,
                    ),
            )
        }
    }
}

private val LibraryActionGlyph.symbol: String
    get() = when (this) {
        LibraryActionGlyph.PLAY -> "▶"
        LibraryActionGlyph.RESTART -> "↺"
        LibraryActionGlyph.BACK -> "‹"
    }

private val OwnPlayShapeHeaderSpacing = 12.dp
'''
actions_path.write_text(actions_source)

audit_path.parent.mkdir(parents=True, exist_ok=True)
audit_path.write_text('''# Stage 15 — Library media-first source audit\n\n'
'## Source boundary\n'
'- Parent: `02d74a2278a06f6cdcff7cc8a62fa4f98c6cd51c` (Stage 14)\n'
'- Scope: Library presentation only.\n'
'- Explicitly unchanged: `LibraryFullscreenPlayer`, playback controller/session/surface ownership, Live, providers, Room/schema, download state machine, Settings, signing, versioning, release/deploy.\n\n'
'## Structural visual changes\n'
'- Continue Watching no longer renders a media card plus a second action panel. It is a single cinematic 16:9 artwork surface with title/meta/progress overlay, a compact resume affordance, and a compact restart affordance.\n'
'- Library uses local shelf headers rather than the generic app section-header primitive.\n'
'- Home branding is reduced by disabling the tagline.\n'
'- Poster cards are borderless artwork surfaces with restrained overlay metadata.\n'
'- Downloaded Media is a compact offline-status shelf rather than an `OwnPlayPanel` card.\n'
'- Movie and Series detail pages use full-bleed artwork heroes with an overlay Back control; actions and metadata sit below the hero.\n'
'- Episode rows are compact media rows with a trailing primary play/resume control and secondary actions below, rather than full bordered panels.\n'
'- Library primary CTA uses compact squared geometry; secondary actions are text-led/transparent.\n'
'- Category targets remain accessible but reduce to 44dp visual height with a restrained accent underline.\n\n'
'## Accessibility geometry\n'
'- Icon actions: 48dp interaction target / 40dp visible control.\n'
'- Button actions: 48dp interaction target / 40dp visible control.\n'
'- Decorative glyph semantics are cleared; icon actions expose explicit content descriptions.\n\n'
'## Acceptance boundary\n'
'- Source validation does not establish visual acceptance.\n'
'- Physical screenshot/video comparison against approved references remains mandatory.\n'
'- No APK/AAB is authorized or generated by this Stage 15 source pass.\n'
'''.replace("'\n'", ''))

print('Stage 15 Library media-first redesign applied')
