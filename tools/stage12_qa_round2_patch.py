from pathlib import Path


def replace(path: str, old: str, new: str, count: int = 1) -> None:
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise SystemExit(f"Patch anchor missing in {path}: {old[:120]!r}")
    p.write_text(text.replace(old, new, count))


# DAO: surface persisted provider categories and live channel category keys.
dao = "app/src/main/java/app/ownplay/mobile/data/db/Daos.kt"
replace(
    dao,
    "    val providerStreamId: String?,\n    val name: String,",
    "    val providerStreamId: String?,\n    val categoryKey: String?,\n    val name: String,",
)
replace(
    dao,
    "    @Upsert\n    suspend fun upsertEpisodes(rows: List<EpisodeEntity>)\n\n    @Query(\n        \"\"\"\n        SELECT\n            c.channelId AS channelId,\n            c.sourceId AS sourceId,\n            c.providerStreamId AS providerStreamId,",
    "    @Upsert\n    suspend fun upsertEpisodes(rows: List<EpisodeEntity>)\n\n    @Query(\n        \"\"\"\n        SELECT * FROM provider_categories\n        WHERE sourceId = :sourceId\n          AND kind = :kind\n          AND available = 1\n        ORDER BY providerOrder ASC, name COLLATE NOCASE ASC, categoryKey ASC\n        \"\"\",\n    )\n    fun observeAvailableCategories(sourceId: String, kind: String): Flow<List<ProviderCategoryEntity>>\n\n    @Query(\n        \"\"\"\n        SELECT\n            c.channelId AS channelId,\n            c.sourceId AS sourceId,\n            c.providerStreamId AS providerStreamId,",
)
replace(
    dao,
    "            c.providerStreamId AS providerStreamId,\n            COALESCE(p.localName, c.name) AS name,",
    "            c.providerStreamId AS providerStreamId,\n            c.categoryKey AS categoryKey,\n            COALESCE(p.localName, c.name) AS name,",
)

# Live domain/repository: expose categories and produce TS/HLS playback candidates.
Path("app/src/main/java/app/ownplay/mobile/feature/live/domain/LiveModels.kt").write_text(
    '''package app.ownplay.mobile.feature.live.domain

import app.ownplay.mobile.playback.domain.PlaybackStreamFormat
import kotlinx.coroutines.flow.Flow

data class LiveCategory(
    val categoryKey: String,
    val name: String,
    val providerOrder: Int,
)

data class LiveChannel(
    val channelId: String,
    val sourceId: String,
    val categoryKey: String?,
    val name: String,
    val logoUrl: String?,
    val sortOrder: Int,
)

data class LiveCatalog(
    val activeSourceId: String? = null,
    val activeSourceName: String? = null,
    val categories: List<LiveCategory> = emptyList(),
    val channels: List<LiveChannel> = emptyList(),
)

class ResolvedLivePlayback(
    val channel: LiveChannel,
    val uri: String,
    val streamFormat: PlaybackStreamFormat,
    val fallbackUri: String? = null,
    val fallbackStreamFormat: PlaybackStreamFormat? = null,
) {
    override fun toString(): String =
        "ResolvedLivePlayback(channelId=${channel.channelId}, uri=<redacted>, fallbackUri=${if (fallbackUri == null) \"none\" else \"<redacted>\"}, streamFormat=$streamFormat)"
}

sealed interface LivePlaybackResolution {
    data class Success(val value: ResolvedLivePlayback) : LivePlaybackResolution

    data class Failure(
        val code: String,
        val safeMessage: String,
    ) : LivePlaybackResolution
}

interface LiveRepository {
    fun observeCatalog(): Flow<LiveCatalog>
    suspend fun resolvePlayback(channelId: String): LivePlaybackResolution
}
'''
)

