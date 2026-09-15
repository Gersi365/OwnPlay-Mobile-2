package app.ownplay.mobile.feature.library.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayShapeTokens
import app.ownplay.mobile.design.OwnPlaySpacing
import app.ownplay.mobile.downloads.domain.DownloadAction
import app.ownplay.mobile.downloads.domain.DownloadItem
import app.ownplay.mobile.downloads.domain.DownloadState
import app.ownplay.mobile.downloads.domain.OfflineAvailability
import app.ownplay.mobile.downloads.ui.DownloadControls
import app.ownplay.mobile.feature.library.domain.LibraryDetailStartPolicy
import app.ownplay.mobile.feature.library.domain.LibraryEpisode
import app.ownplay.mobile.feature.library.domain.LibraryMediaKind
import app.ownplay.mobile.feature.library.domain.LibraryMediaMetadata
import app.ownplay.mobile.feature.library.domain.LibraryMovie
import app.ownplay.mobile.feature.library.domain.LibraryMovieDetail
import app.ownplay.mobile.feature.library.domain.LibrarySeries
import app.ownplay.mobile.feature.library.domain.LibrarySeriesDetail
import app.ownplay.mobile.feature.library.domain.LibraryStartMode

@Composable
internal fun MovieDetailStage33(
    movie: LibraryMovie,
    detail: LibraryMovieDetail?,
    warning: String?,
    downloadItem: DownloadItem?,
    errorMessage: String?,
    preferResume: Boolean,
    onBack: () -> Unit,
    onResume: () -> Unit,
    onBeginning: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onDownloadAction: (DownloadAction, LibraryMediaMetadata) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val metadata = detail?.metadata ?: movie.toBaseMetadata()
    val startActions = LibraryDetailStartPolicy.actions(
        hasProgress = movie.resumePositionMs != null,
        preferResume = preferResume,
    )
    val playLabel = startModeLabelStage33(startActions.primary)
    val playAction = startActionStage33(startActions.primary, onResume, onBeginning)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        LibraryDetailHeroStage33(
            metadata = metadata,
            label = "MOVIE",
            favorite = movie.favorite,
            playLabel = playLabel,
            onPlay = playAction,
            onFavoriteToggle = onFavoriteToggle,
            onBack = onBack,
        )
        Column(
            modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Md),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
        ) {
            warning?.let {
                LibraryShelfState(
                    title = "Using available metadata",
                    message = it,
                    tone = LibraryStateTone.WARNING,
                )
            }
            errorMessage?.let {
                LibraryShelfState(
                    title = "Action unavailable",
                    message = it,
                    tone = LibraryStateTone.ERROR,
                )
            }
            LibraryMetadataBodyStage33(metadata)
            LibraryShelfHeader(title = "Offline")
            DownloadControls(
                item = downloadItem,
                onAction = { action -> onDownloadAction(action, metadata) },
                modifier = Modifier.fillMaxWidth(),
            )
            startActions.secondary?.let { secondary ->
                LibrarySecondaryAction(
                    text = secondaryStartLabelStage33(secondary),
                    onClick = startActionStage33(secondary, onResume, onBeginning),
                    modifier = Modifier.fillMaxWidth(0.62f),
                    glyph = startModeGlyphStage33(secondary),
                )
            }
            Spacer(modifier = Modifier.height(OwnPlaySpacing.Xl))
        }
    }
}

