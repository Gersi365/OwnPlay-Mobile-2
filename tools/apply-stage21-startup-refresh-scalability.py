#!/usr/bin/env python3
from pathlib import Path

ROOT = Path.cwd()


def replace_once(path: str, old: str, new: str) -> None:
    file_path = ROOT / path
    text = file_path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one replacement target, found {count}")
    file_path.write_text(text.replace(old, new, 1), encoding="utf-8")


def replace_between(path: str, start_marker: str, end_marker: str, replacement: str) -> None:
    file_path = ROOT / path
    text = file_path.read_text(encoding="utf-8")
    start = text.find(start_marker)
    end = text.find(end_marker, start + len(start_marker))
    if start < 0 or end < 0:
        raise SystemExit(f"{path}: replacement markers not found")
    file_path.write_text(text[:start] + replacement + text[end:], encoding="utf-8")


source_loader = "app/src/main/java/app/ownplay/mobile/sources/data/SourceCatalogLoader.kt"
source_loader_function = '''    private suspend fun loadXtream(
        source: Source,
        credential: SourceCredential,
    ): ProviderRefreshPayload {
        val xtreamCredential = credential as? SourceCredential.Xtream
            ?: return failedPayload("CREDENTIAL_TYPE")

        return coroutineScope {
            // These six catalog sections are independent. Launch them together and let the
            // shared OkHttp dispatcher enforce the existing per-host request ceiling.
            val liveCategoriesDeferred = async {
                xtreamClient.liveCategories(source.baseLocator, xtreamCredential)
            }
            val globalLiveStreamsDeferred = async {
                xtreamClient.liveStreams(source.baseLocator, xtreamCredential)
            }
            val vodCategoriesDeferred = async {
                xtreamClient.vodCategories(source.baseLocator, xtreamCredential)
            }
            val vodStreamsDeferred = async {
                xtreamClient.vodStreams(source.baseLocator, xtreamCredential)
            }
            val seriesCategoriesDeferred = async {
                xtreamClient.seriesCategories(source.baseLocator, xtreamCredential)
            }
            val seriesDeferred = async {
                xtreamClient.series(source.baseLocator, xtreamCredential)
            }

            val liveCategoriesResult = liveCategoriesDeferred.await()
            val globalLiveStreamsResult = globalLiveStreamsDeferred.await()
            val vodCategoriesResult = vodCategoriesDeferred.await()
            val vodStreamsResult = vodStreamsDeferred.await()
            val seriesCategoriesResult = seriesCategoriesDeferred.await()
            val seriesResult = seriesDeferred.await()

            val liveStreamsResult = recoverLiveCategoryAttribution(
                source = source,
                credential = xtreamCredential,
                categoriesResult = liveCategoriesResult,
                streamsResult = globalLiveStreamsResult,
            )
            val liveCategoryMap = categoryIdMap(source.sourceId, "LIVE", liveCategoriesResult)
            val vodCategoryMap = categoryIdMap(source.sourceId, "MOVIE", vodCategoriesResult)
            val seriesCategoryMap = categoryIdMap(source.sourceId, "SERIES", seriesCategoriesResult)

            ProviderRefreshPayload(
                liveCategories = mapCategories(source.sourceId, "LIVE", liveCategoriesResult),
                liveChannels = mapXtreamResult(liveStreamsResult) { streams ->
                    streams.map { stream ->
                        ProviderLiveChannelRecord(
                            channelId = StableIdentity.xtreamContentId(source.sourceId, "live", stream.streamId),
                            providerKey = stream.streamId,
                            providerStreamId = stream.streamId,
                            categoryKey = XtreamLiveCategoryAttribution
                                .normalizeProviderCategoryId(stream.categoryId)
                                ?.let(liveCategoryMap::get),
                            name = stream.name,
                            tvgId = stream.epgChannelId,
                            tvgName = stream.name,
                            logoUrl = stream.streamIcon,
                            streamLocator = "xtream://live/${stream.streamId}",
                            providerOrder = stream.providerOrder,
                        )
                    }
                },
                vodCategories = mapCategories(source.sourceId, "MOVIE", vodCategoriesResult),
                movies = mapXtreamResult(vodStreamsResult) { movies ->
                    movies.map { movie ->
                        ProviderMovieRecord(
                            movieId = StableIdentity.xtreamContentId(source.sourceId, "movie", movie.streamId),
                            providerStreamId = movie.streamId,
                            categoryKey = XtreamLiveCategoryAttribution
                                .normalizeProviderCategoryId(movie.categoryId)
                                ?.let(vodCategoryMap::get),
                            name = movie.name,
                            posterUrl = movie.posterUrl,
                            backdropUrl = null,
                            extension = movie.extension,
                            rating = movie.rating,
                            providerOrder = movie.providerOrder,
                        )
                    }
                },
                seriesCategories = mapCategories(source.sourceId, "SERIES", seriesCategoriesResult),
                series = mapXtreamResult(seriesResult) { series ->
                    series.map { item ->
                        ProviderSeriesRecord(
                            seriesId = StableIdentity.xtreamContentId(source.sourceId, "series", item.seriesId),
                            providerSeriesId = item.seriesId,
                            categoryKey = XtreamLiveCategoryAttribution
                                .normalizeProviderCategoryId(item.categoryId)
                                ?.let(seriesCategoryMap::get),
                            name = item.name,
                            posterUrl = item.posterUrl,
                            backdropUrl = item.backdropUrl,
                            description = item.description,
                            rating = item.rating,
                            providerOrder = item.providerOrder,
                        )
                    }
                },
            )
        }
    }

'''
replace_between(
    source_loader,
    "    private suspend fun loadXtream(\n",
    "    private suspend fun loadM3u(\n",
    source_loader_function,
)