Path("app/src/main/java/app/ownplay/mobile/feature/live/data/LiveRepositoryImpl.kt").write_text(
    '''package app.ownplay.mobile.feature.live.data

import app.ownplay.mobile.data.db.CatalogDao
import app.ownplay.mobile.data.db.SourceDao
import app.ownplay.mobile.data.security.CredentialStore
import app.ownplay.mobile.feature.live.domain.LiveCatalog
import app.ownplay.mobile.feature.live.domain.LiveCategory
import app.ownplay.mobile.feature.live.domain.LiveChannel
import app.ownplay.mobile.feature.live.domain.LivePlaybackResolution
import app.ownplay.mobile.feature.live.domain.LiveRepository
import app.ownplay.mobile.feature.live.domain.ResolvedLivePlayback
import app.ownplay.mobile.playback.domain.PlaybackStreamFormat
import app.ownplay.mobile.sources.data.xtream.XtreamUrlBuilder
import app.ownplay.mobile.sources.domain.SourceCredential
import app.ownplay.mobile.sources.domain.SourceRepository
import app.ownplay.mobile.sources.domain.SourceType
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

class LiveRepositoryImpl(
    private val sourceRepository: SourceRepository,
    private val sourceDao: SourceDao,
    private val catalogDao: CatalogDao,
    private val credentialStore: CredentialStore,
) : LiveRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCatalog(): Flow<LiveCatalog> =
        sourceRepository.observeActiveSource().flatMapLatest { source ->
            if (source == null) {
                flowOf(LiveCatalog())
            } else {
                combine(
                    catalogDao.observeAvailableCategories(source.sourceId, "LIVE"),
                    catalogDao.observeAvailableLiveChannels(source.sourceId),
                ) { categoryRows, channelRows ->
                    LiveCatalog(
                        activeSourceId = source.sourceId,
                        activeSourceName = source.displayName,
                        categories = categoryRows.map { row ->
                            LiveCategory(
                                categoryKey = row.categoryKey,
                                name = row.name,
                                providerOrder = row.providerOrder,
                            )
                        },
                        channels = channelRows.map { row ->
                            LiveChannel(
                                channelId = row.channelId,
                                sourceId = row.sourceId,
                                categoryKey = row.categoryKey,
                                name = row.name,
                                logoUrl = row.logoUrl,
                                sortOrder = row.sortOrder,
                            )
                        },
                    )
                }
            }
        }

    override suspend fun resolvePlayback(channelId: String): LivePlaybackResolution {
        if (channelId.isBlank()) return failure("INVALID_CHANNEL", "This channel cannot be opened.")

        return try {
            val channel = catalogDao.getLiveChannel(channelId)
                ?: return failure("CHANNEL_NOT_FOUND", "This channel is no longer available.")
            if (!channel.available) return failure("CHANNEL_UNAVAILABLE", "This channel is currently unavailable.")

            val source = sourceDao.get(channel.sourceId)
                ?: return failure("SOURCE_NOT_FOUND", "The channel source is no longer available.")
            if (!source.enabled) return failure("SOURCE_DISABLED", "The channel source is disabled.")

            var fallbackUri: String? = null
            var fallbackFormat: PlaybackStreamFormat? = null
            val uri = when (source.type) {
                SourceType.XTREAM.name -> {
                    val providerId = channel.providerStreamId
                        ?: return failure("STREAM_ID_MISSING", "This channel has no playable stream id.")
                    val credential = credentialStore.get(source.sourceId) as? SourceCredential.Xtream
                        ?: return failure("CREDENTIAL_MISSING", "Source credentials are unavailable.")
                    fallbackUri = XtreamUrlBuilder.streamUrl(
                        baseUrl = source.baseLocator,
                        credential = credential,
                        kind = "live",
                        providerId = providerId,
                        extension = "m3u8",
                    )
                    fallbackFormat = PlaybackStreamFormat.HLS
                    XtreamUrlBuilder.streamUrl(
                        baseUrl = source.baseLocator,
                        credential = credential,
                        kind = "live",
                        providerId = providerId,
                        extension = "ts",
                    )
                }

                SourceType.M3U.name -> channel.streamLocator
                else -> return failure("SOURCE_TYPE_UNSUPPORTED", "This source type is not supported.")
            }

            LivePlaybackResolution.Success(
                ResolvedLivePlayback(
                    channel = LiveChannel(
                        channelId = channel.channelId,
                        sourceId = channel.sourceId,
                        categoryKey = channel.categoryKey,
                        name = channel.name,
                        logoUrl = channel.logoUrl,
                        sortOrder = channel.providerOrder,
                    ),
                    uri = uri,
                    streamFormat = streamFormatFor(uri),
                    fallbackUri = fallbackUri,
                    fallbackStreamFormat = fallbackFormat,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failure("STREAM_RESOLUTION_FAILED", "The channel stream could not be prepared.")
        }
    }

    private fun streamFormatFor(uri: String): PlaybackStreamFormat {
        val normalizedPath = runCatching { URI(uri).path.orEmpty() }
            .getOrDefault(uri.substringBefore('?'))
            .lowercase(Locale.US)
        return if (normalizedPath.endsWith(".m3u8")) PlaybackStreamFormat.HLS else PlaybackStreamFormat.AUTO
    }

    private fun failure(code: String, message: String): LivePlaybackResolution.Failure =
        LivePlaybackResolution.Failure(code = code, safeMessage = message)
}
'''
)