@Composable
internal fun SeriesDetailStage33(
    series: LibrarySeries,
    detail: LibrarySeriesDetail?,
    initialEpisodeId: String?,
    warning: String?,
    errorMessage: String?,
    preferResume: Boolean,
    onBack: () -> Unit,
    onEpisodePlay: (LibraryEpisode, LibraryStartMode) -> Unit,
    onFavoriteToggle: () -> Unit,
    downloadForEpisode: (LibraryEpisode) -> DownloadItem?,
    onDownloadAction: (LibraryEpisode, DownloadItem?, DownloadAction, LibraryMediaMetadata) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val episodes = detail?.episodes.orEmpty()
    val metadata = detail?.metadata ?: series.toBaseMetadata()
    val seasons = remember(episodes) { episodes.map { it.seasonNumber }.distinct().sorted() }
    var selectedSeasonNumber by remember(series.seriesId) { mutableStateOf<Int?>(null) }
    LaunchedEffect(seasons, episodes, initialEpisodeId) {
        val targetSeason = episodes.firstOrNull { it.episodeId == initialEpisodeId }?.seasonNumber
        selectedSeasonNumber = when {
            targetSeason != null -> targetSeason
            selectedSeasonNumber in seasons -> selectedSeasonNumber
            else -> seasons.firstOrNull()
        }
    }
    val visibleEpisodes = selectedSeasonNumber?.let { selected ->
        episodes.filter { it.seasonNumber == selected }
    } ?: episodes
    val initialEpisodeBringIntoView = remember(series.seriesId, initialEpisodeId) { BringIntoViewRequester() }
    LaunchedEffect(initialEpisodeId, selectedSeasonNumber, visibleEpisodes) {
        val targetVisible = initialEpisodeId != null && visibleEpisodes.any { it.episodeId == initialEpisodeId }
        if (targetVisible) initialEpisodeBringIntoView.bringIntoView()
    }
    val primaryEpisode = episodes.firstOrNull { it.resumePositionMs != null } ?: episodes.firstOrNull()
    val primaryActions = LibraryDetailStartPolicy.actions(
        hasProgress = primaryEpisode?.resumePositionMs != null,
        preferResume = preferResume,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        LibraryDetailHeroStage33(
            metadata = metadata,
            label = "SERIES",
            favorite = series.favorite,
            playLabel = startModeLabelStage33(primaryActions.primary),
            onPlay = primaryEpisode?.let { episode -> { onEpisodePlay(episode, primaryActions.primary) } },
            onFavoriteToggle = onFavoriteToggle,
            onBack = onBack,
        )
        Column(
            modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Md),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
        ) {
            LibraryMetadataBodyStage33(metadata)
            primaryEpisode?.let { episode ->
                primaryActions.secondary?.let { secondary ->
                    LibrarySecondaryAction(
                        text = secondaryStartLabelStage33(secondary),
                        onClick = { onEpisodePlay(episode, secondary) },
                        modifier = Modifier.fillMaxWidth(0.62f),
                        glyph = startModeGlyphStage33(secondary),
                    )
                }
            }
            warning?.let {
                LibraryShelfState(
                    title = "Using cached details",
                    message = it,
                    tone = LibraryStateTone.WARNING,
                )
            }
            errorMessage?.let {
                LibraryShelfState(
                    title = "Action unavailable",
                    message = it,
                    tone = LibraryStateTone.ERROR,
                )
            }
            LibraryShelfHeader(
                title = "Episodes",
                actionLabel = visibleEpisodes.size.takeIf { it > 0 }?.let { "$it episodes" },
            )
            if (seasons.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(seasons, key = { it }) { season ->
                        LibraryFilterTab(
                            label = "Season $season",
                            selected = selectedSeasonNumber == season,
                            onClick = { selectedSeasonNumber = season },
                        )
                    }
                }
            }
            when {
                detail == null && errorMessage == null -> LibraryShelfState(
                    title = "Loading episodes",
                    message = "Refreshing provider metadata and episodes for ${series.name}.",
                    tone = LibraryStateTone.LOADING,
                )
                episodes.isEmpty() -> LibraryShelfState(
                    title = "No episodes available",
                    message = "The source did not return playable episodes for this series.",
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    visibleEpisodes.forEach { episode ->
                        val item = downloadForEpisode(episode)
                        val episodeMetadata = buildEpisodeDownloadMetadata(metadata, episode)
                        EpisodeRowStage33(
                            episode = episode,
                            downloadItem = item,
                            modifier = if (episode.episodeId == initialEpisodeId) {
                                Modifier.bringIntoViewRequester(initialEpisodeBringIntoView)
                            } else {
                                Modifier
                            },
                            preferResume = preferResume,
                            onPlay = { mode -> onEpisodePlay(episode, mode) },
                            onDownloadAction = { action ->
                                onDownloadAction(episode, item, action, episodeMetadata)
                            },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(OwnPlaySpacing.Xl))
        }
    }
}

@Composable
internal fun ManagedDownloadDetailStage33(
    item: DownloadItem,
    availability: OfflineAvailability?,
    errorMessage: String?,
    preferResume: Boolean,
    onBack: () -> Unit,
    onPlayOffline: (LibraryStartMode) -> Unit,
    onRemove: () -> Unit,
    onDownloadAction: (DownloadAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val storedMetadata = item.metadata ?: LibraryMediaMetadata(title = item.title)
    val metadata = if (item.mediaKind == LibraryMediaKind.EPISODE && item.title.isNotBlank()) {
        storedMetadata.copy(title = item.title)
    } else {
        storedMetadata
    }
    val startActions = LibraryDetailStartPolicy.actions(
        hasProgress = item.resumePositionMs != null,
        preferResume = preferResume,
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        LibraryDetailHeroStage33(
            metadata = metadata,
            label = if (item.mediaKind == LibraryMediaKind.MOVIE) "MOVIE" else "EPISODE",
            favorite = null,
            playLabel = startModeLabelStage33(startActions.primary),
            onPlay = null,
            onFavoriteToggle = null,
            onBack = onBack,
        )
        Column(
            modifier = Modifier.padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Md),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
        ) {
            LibraryMetadataBodyStage33(metadata)
            LibraryShelfHeader(title = "Offline")
            DownloadControls(
                item = item,
                onAction = { action ->
                    when (action) {
                        DownloadAction.PLAY_OFFLINE -> onPlayOffline(LibraryStartMode.BEGINNING)
                        DownloadAction.RESUME_OFFLINE -> onPlayOffline(LibraryStartMode.RESUME)
                        DownloadAction.REMOVE -> onRemove()
                        else -> onDownloadAction(action)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            LibraryShelfState(
                title = "Offline copy",
                message = "This title is no longer available in the active Library. Offline download controls remain available here.",
                tone = LibraryStateTone.WARNING,
            )
            errorMessage?.let {
                LibraryShelfState(
                    title = "Action unavailable",
                    message = it,
                    tone = LibraryStateTone.ERROR,
                )
            }
            if (item.state == DownloadState.COMPLETED && availability == OfflineAvailability.AVAILABLE) {
                startActions.secondary?.let { secondary ->
                    LibrarySecondaryAction(
                        text = secondaryStartLabelStage33(secondary),
                        onClick = { onPlayOffline(secondary) },
                        modifier = Modifier.fillMaxWidth(0.62f),
                        glyph = startModeGlyphStage33(secondary),
                    )
                }
            }
            Spacer(modifier = Modifier.height(OwnPlaySpacing.Xl))
        }
    }
}

@Composable
private fun LibraryDetailHeroStage33(
    metadata: LibraryMediaMetadata,
    label: String,
    favorite: Boolean?,
    playLabel: String,
    onPlay: (() -> Unit)?,
    onFavoriteToggle: (() -> Unit)?,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .background(OwnPlayColors.SurfaceElevated),
    ) {
        LibraryRemoteArtwork(
            locator = metadata.backdropUrl ?: metadata.posterUrl,
            contentDescription = "${metadata.title} backdrop",
            modifier = Modifier
                .fillMaxWidth()
                .height(228.dp),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.16f),
                        0.52f to Color.Black.copy(alpha = 0.34f),
                        1f to OwnPlayColors.Background,
                    ),
                ),
        )
        LibraryIconAction(
            glyph = LibraryActionGlyph.BACK,
            contentDescription = "Back to Library",
            onClick = onBack,
            modifier = Modifier.padding(start = OwnPlaySpacing.Md, top = OwnPlaySpacing.Md),
            visualSize = 36.dp,
        )
        if (favorite != null && onFavoriteToggle != null) {
            LibraryIconAction(
                glyph = if (favorite) LibraryActionGlyph.FAVORITE_ON else LibraryActionGlyph.FAVORITE_OFF,
                contentDescription = if (favorite) "Remove from favorites" else "Add to favorites",
                onClick = onFavoriteToggle,
                emphasized = favorite,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = OwnPlaySpacing.Md, top = OwnPlaySpacing.Md),
                visualSize = 36.dp,
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Md),
            horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Lg),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(
                modifier = Modifier
                    .width(132.dp)
                    .aspectRatio(0.68f)
                    .clip(OwnPlayShapeTokens.Medium)
                    .background(OwnPlayColors.SurfaceElevated),
            ) {
                LibraryRemoteArtwork(
                    locator = metadata.posterUrl ?: metadata.backdropUrl,
                    contentDescription = "${metadata.title} poster",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                onPlay?.let { action ->
                    LibraryIconAction(
                        glyph = LibraryActionGlyph.PLAY,
                        contentDescription = "$playLabel ${metadata.title}",
                        emphasized = false,
                        visualSize = 34.dp,
                        onClick = action,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f).padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = OwnPlayColors.Accent,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = metadata.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = OwnPlayColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                )
                if (onPlay != null && playLabel == "Resume") {
                    Text(
                        text = "Resume available",
                        style = MaterialTheme.typography.labelMedium,
                        color = OwnPlayColors.Accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryMetadataBodyStage33(metadata: LibraryMediaMetadata) {
    val facts = buildList {
        metadata.releaseDate?.takeIf(String::isNotBlank)?.let(::add)
        metadata.durationMs?.takeIf { it > 0L }?.let { add(formatLibraryDuration(it)) }
        metadata.rating?.takeIf(String::isNotBlank)?.let { add("★ $it") }
    }
    if (facts.isNotEmpty()) {
        Text(
            text = facts.joinToString("  •  "),
            style = MaterialTheme.typography.bodyMedium,
            color = OwnPlayColors.TextSecondary,
            fontWeight = FontWeight.Medium,
        )
    }
    metadata.genre?.takeIf(String::isNotBlank)?.let { genre ->
        Text(
            text = genre,
            style = MaterialTheme.typography.labelLarge,
            color = OwnPlayColors.Accent,
        )
    }
    metadata.plot?.takeIf(String::isNotBlank)?.let { plot ->
        Text(
            text = plot,
            style = MaterialTheme.typography.bodyMedium,
            color = OwnPlayColors.TextSecondary,
        )
    }
    metadata.director?.takeIf(String::isNotBlank)?.let { director ->
        MetadataCreditStage33(label = "Director", value = director)
    }
    metadata.cast?.takeIf(String::isNotBlank)?.let { cast ->
        MetadataCreditStage33(label = "Cast", value = cast)
    }
}

@Composable
private fun MetadataCreditStage33(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(62.dp),
            style = MaterialTheme.typography.labelMedium,
            color = OwnPlayColors.TextMuted,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = OwnPlayColors.TextSecondary,
        )
    }
}

@Composable
private fun EpisodeRowStage33(
    episode: LibraryEpisode,
    downloadItem: DownloadItem?,
    preferResume: Boolean,
    onPlay: (LibraryStartMode) -> Unit,
    onDownloadAction: (DownloadAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasProgress = episode.resumePositionMs != null
    val actions = LibraryDetailStartPolicy.actions(
        hasProgress = hasProgress,
        preferResume = preferResume,
    )
    val displayTitle = episodeDisplayTitleStage33(episode)
    val completedOffline = downloadItem?.state == DownloadState.COMPLETED

    fun startEpisode(mode: LibraryStartMode) {
        if (completedOffline) {
            onDownloadAction(
                if (mode == LibraryStartMode.RESUME) {
                    DownloadAction.RESUME_OFFLINE
                } else {
                    DownloadAction.PLAY_OFFLINE
                },
            )
        } else {
            onPlay(mode)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(42.dp)
                .background(
                    if (hasProgress) OwnPlayColors.Accent.copy(alpha = 0.72f) else OwnPlayColors.Divider,
                    OwnPlayShapeTokens.Small,
                ),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "S${episode.seasonNumber} • E${episode.episodeNumber}" +
                    (episode.durationMs?.let { "  •  ${formatLibraryDuration(it)}" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = OwnPlayColors.Accent,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleSmall,
                color = OwnPlayColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
            DownloadControls(
                item = downloadItem,
                onAction = onDownloadAction,
                compact = true,
            )
            actions.secondary?.let { secondary ->
                EpisodeInlineStartAction(
                    text = secondaryStartLabelStage33(secondary),
                    contentDescription = startModeContentDescriptionStage33(secondary, displayTitle),
                    onClick = { startEpisode(secondary) },
                )
            }
        }
        EpisodePrimaryPlayAction(
            contentDescription = startModeContentDescriptionStage33(actions.primary, displayTitle),
            onClick = { startEpisode(actions.primary) },
        )
    }
}

@Composable
private fun EpisodePrimaryPlayAction(
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "▶",
            style = MaterialTheme.typography.titleLarge,
            color = OwnPlayColors.Accent,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun EpisodeInlineStartAction(
    text: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Text(
        text = "↻ $text",
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 7.dp)
            .semantics { this.contentDescription = contentDescription },
        style = MaterialTheme.typography.labelMedium,
        color = OwnPlayColors.TextMuted,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
    )
}

private fun startActionStage33(
    mode: LibraryStartMode,
    onResume: () -> Unit,
    onBeginning: () -> Unit,
): () -> Unit = if (mode == LibraryStartMode.RESUME) onResume else onBeginning

private fun startModeLabelStage33(mode: LibraryStartMode): String =
    if (mode == LibraryStartMode.RESUME) "Resume" else "Play"

private fun secondaryStartLabelStage33(mode: LibraryStartMode): String =
    if (mode == LibraryStartMode.RESUME) "Resume" else "Play from beginning"


private fun startModeGlyphStage33(mode: LibraryStartMode): LibraryActionGlyph =
    if (mode == LibraryStartMode.RESUME) LibraryActionGlyph.PLAY else LibraryActionGlyph.RESTART

private fun startModeContentDescriptionStage33(mode: LibraryStartMode, title: String): String =
    if (mode == LibraryStartMode.RESUME) "Resume $title" else "Play $title from beginning"

private fun episodeDisplayTitleStage33(episode: LibraryEpisode): String {
    val original = episode.title.trim()
    if (original.isBlank()) return "Episode ${episode.episodeNumber}"
    val seasonToken = "S${episode.seasonNumber.toString().padStart(2, '0')}" +
        "E${episode.episodeNumber.toString().padStart(2, '0')}"
    var candidate = original
    if (candidate.startsWith(episode.seriesName, ignoreCase = true)) {
        candidate = candidate.drop(episode.seriesName.length).trimStart(' ', '-', '–', '—', '•', ':')
    }
    val tokenIndex = candidate.indexOf(seasonToken, ignoreCase = true)
    if (tokenIndex in 0..8) {
        candidate = candidate.substring(tokenIndex + seasonToken.length).trimStart(' ', '-', '–', '—', '•', ':')
    }
    return candidate.ifBlank { original }
}

internal fun LibraryMovie.toBaseMetadata(): LibraryMediaMetadata = LibraryMediaMetadata(
    title = name,
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
    durationMs = durationMs,
    rating = rating,
)

internal fun LibrarySeries.toBaseMetadata(): LibraryMediaMetadata = LibraryMediaMetadata(
    title = name,
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
    plot = description,
    rating = rating,
)

internal fun buildEpisodeDownloadMetadata(
    seriesMetadata: LibraryMediaMetadata,
    episode: LibraryEpisode,
): LibraryMediaMetadata = LibraryMediaMetadata(
    title = episodeDisplayTitleStage33(episode),
    posterUrl = seriesMetadata.posterUrl,
    backdropUrl = seriesMetadata.backdropUrl,
    plot = seriesMetadata.plot,
    releaseDate = seriesMetadata.releaseDate,
    durationMs = episode.durationMs,
    rating = seriesMetadata.rating,
    genre = seriesMetadata.genre,
    director = seriesMetadata.director,
    cast = seriesMetadata.cast,
)