library_repository = "app/src/main/java/app/ownplay/mobile/feature/library/data/LibraryRepositoryImpl.kt"
library_observe_rows = '''    private fun observeRows(sourceId: String): Flow<LibraryRows> {
        val coreRows = combine(
            libraryDao.observeAvailableMovies(sourceId),
            libraryDao.observeAvailableSeries(sourceId),
        ) { movies, series ->
            CoreRows(movies = movies, series = series)
        }
        val categoryRows = combine(
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
            // Library home only needs episode metadata for active Continue Watching rows.
            // Do not materialize every episode in a large provider catalog on each emission.
            val progressEpisodes = mutableListOf<EpisodeLibraryView>()
            for (row in progress) {
                if (
                    row.mediaKind.equals(LibraryMediaKind.EPISODE.name, ignoreCase = true) &&
                    !row.completed &&
                    row.positionMs > 0L &&
                    row.durationMs > 0L
                ) {
                    libraryDao.getEpisode(row.contentId)
                        ?.takeIf { episode -> episode.sourceId == sourceId }
                        ?.let(progressEpisodes::add)
                }
            }
            LibraryRows(
                movies = core.movies,
                series = core.series,
                episodes = progressEpisodes,
                movieCategories = categories.movieCategories,
                seriesCategories = categories.seriesCategories,
                progress = progress,
                downloads = downloads,
            )
        }
    }

'''
replace_between(
    library_repository,
    "    private fun observeRows(sourceId: String): Flow<LibraryRows> {\n",
    "    private fun LibraryRows.toCatalog(source: Source): LibraryCatalog {\n",
    library_observe_rows,
)
replace_once(
    library_repository,
    '''    private data class CoreRows(
        val movies: List<MovieEntity>,
        val series: List<SeriesEntity>,
        val episodes: List<EpisodeLibraryView>,
    )''',
    '''    private data class CoreRows(
        val movies: List<MovieEntity>,
        val series: List<SeriesEntity>,
    )''',
)