# Source UI: automatically select and refresh a newly added source/list.
source_ui = "app/src/main/java/app/ownplay/mobile/feature/settings/ui/SourceManagementScreen.kt"
add_refresh = '''    fun launchAddAndRefresh(
        state: SourceEditorState,
        operation: suspend () -> SourceResult<Source>,
    ) {
        saving = true
        scope.launch {
            when (val added = operation()) {
                is SourceResult.Success -> {
                    val source = added.value
                    when (val selected = sourceRepository.selectSource(source.sourceId)) {
                        is SourceResult.Failure -> {
                            editor = null
                            showStatus(
                                "Source saved",
                                "The source was saved but could not be selected automatically. ${selected.error.safeMessage}",
                            )
                        }

                        is SourceResult.Success -> when (val refreshed = sourceRepository.refresh(source.sourceId)) {
                            is SourceResult.Success -> {
                                editor = null
                                val summary = refreshed.value
                                showStatus(
                                    "Source ready",
                                    "Loaded ${summary.liveChannels} live channels, ${summary.movies} movies, and ${summary.series} series.",
                                )
                            }

                            is SourceResult.Failure -> {
                                editor = null
                                showStatus(
                                    "Source saved",
                                    "The source is active, but automatic loading failed. ${refreshed.error.safeMessage}",
                                )
                            }
                        }
                    }
                }

                is SourceResult.Failure -> showStatus("Source not saved", added.error.safeMessage)
            }
            saving = false
        }
    }

'''
replace(source_ui, "    fun launchSave(\n", add_refresh + "    fun launchSave(\n")
replace(
    source_ui,
    "                        launchSave(state) {\n                            sourceRepository.addSource(\n                                NewSource.Xtream(",
    "                        launchAddAndRefresh(state) {\n                            sourceRepository.addSource(\n                                NewSource.Xtream(",
)
replace(
    source_ui,
    "                        launchSave(state) {\n                            sourceRepository.addSource(\n                                NewSource.M3u(",
    "                        launchAddAndRefresh(state) {\n                            sourceRepository.addSource(\n                                NewSource.M3u(",
)