library_shell = "app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryShell.kt"
library_text_before = (ROOT / library_shell).read_text(encoding="utf-8")
library_fullscreen_marker = "private fun LibraryFullscreenPlayer("
library_fullscreen_before = library_text_before[library_text_before.index(library_fullscreen_marker):]
replace_once(
    library_shell,
    '''    val rawMovieCategories = catalog?.movieCategories.orEmpty()
    val rawSeriesCategories = catalog?.seriesCategories.orEmpty()
    val movieCategories = LibraryBrowsePolicy.visibleCategories(rawMovieCategories)
    val seriesCategories = LibraryBrowsePolicy.visibleCategories(rawSeriesCategories)
    val activeMovieCategoryKey = LibraryBrowsePolicy.activeCategoryKey(movieCategories, selectedMovieCategoryKey)
    val activeSeriesCategoryKey = LibraryBrowsePolicy.activeCategoryKey(seriesCategories, selectedSeriesCategoryKey)
    val visibleMovies = catalog?.movies.orEmpty().let { movies ->
        if (searchActive) {
            movies.filter { it.name.contains(normalizedSearchQuery, ignoreCase = true) }
        } else {
            activeMovieCategoryKey?.let { key -> movies.filter { it.categoryKey == key } } ?: movies
        }
    }
    val visibleSeries = catalog?.series.orEmpty().let { series ->
        if (searchActive) {
            series.filter { it.name.contains(normalizedSearchQuery, ignoreCase = true) }
        } else {
            activeSeriesCategoryKey?.let { key -> series.filter { it.categoryKey == key } } ?: series
        }
    }''',
    '''    val rawMovieCategories = catalog?.movieCategories.orEmpty()
    val rawSeriesCategories = catalog?.seriesCategories.orEmpty()
    val movieCategories = remember(rawMovieCategories) {
        LibraryBrowsePolicy.visibleCategories(rawMovieCategories)
    }
    val seriesCategories = remember(rawSeriesCategories) {
        LibraryBrowsePolicy.visibleCategories(rawSeriesCategories)
    }
    val activeMovieCategoryKey = LibraryBrowsePolicy.activeCategoryKey(movieCategories, selectedMovieCategoryKey)
    val activeSeriesCategoryKey = LibraryBrowsePolicy.activeCategoryKey(seriesCategories, selectedSeriesCategoryKey)
    val movies = catalog?.movies.orEmpty()
    val series = catalog?.series.orEmpty()
    val visibleMovies = remember(movies, searchActive, normalizedSearchQuery, activeMovieCategoryKey) {
        if (searchActive) {
            movies.filter { it.name.contains(normalizedSearchQuery, ignoreCase = true) }
        } else {
            activeMovieCategoryKey?.let { key -> movies.filter { it.categoryKey == key } } ?: movies
        }
    }
    val visibleSeries = remember(series, searchActive, normalizedSearchQuery, activeSeriesCategoryKey) {
        if (searchActive) {
            series.filter { it.name.contains(normalizedSearchQuery, ignoreCase = true) }
        } else {
            activeSeriesCategoryKey?.let { key -> series.filter { it.categoryKey == key } } ?: series
        }
    }''',
)
library_text_after = (ROOT / library_shell).read_text(encoding="utf-8")
library_fullscreen_after = library_text_after[library_text_after.index(library_fullscreen_marker):]
if library_fullscreen_before != library_fullscreen_after:
    raise SystemExit("Library fullscreen suffix changed unexpectedly")

live_shell = "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt"
live_text_before = (ROOT / live_shell).read_text(encoding="utf-8")
live_fullscreen_marker = "private fun FullscreenLive("
live_fullscreen_before = live_text_before[live_text_before.index(live_fullscreen_marker):]
replace_once(
    live_shell,
    '''    val channels = catalog?.channels.orEmpty()
    val rawCategories = catalog?.categories.orEmpty()
    val categories = LiveBrowsePolicy.visibleCategories(rawCategories)
    var selectedCategoryKey by remember(catalog?.activeSourceId) { mutableStateOf<String?>(null) }
    val activeCategoryKey = LiveBrowsePolicy.activeCategoryKey(categories, selectedCategoryKey)
    val normalizedSearchQuery = searchQuery.trim()
    val searchActive = normalizedSearchQuery.isNotEmpty()
    val categoryChannels = activeCategoryKey?.let { key ->
        channels.filter { channel -> channel.categoryKey == key }
    } ?: channels
    val visibleChannels = if (searchActive) {
        channels.filter { channel -> channel.name.contains(normalizedSearchQuery, ignoreCase = true) }
    } else {
        categoryChannels
    }
    val showCategories = categories.isNotEmpty() && !searchActive
    val selectedChannel = channels.firstOrNull { it.channelId == presentationState.selectedChannelId }''',
    '''    val channels = catalog?.channels.orEmpty()
    val rawCategories = catalog?.categories.orEmpty()
    val categories = remember(rawCategories) { LiveBrowsePolicy.visibleCategories(rawCategories) }
    var selectedCategoryKey by remember(catalog?.activeSourceId) { mutableStateOf<String?>(null) }
    val activeCategoryKey = LiveBrowsePolicy.activeCategoryKey(categories, selectedCategoryKey)
    val normalizedSearchQuery = searchQuery.trim()
    val searchActive = normalizedSearchQuery.isNotEmpty()
    val visibleChannels = remember(channels, activeCategoryKey, searchActive, normalizedSearchQuery) {
        if (searchActive) {
            channels.filter { channel -> channel.name.contains(normalizedSearchQuery, ignoreCase = true) }
        } else {
            activeCategoryKey?.let { key ->
                channels.filter { channel -> channel.categoryKey == key }
            } ?: channels
        }
    }
    val showCategories = categories.isNotEmpty() && !searchActive
    val selectedChannel = remember(channels, presentationState.selectedChannelId) {
        channels.firstOrNull { it.channelId == presentationState.selectedChannelId }
    }''',
)
replace_once(
    live_shell,
    '''        val channelNumber = channelNumber(channels, selectedChannel)''',
    '''        val channelNumber = remember(channels, selectedChannel.channelId) {
            channelNumber(channels, selectedChannel)
        }''',
)
live_text_after = (ROOT / live_shell).read_text(encoding="utf-8")
live_fullscreen_after = live_text_after[live_text_after.index(live_fullscreen_marker):]
if live_fullscreen_before != live_fullscreen_after:
    raise SystemExit("Live fullscreen suffix changed unexpectedly")

audit = ROOT / "docs/audit/STAGE21_STARTUP_REFRESH_SCALABILITY_SOURCE_AUDIT.md"
audit.write_text('''# Stage 21 — startup, refresh, and large-catalog scalability source audit

## Authority and scope

- Parent/source authority: Stage 20 exact HEAD `ca365683fe2a167ca435533e14a45bb6800f35be`.
- Scope: startup/loading audit, provider refresh latency, and large-catalog browse cost.
- Source-only stage. APK/AAB generation is not authorized.

## Audit findings

1. `OwnPlayApplication` already observes refresh scheduling off the main thread, while the initial Live destination immediately needs the shared playback controller. Deferring Media3 construction would require a playback architecture/lifecycle redesign, so Stage 21 intentionally does not change that boundary.
2. Xtream refresh loaded six independent top-level catalog endpoints sequentially even though the shared OkHttp dispatcher already bounds total and per-host concurrency.
3. Library home observed every available episode for the active source even though `LibraryCatalog` only needs episode metadata for incomplete Continue Watching rows; full episode lists are already loaded on demand for a selected series.
4. Library and Live browse filtering was recomputed during unrelated Compose recompositions. This is especially expensive with provider catalogs containing thousands of movies, series, or channels and with playback state updating while Live is visible.
5. ProviderRefreshWorker remains sequential across providers by design in this stage. Parallelizing whole-provider refreshes would multiply catalog traffic and persistence pressure beyond the existing per-host network bound.

## Implemented corrections

- Launch the six independent Xtream top-level catalog requests concurrently. Existing OkHttp dispatcher limits remain the transport authority, and existing partial-refresh/category-recovery behavior remains unchanged.
- Library home no longer subscribes to the complete episode catalog. It resolves episode metadata only for valid incomplete episode-progress rows; `getEpisodesForSeries` remains the full detail path.
- Memoize Library category visibility and movie/series search/category filtering against only their relevant inputs.
- Memoize Live category visibility, channel search/category filtering, selected-channel lookup, and fullscreen channel-number lookup against only their relevant inputs.
- Guarded the `LibraryFullscreenPlayer` and `FullscreenLive` suffixes byte-for-byte while applying the browse-only UI edits.

## Explicitly unchanged

- Room entities, schema files, and database version.
- Playback controller/session/surface ownership architecture.
- Live presentation reducer semantics and Back/Preview/Fullscreen behavior.
- Player/fullscreen implementations.
- Source credentials, backup format, downloads state machine/storage policy, signing, versioning, release, and deployment.

## Acceptance contract

Stage 21 is source-PASS only after exact-final-HEAD validation completes `compileDebugKotlin`, `compileDebugUnitTestKotlin`, `testDebugUnitTest`, `lintDebug`, verifies committed Room schemas remain clean, and confirms no APK/AAB artifact was produced. Physical/device performance remains separately unverified until exercised on representative large provider catalogs.
''', encoding="utf-8")

print("Stage 21 deterministic patch applied.")