# Library domain: include provider categories and category keys.
Path("app/src/main/java/app/ownplay/mobile/feature/library/domain/LibraryModels.kt").write_text(
    '''package app.ownplay.mobile.feature.library.domain

import app.ownplay.mobile.playback.domain.PlaybackStart
import app.ownplay.mobile.playback.domain.PlaybackStreamFormat
import kotlinx.coroutines.flow.Flow

enum class LibraryMediaKind {
    MOVIE,
    EPISODE,
}

enum class LibraryStartMode {
    RESUME,
    BEGINNING,
}

data class LibraryCategory(
    val categoryKey: String,
    val name: String,
    val providerOrder: Int,
)

data class LibraryMovie(
    val movieId: String,
    val sourceId: String,
    val categoryKey: String?,
    val name: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val rating: String?,
    val providerOrder: Int,
    val resumePositionMs: Long?,
    val durationMs: Long?,
)

data class LibrarySeries(
    val seriesId: String,
    val sourceId: String,
    val categoryKey: String?,
    val name: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val description: String?,
    val rating: String?,
    val providerOrder: Int,
)

data class LibraryEpisode(
    val episodeId: String,
    val seriesId: String,
    val sourceId: String,
    val seriesName: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val durationMs: Long?,
    val resumePositionMs: Long?,
)

data class ContinueWatchingItem(
    val sourceId: String,
    val contentId: String,
    val mediaKind: LibraryMediaKind,
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

data class LibraryDownloadedMedia(
    val downloadId: String,
    val sourceId: String,
    val mediaKind: LibraryMediaKind,
    val contentId: String,
    val title: String,
    val createdAt: Long,
)

data class LibraryCatalog(
    val activeSourceId: String? = null,
    val activeSourceName: String? = null,
    val continueWatching: List<ContinueWatchingItem> = emptyList(),
    val movieCategories: List<LibraryCategory> = emptyList(),
    val movies: List<LibraryMovie> = emptyList(),
    val seriesCategories: List<LibraryCategory> = emptyList(),
    val series: List<LibrarySeries> = emptyList(),
    val downloadedMedia: List<LibraryDownloadedMedia> = emptyList(),
)

data class LibrarySeriesDetail(
    val series: LibrarySeries,
    val episodes: List<LibraryEpisode>,
)

sealed interface LibrarySeriesDetailResult {
    data class Success(
        val detail: LibrarySeriesDetail,
        val refreshWarning: String? = null,
    ) : LibrarySeriesDetailResult

    data class Failure(
        val code: String,
        val safeMessage: String,
    ) : LibrarySeriesDetailResult
}

class ResolvedLibraryPlayback(
    val sourceId: String,
    val contentId: String,
    val mediaKind: LibraryMediaKind,
    val title: String,
    val subtitle: String?,
    val uri: String,
    val streamFormat: PlaybackStreamFormat,
    val start: PlaybackStart,
    val knownDurationMs: Long?,
    val offline: Boolean = false,
) {
    override fun toString(): String =
        "ResolvedLibraryPlayback(sourceId=$sourceId, contentId=$contentId, mediaKind=$mediaKind, uri=<redacted>, streamFormat=$streamFormat, start=$start, offline=$offline)"
}

sealed interface LibraryPlaybackResolution {
    data class Success(val value: ResolvedLibraryPlayback) : LibraryPlaybackResolution

    data class Failure(
        val code: String,
        val safeMessage: String,
    ) : LibraryPlaybackResolution
}

data class PlaybackProgressUpdate(
    val sourceId: String,
    val mediaKind: LibraryMediaKind,
    val contentId: String,
    val positionMs: Long,
    val durationMs: Long,
    val ended: Boolean,
)

interface LibraryRepository {
    fun observeCatalog(): Flow<LibraryCatalog>
    suspend fun loadSeriesDetail(seriesId: String): LibrarySeriesDetailResult
    suspend fun resolveMoviePlayback(movieId: String, startMode: LibraryStartMode): LibraryPlaybackResolution
    suspend fun resolveEpisodePlayback(episodeId: String, startMode: LibraryStartMode): LibraryPlaybackResolution
    suspend fun saveProgress(update: PlaybackProgressUpdate)
}
'''
)

# Library repository: observe category rows and map them to domain models.
library_repo = "app/src/main/java/app/ownplay/mobile/feature/library/data/LibraryRepositoryImpl.kt"
replace(library_repo, "import app.ownplay.mobile.data.db.PlaybackProgressEntity\n", "import app.ownplay.mobile.data.db.PlaybackProgressEntity\nimport app.ownplay.mobile.data.db.ProviderCategoryEntity\n")
replace(library_repo, "import app.ownplay.mobile.feature.library.domain.LibraryCatalog\n", "import app.ownplay.mobile.feature.library.domain.LibraryCatalog\nimport app.ownplay.mobile.feature.library.domain.LibraryCategory\n")
replace(
    library_repo,
    '''        return combine(
            coreRows,
            libraryDao.observeIncompleteProgress(sourceId),
            libraryDao.observeCompletedDownloads(sourceId),
        ) { core, progress, downloads ->
            LibraryRows(
                movies = core.movies,
                series = core.series,
                episodes = core.episodes,
                progress = progress,
                downloads = downloads,
            )
        }
''',
    '''        val categoryRows = combine(
            catalogDao.observeAvailableCategories(sourceId, "MOVIE"),
            catalogDao.observeAvailableCategories(sourceId, "SERIES"),
        ) { movieCategories, seriesCategories ->
            CategoryRows(movieCategories = movieCategories, seriesCategories = seriesCategories)
        }
        return combine(
            coreRows,
            categoryRows,
            libraryDao.observeIncompleteProgress(sourceId),
            libraryDao.observeCompletedDownloads(sourceId),
        ) { core, categories, progress, downloads ->
            LibraryRows(
                movies = core.movies,
                series = core.series,
                episodes = core.episodes,
                movieCategories = categories.movieCategories,
                seriesCategories = categories.seriesCategories,
                progress = progress,
                downloads = downloads,
            )
        }
''',
)
replace(
    library_repo,
    "            continueWatching = LibraryOrderingPolicy.continueWatching(continueItems),\n            movies = LibraryOrderingPolicy.movies(movieModels),\n            series = LibraryOrderingPolicy.series(series.map { it.toDomain() }),",
    "            continueWatching = LibraryOrderingPolicy.continueWatching(continueItems),\n            movieCategories = movieCategories.map { LibraryCategory(it.categoryKey, it.name, it.providerOrder) },\n            movies = LibraryOrderingPolicy.movies(movieModels),\n            seriesCategories = seriesCategories.map { LibraryCategory(it.categoryKey, it.name, it.providerOrder) },\n            series = LibraryOrderingPolicy.series(series.map { it.toDomain() }),",
)
replace(
    library_repo,
    "            sourceId = sourceId,\n            name = name,\n            posterUrl = posterUrl,",
    "            sourceId = sourceId,\n            categoryKey = categoryKey,\n            name = name,\n            posterUrl = posterUrl,",
    1,
)
replace(
    library_repo,
    "        sourceId = sourceId,\n        name = name,\n        posterUrl = posterUrl,",
    "        sourceId = sourceId,\n        categoryKey = categoryKey,\n        name = name,\n        posterUrl = posterUrl,",
    1,
)
replace(
    library_repo,
    '''    private data class LibraryRows(
        val movies: List<MovieEntity>,
        val series: List<SeriesEntity>,
        val episodes: List<EpisodeLibraryView>,
        val progress: List<PlaybackProgressEntity>,
        val downloads: List<DownloadEntity>,
    )
''',
    '''    private data class CategoryRows(
        val movieCategories: List<ProviderCategoryEntity>,
        val seriesCategories: List<ProviderCategoryEntity>,
    )

    private data class LibraryRows(
        val movies: List<MovieEntity>,
        val series: List<SeriesEntity>,
        val episodes: List<EpisodeLibraryView>,
        val movieCategories: List<ProviderCategoryEntity>,
        val seriesCategories: List<ProviderCategoryEntity>,
        val progress: List<PlaybackProgressEntity>,
        val downloads: List<DownloadEntity>,
    )
''',
)

# Live UI: category filtering and a single automatic fallback after player error.
live_ui = "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt"
replace(live_ui, "import androidx.compose.foundation.layout.Box\n", "import androidx.compose.foundation.layout.Box\nimport androidx.compose.foundation.layout.PaddingValues\n")
replace(live_ui, "import androidx.compose.foundation.lazy.LazyColumn\nimport androidx.compose.foundation.lazy.itemsIndexed\n", "import androidx.compose.foundation.lazy.LazyColumn\nimport androidx.compose.foundation.lazy.LazyRow\nimport androidx.compose.foundation.lazy.items\nimport androidx.compose.foundation.lazy.itemsIndexed\n")
replace(live_ui, "import app.ownplay.mobile.feature.live.domain.LiveCatalog\n", "import app.ownplay.mobile.feature.live.domain.LiveCatalog\nimport app.ownplay.mobile.feature.live.domain.LiveCategory\n")
replace(live_ui, "    var resolutionError by remember { mutableStateOf<String?>(null) }\n", "    var resolutionError by remember { mutableStateOf<String?>(null) }\n    var fallbackLoadRequest by remember { mutableStateOf<PlaybackLoadRequest?>(null) }\n")
replace(
    live_ui,
    '''                    is LivePlaybackResolution.Success -> {
                        playbackController.load(
                            PlaybackLoadRequest(
                                media = PlaybackMedia(
                                    id = resolved.value.channel.channelId,
                                    uri = resolved.value.uri,
                                    title = resolved.value.channel.name,
                                    kind = PlaybackKind.LIVE,
                                    streamFormat = resolved.value.streamFormat,
                                ),
                            ),
                        )
                    }
''',
    '''                    is LivePlaybackResolution.Success -> {
                        fallbackLoadRequest = resolved.value.fallbackUri?.let { fallbackUri ->
                            PlaybackLoadRequest(
                                media = PlaybackMedia(
                                    id = resolved.value.channel.channelId,
                                    uri = fallbackUri,
                                    title = resolved.value.channel.name,
                                    kind = PlaybackKind.LIVE,
                                    streamFormat = resolved.value.fallbackStreamFormat ?: resolved.value.streamFormat,
                                ),
                            )
                        }
                        playbackController.load(
                            PlaybackLoadRequest(
                                media = PlaybackMedia(
                                    id = resolved.value.channel.channelId,
                                    uri = resolved.value.uri,
                                    title = resolved.value.channel.name,
                                    kind = PlaybackKind.LIVE,
                                    streamFormat = resolved.value.streamFormat,
                                ),
                            ),
                        )
                    }
''',
)
replace(live_ui, "                    is LivePlaybackResolution.Failure -> {\n                        playbackController.stop(clearMedia = true)", "                    is LivePlaybackResolution.Failure -> {\n                        fallbackLoadRequest = null\n                        playbackController.stop(clearMedia = true)")
replace(live_ui, "            LiveEffect.StopPlayback -> scope.launch {\n                resolutionError = null\n", "            LiveEffect.StopPlayback -> scope.launch {\n                resolutionError = null\n                fallbackLoadRequest = null\n")
replace(
    live_ui,
    '''    val channels = catalog?.channels.orEmpty()
    val selectedChannel = channels.firstOrNull { it.channelId == presentationState.selectedChannelId }

    LaunchedEffect(presentationState.presentation) {
''',
    '''    val channels = catalog?.channels.orEmpty()
    val categories = catalog?.categories.orEmpty()
    var selectedCategoryKey by remember(catalog?.activeSourceId) { mutableStateOf<String?>(null) }
    val visibleChannels = selectedCategoryKey?.let { key ->
        channels.filter { channel -> channel.categoryKey == key }
    } ?: channels
    val selectedChannel = channels.firstOrNull { it.channelId == presentationState.selectedChannelId }

    LaunchedEffect(categories, selectedCategoryKey) {
        if (selectedCategoryKey != null && categories.none { it.categoryKey == selectedCategoryKey }) {
            selectedCategoryKey = null
        }
    }

    LaunchedEffect(playback.phase, fallbackLoadRequest) {
        if (playback.phase == PlaybackPhase.ERROR) {
            val fallback = fallbackLoadRequest ?: return@LaunchedEffect
            fallbackLoadRequest = null
            playbackController.load(fallback)
        }
    }

    LaunchedEffect(presentationState.presentation) {
''',
)
replace(
    live_ui,
    '''        LiveBrowseAndPreview(
            catalog = catalog,
            channels = channels,
            selectedChannel = selectedChannel,
''',
    '''        LiveBrowseAndPreview(
            catalog = catalog,
            channels = visibleChannels,
            categories = categories,
            selectedCategoryKey = selectedCategoryKey,
            onCategorySelected = { selectedCategoryKey = it },
            selectedChannel = selectedChannel,
''',
)
replace(
    live_ui,
    '''private fun LiveBrowseAndPreview(
    catalog: LiveCatalog?,
    channels: List<LiveChannel>,
    selectedChannel: LiveChannel?,
''',
    '''private fun LiveBrowseAndPreview(
    catalog: LiveCatalog?,
    channels: List<LiveChannel>,
    categories: List<LiveCategory>,
    selectedCategoryKey: String?,
    onCategorySelected: (String?) -> Unit,
    selectedChannel: LiveChannel?,
''',
)
replace(
    live_ui,
    '''        }

        when {
            catalog == null -> item {
''',
    '''        }

        if (categories.isNotEmpty()) {
            item {
                LiveCategoryStrip(
                    categories = categories,
                    selectedCategoryKey = selectedCategoryKey,
                    onSelected = onCategorySelected,
                )
            }
        }

        when {
            catalog == null -> item {
''',
    1,
)
replace(
    live_ui,
    '''@Composable
private fun PreviewSurface(
''',
    '''@Composable
private fun LiveCategoryStrip(
    categories: List<LiveCategory>,
    selectedCategoryKey: String?,
    onSelected: (String?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = OwnPlaySpacing.Lg),
        horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
    ) {
        item(key = "all") {
            ProviderCategoryChip("All", selectedCategoryKey == null) { onSelected(null) }
        }
        items(categories, key = { it.categoryKey }) { category ->
            ProviderCategoryChip(category.name, selectedCategoryKey == category.categoryKey) {
                onSelected(category.categoryKey)
            }
        }
    }
}

@Composable
private fun ProviderCategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = OwnPlayShapeTokens.Small,
        color = if (selected) OwnPlayColors.AccentSoft else OwnPlayColors.SurfaceElevated,
        border = BorderStroke(1.dp, if (selected) OwnPlayColors.Accent else Color.Transparent),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = OwnPlaySpacing.Md, vertical = OwnPlaySpacing.Sm),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) OwnPlayColors.TextPrimary else OwnPlayColors.TextSecondary,
        )
    }
}

@Composable
private fun PreviewSurface(
''',
)

# Library UI: expose provider movie and series categories as stable filter chips.
library_ui = "app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryShell.kt"
replace(library_ui, "import app.ownplay.mobile.feature.library.domain.LibraryCatalog\n", "import app.ownplay.mobile.feature.library.domain.LibraryCatalog\nimport app.ownplay.mobile.feature.library.domain.LibraryCategory\n")
replace(
    library_ui,
    '''    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
''',
    '''    modifier: Modifier = Modifier,
) {
    var selectedMovieCategoryKey by remember(catalog?.activeSourceId) { mutableStateOf<String?>(null) }
    var selectedSeriesCategoryKey by remember(catalog?.activeSourceId) { mutableStateOf<String?>(null) }
    val movieCategories = catalog?.movieCategories.orEmpty()
    val seriesCategories = catalog?.seriesCategories.orEmpty()
    val visibleMovies = catalog?.movies.orEmpty().let { movies ->
        selectedMovieCategoryKey?.let { key -> movies.filter { it.categoryKey == key } } ?: movies
    }
    val visibleSeries = catalog?.series.orEmpty().let { series ->
        selectedSeriesCategoryKey?.let { key -> series.filter { it.categoryKey == key } } ?: series
    }

    LaunchedEffect(movieCategories, selectedMovieCategoryKey) {
        if (selectedMovieCategoryKey != null && movieCategories.none { it.categoryKey == selectedMovieCategoryKey }) {
            selectedMovieCategoryKey = null
        }
    }
    LaunchedEffect(seriesCategories, selectedSeriesCategoryKey) {
        if (selectedSeriesCategoryKey != null && seriesCategories.none { it.categoryKey == selectedSeriesCategoryKey }) {
            selectedSeriesCategoryKey = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
''',
    1,
)
replace(
    library_ui,
    '''            OwnPlaySectionHeader(
                title = "Movies",
                actionLabel = catalog?.movies?.size?.takeIf { it > 0 }?.let { "$it titles" },
            )
            when {
''',
    '''            OwnPlaySectionHeader(
                title = "Movies",
                actionLabel = catalog?.movies?.size?.takeIf { it > 0 }?.let { "$it titles" },
            )
            if (movieCategories.isNotEmpty()) {
                LibraryCategoryStrip(
                    categories = movieCategories,
                    selectedCategoryKey = selectedMovieCategoryKey,
                    onSelected = { selectedMovieCategoryKey = it },
                )
            }
            when {
''',
)
replace(
    library_ui,
    '''                !catalog?.movies.isNullOrEmpty() -> MovieRow(
                    movies = catalog?.movies.orEmpty(),
                    onMovieSelected = onMovieSelected,
                )
''',
    '''                visibleMovies.isNotEmpty() -> MovieRow(
                    movies = visibleMovies,
                    onMovieSelected = onMovieSelected,
                )

                catalog != null && catalog.movies.isNotEmpty() -> OwnPlayStatePanel(
                    title = "No movies in this category",
                    message = "Choose another provider category or All.",
                )
''',
)
replace(
    library_ui,
    '''            OwnPlaySectionHeader(
                title = "Series",
                actionLabel = catalog?.series?.size?.takeIf { it > 0 }?.let { "$it titles" },
            )
            when {
''',
    '''            OwnPlaySectionHeader(
                title = "Series",
                actionLabel = catalog?.series?.size?.takeIf { it > 0 }?.let { "$it titles" },
            )
            if (seriesCategories.isNotEmpty()) {
                LibraryCategoryStrip(
                    categories = seriesCategories,
                    selectedCategoryKey = selectedSeriesCategoryKey,
                    onSelected = { selectedSeriesCategoryKey = it },
                )
            }
            when {
''',
)
replace(
    library_ui,
    '''                !catalog?.series.isNullOrEmpty() -> SeriesRow(
                    seriesItems = catalog?.series.orEmpty(),
                    onSeriesSelected = onSeriesSelected,
                )
''',
    '''                visibleSeries.isNotEmpty() -> SeriesRow(
                    seriesItems = visibleSeries,
                    onSeriesSelected = onSeriesSelected,
                )

                catalog != null && catalog.series.isNotEmpty() -> OwnPlayStatePanel(
                    title = "No series in this category",
                    message = "Choose another provider category or All.",
                )
''',
)
replace(
    library_ui,
    '''@Composable
private fun ContinueWatchingCard(
''',
    '''@Composable
private fun LibraryCategoryStrip(
    categories: List<LibraryCategory>,
    selectedCategoryKey: String?,
    onSelected: (String?) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm)) {
        item(key = "all") {
            LibraryCategoryChip("All", selectedCategoryKey == null) { onSelected(null) }
        }
        items(categories, key = { it.categoryKey }) { category ->
            LibraryCategoryChip(category.name, selectedCategoryKey == category.categoryKey) {
                onSelected(category.categoryKey)
            }
        }
    }
}

@Composable
private fun LibraryCategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = OwnPlayShapeTokens.Small,
        color = if (selected) OwnPlayColors.AccentSoft else OwnPlayColors.SurfaceElevated,
        border = BorderStroke(1.dp, if (selected) OwnPlayColors.Accent else Color.Transparent),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = OwnPlaySpacing.Md, vertical = OwnPlaySpacing.Sm),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) OwnPlayColors.TextPrimary else OwnPlayColors.TextSecondary,
        )
    }
}

@Composable
private fun ContinueWatchingCard(
''',
)

# Focused URL regression coverage.
test_path = Path("app/src/test/java/app/ownplay/mobile/sources/data/xtream/XtreamUrlBuilderTest.kt")
test_text = test_path.read_text()
anchor = '''    @Test
    fun redactionRemovesCredentialsFromStreamPath() {
'''
extra = '''    @Test
    fun buildsExtensionSpecificLiveCandidates() {
        val credential = SourceCredential.Xtream("user", "pass")
        val ts = XtreamUrlBuilder.streamUrl("http://provider.test:8080", credential, "live", "42", "ts")
        val hls = XtreamUrlBuilder.streamUrl("http://provider.test:8080", credential, "live", "42", "m3u8")

        assertTrue(ts.endsWith("/42.ts"))
        assertTrue(hls.endsWith("/42.m3u8"))
    }

'''
if anchor not in test_text:
    raise SystemExit("XtreamUrlBuilderTest anchor missing")
test_path.write_text(test_text.replace(anchor, extra + anchor, 1))

# Evidence: no secrets/provider locator values.
audit = Path("docs/audit/STAGE_12_STABILIZATION.txt")
audit.write_text(
    audit.read_text().rstrip()
    + '''

Physical QA round 2 findings and corrections
- The user confirmed that the stable-signer v2 installation required the documented one-time uninstall because the immediately prior QA APK used an unrecoverable ephemeral signer. This is not a new defect. From stable-signer v2 onward, every QA build must reuse signer certificate SHA-256 b92596cb233b42995775f9fe9d5b3bb8c3d3b72700626a8937326aa804c4a0f5 and use a monotonically increasing versionCode.
- New source/list add previously persisted connection data but required a separate manual Refresh. New Xtream and M3U source adds now select the new source and start provider refresh automatically; manual Refresh remains available.
- Provider LIVE/MOVIE/SERIES categories were already persisted in Room but were not projected through the Live/Library domain and presentation layers. Category observation and source-aware category filters are now wired without changing the Room schema.
- Live Xtream playback previously produced an extensionless /live/<redacted>/<redacted>/id locator. The resolver now emits the common transport-stream .ts candidate and a runtime-only .m3u8 fallback candidate. Live presentation performs at most one automatic HLS fallback after a primary player error, without creating a second player/session.
- No source URL, username, password, token, stream URL, signing password, or private key is recorded here.
- These corrections require exact-head source validation and later physical retest. Source PASS cannot establish provider playback compatibility or physical responsiveness.
'''
)
