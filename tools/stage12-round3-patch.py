#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()


def path(rel: str) -> Path:
    return ROOT / rel


def read(rel: str) -> str:
    return path(rel).read_text()


def write(rel: str, content: str) -> None:
    target = path(rel)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content)


def replace_once(rel: str, old: str, new: str) -> None:
    text = read(rel)
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{rel}: expected one replacement target, found {count}")
    write(rel, text.replace(old, new, 1))


def append_once(rel: str, marker: str, block: str) -> None:
    text = read(rel)
    if marker in text:
        return
    write(rel, text.rstrip() + "\n\n" + block.strip() + "\n")


# Shared presentation-only provider utility-category filtering. Provider metadata remains raw in Room.
write(
    "app/src/main/java/app/ownplay/mobile/sources/domain/ProviderCategoryVisibility.kt",
    '''package app.ownplay.mobile.sources.domain

import java.util.Locale

object ProviderCategoryVisibility {
    private val exactUtilityLabels = setOf(
        "all",
        "all channels",
        "all live",
        "all live channels",
        "all movies",
        "all series",
        "all tv",
        "all vod",
    )

    fun isUtilityLabel(label: String): Boolean {
        val normalized = normalize(label)
        if (normalized in exactUtilityLabels) return true
        return normalized.contains("account information") ||
            normalized.contains("account info")
    }

    fun normalized(label: String): String = normalize(label)

    private fun normalize(label: String): String = label
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}
''',
)

write(
    "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveBrowsePolicy.kt",
    '''package app.ownplay.mobile.feature.live.ui

import app.ownplay.mobile.feature.live.domain.LiveCategory
import app.ownplay.mobile.sources.domain.ProviderCategoryVisibility

internal object LiveBrowsePolicy {
    fun visibleCategories(categories: List<LiveCategory>): List<LiveCategory> =
        categories.filterNot { category -> ProviderCategoryVisibility.isUtilityLabel(category.name) }

    fun activeCategoryKey(
        categories: List<LiveCategory>,
        requestedCategoryKey: String?,
    ): String? = requestedCategoryKey
        ?.takeIf { key -> categories.any { category -> category.categoryKey == key } }
        ?: categories.firstOrNull()?.categoryKey
}
''',
)

write(
    "app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryBrowsePolicy.kt",
    '''package app.ownplay.mobile.feature.library.ui

import app.ownplay.mobile.feature.library.domain.LibraryCategory
import app.ownplay.mobile.sources.domain.ProviderCategoryVisibility

internal object LibraryBrowsePolicy {
    fun visibleCategories(categories: List<LibraryCategory>): List<LibraryCategory> =
        categories.filterNot { category -> ProviderCategoryVisibility.isUtilityLabel(category.name) }

    fun activeCategoryKey(
        categories: List<LibraryCategory>,
        requestedCategoryKey: String?,
    ): String? = requestedCategoryKey
        ?.takeIf { key -> categories.any { category -> category.categoryKey == key } }
        ?: categories.firstOrNull()?.categoryKey
}
''',
)

write(
    "app/src/main/java/app/ownplay/mobile/feature/live/domain/LiveGuidePolicy.kt",
    '''package app.ownplay.mobile.feature.live.domain

object LiveGuidePolicy {
    fun nowNext(
        programs: List<LiveProgram>,
        nowEpochSeconds: Long,
    ): LiveNowNext {
        val usable = programs.filter { it.title.isNotBlank() }
        if (usable.isEmpty()) return LiveNowNext()

        val current = usable.firstOrNull { program ->
            val start = program.startEpochSeconds
            val end = program.endEpochSeconds
            start != null && end != null && start <= nowEpochSeconds && nowEpochSeconds < end
        }

        val next = when {
            current != null -> {
                val currentIndex = usable.indexOf(current)
                usable.drop(currentIndex + 1).firstOrNull { candidate ->
                    candidate.startEpochSeconds == null || candidate.startEpochSeconds >= (current.endEpochSeconds ?: nowEpochSeconds)
                }
            }
            else -> usable.firstOrNull { candidate ->
                candidate.startEpochSeconds?.let { it > nowEpochSeconds } == true
            }
        }

        if (current != null || next != null) {
            return LiveNowNext(now = current, next = next)
        }

        // Some Xtream providers omit timestamps in get_short_epg while retaining list order.
        return LiveNowNext(
            now = usable.getOrNull(0),
            next = usable.getOrNull(1),
        )
    }
}
''',
)

write(
    "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveOrientationPolicy.kt",
    '''package app.ownplay.mobile.feature.live.ui

internal object LiveOrientationPolicy {
    fun isLandscape(orientationDegrees: Int): Boolean =
        orientationDegrees in 60..120 || orientationDegrees in 240..300

    fun isPortrait(orientationDegrees: Int): Boolean =
        orientationDegrees in 0..30 || orientationDegrees in 150..210 || orientationDegrees in 330..359
}
''',
)

write(
    "app/src/main/java/app/ownplay/mobile/feature/live/ui/LivePlaybackFallbackPolicy.kt",
    '''package app.ownplay.mobile.feature.live.ui

import app.ownplay.mobile.playback.domain.PlaybackPhase
import app.ownplay.mobile.playback.domain.PlaybackSnapshot

internal object LivePlaybackFallbackPolicy {
    fun shouldUseFallback(playback: PlaybackSnapshot): Boolean =
        playback.phase == PlaybackPhase.ERROR ||
            (
                playback.phase == PlaybackPhase.READY &&
                    playback.audioTrackPresent == true &&
                    (playback.audioTrackSupported == false || playback.audioTrackSelected == false)
                )
}
''',
)

# Library category strips: no synthetic All chip; first real category is selected deterministically.
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryShell.kt",
    '''    val movieCategories = catalog?.movieCategories.orEmpty()
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
''',
    '''    val rawMovieCategories = catalog?.movieCategories.orEmpty()
    val rawSeriesCategories = catalog?.seriesCategories.orEmpty()
    val movieCategories = LibraryBrowsePolicy.visibleCategories(rawMovieCategories)
    val seriesCategories = LibraryBrowsePolicy.visibleCategories(rawSeriesCategories)
    val activeMovieCategoryKey = LibraryBrowsePolicy.activeCategoryKey(movieCategories, selectedMovieCategoryKey)
    val activeSeriesCategoryKey = LibraryBrowsePolicy.activeCategoryKey(seriesCategories, selectedSeriesCategoryKey)
    val visibleMovies = catalog?.movies.orEmpty().let { movies ->
        activeMovieCategoryKey?.let { key -> movies.filter { it.categoryKey == key } } ?: movies
    }
    val visibleSeries = catalog?.series.orEmpty().let { series ->
        activeSeriesCategoryKey?.let { key -> series.filter { it.categoryKey == key } } ?: series
    }

    LaunchedEffect(movieCategories, selectedMovieCategoryKey) {
        val resolvedCategoryKey = LibraryBrowsePolicy.activeCategoryKey(movieCategories, selectedMovieCategoryKey)
        if (selectedMovieCategoryKey != resolvedCategoryKey) {
            selectedMovieCategoryKey = resolvedCategoryKey
        }
    }
    LaunchedEffect(seriesCategories, selectedSeriesCategoryKey) {
        val resolvedCategoryKey = LibraryBrowsePolicy.activeCategoryKey(seriesCategories, selectedSeriesCategoryKey)
        if (selectedSeriesCategoryKey != resolvedCategoryKey) {
            selectedSeriesCategoryKey = resolvedCategoryKey
        }
    }
''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryShell.kt",
    "                    selectedCategoryKey = selectedMovieCategoryKey,",
    "                    selectedCategoryKey = activeMovieCategoryKey,",
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryShell.kt",
    "                    selectedCategoryKey = selectedSeriesCategoryKey,",
    "                    selectedCategoryKey = activeSeriesCategoryKey,",
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryShell.kt",
    '                    message = "Choose another provider category or All.",',
    '                    message = "Choose another provider category.",',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryShell.kt",
    '                    message = "Choose another provider category or All.",',
    '                    message = "Choose another provider category.",',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryShell.kt",
    '''    LazyRow(horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm)) {
        item(key = "all") {
            LibraryCategoryChip("All", selectedCategoryKey == null) { onSelected(null) }
        }
        items(categories, key = { it.categoryKey }) { category ->
''',
    '''    LazyRow(horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm)) {
        items(categories, key = { it.categoryKey }) { category ->
''',
)

# Keep browse/settings/library portrait; actual fullscreen playback explicitly requests landscape.
replace_once(
    "app/src/main/AndroidManifest.xml",
    '''            android:configChanges="orientation|screenLayout|screenSize|smallestScreenSize"
            android:exported="true"''',
    '''            android:configChanges="orientation|screenLayout|screenSize|smallestScreenSize"
            android:screenOrientation="portrait"
            android:exported="true"''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/MainActivity.kt",
    "import android.content.pm.PackageManager\n",
    "import android.content.pm.ActivityInfo\nimport android.content.pm.PackageManager\n",
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/MainActivity.kt",
    '''                    contentFullscreen = fullscreen
                    setImmersiveFullscreen(fullscreen)
                    updatePictureInPictureParams()
''',
    '''                    contentFullscreen = fullscreen
                    setContentOrientation(fullscreen)
                    setImmersiveFullscreen(fullscreen)
                    updatePictureInPictureParams()
''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/MainActivity.kt",
    '''    private fun setImmersiveFullscreen(fullscreen: Boolean) {
''',
    '''    private fun setContentOrientation(fullscreen: Boolean) {
        requestedOrientation = if (fullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    private fun setImmersiveFullscreen(fullscreen: Boolean) {
''',
)

# Live EPG domain contract. EPG remains optional and never blocks playback.
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/domain/LiveModels.kt",
    '''data class LiveCatalog(
    val activeSourceId: String? = null,
    val activeSourceName: String? = null,
    val categories: List<LiveCategory> = emptyList(),
    val channels: List<LiveChannel> = emptyList(),
)

class ResolvedLivePlayback''',
    '''data class LiveCatalog(
    val activeSourceId: String? = null,
    val activeSourceName: String? = null,
    val categories: List<LiveCategory> = emptyList(),
    val channels: List<LiveChannel> = emptyList(),
)

data class LiveProgram(
    val title: String,
    val startEpochSeconds: Long?,
    val endEpochSeconds: Long?,
)

data class LiveNowNext(
    val now: LiveProgram? = null,
    val next: LiveProgram? = null,
)

class ResolvedLivePlayback''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/domain/LiveModels.kt",
    '''interface LiveRepository {
    fun observeCatalog(): Flow<LiveCatalog>
    suspend fun resolvePlayback(channelId: String): LivePlaybackResolution
}
''',
    '''interface LiveRepository {
    fun observeCatalog(): Flow<LiveCatalog>
    suspend fun loadNowNext(channelId: String): LiveNowNext
    suspend fun resolvePlayback(channelId: String): LivePlaybackResolution
}
''',
)

# Reuse existing Xtream get_short_epg support with a bounded in-memory TTL cache.
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/data/LiveRepositoryImpl.kt",
    "import app.ownplay.mobile.feature.live.domain.LiveCatalog\n",
    "import app.ownplay.mobile.feature.live.domain.LiveCatalog\nimport app.ownplay.mobile.feature.live.domain.LiveGuidePolicy\nimport app.ownplay.mobile.feature.live.domain.LiveNowNext\nimport app.ownplay.mobile.feature.live.domain.LiveProgram\n",
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/data/LiveRepositoryImpl.kt",
    "import app.ownplay.mobile.sources.data.xtream.XtreamUrlBuilder\n",
    "import app.ownplay.mobile.sources.data.xtream.XtreamClient\nimport app.ownplay.mobile.sources.data.xtream.XtreamResult\nimport app.ownplay.mobile.sources.data.xtream.XtreamUrlBuilder\n",
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/data/LiveRepositoryImpl.kt",
    "import java.util.Locale\n",
    "import java.util.Locale\nimport java.util.concurrent.ConcurrentHashMap\n",
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/data/LiveRepositoryImpl.kt",
    '''    private val catalogDao: CatalogDao,
    private val credentialStore: CredentialStore,
) : LiveRepository {
''',
    '''    private val catalogDao: CatalogDao,
    private val credentialStore: CredentialStore,
    private val xtreamClient: XtreamClient,
) : LiveRepository {
    private data class GuideCacheEntry(
        val loadedAtMs: Long,
        val guide: LiveNowNext,
    )

    private val guideCache = ConcurrentHashMap<String, GuideCacheEntry>()
''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/data/LiveRepositoryImpl.kt",
    '''    override suspend fun resolvePlayback(channelId: String): LivePlaybackResolution {
''',
    '''    override suspend fun loadNowNext(channelId: String): LiveNowNext {
        if (channelId.isBlank()) return LiveNowNext()
        val nowMs = System.currentTimeMillis()
        guideCache[channelId]
            ?.takeIf { nowMs - it.loadedAtMs < GUIDE_CACHE_TTL_MS }
            ?.let { return it.guide }

        val guide = try {
            val channel = catalogDao.getLiveChannel(channelId) ?: return LiveNowNext()
            val source = sourceDao.get(channel.sourceId) ?: return LiveNowNext()
            if (!channel.available || !source.enabled || source.type != SourceType.XTREAM.name) {
                return LiveNowNext()
            }
            val streamId = channel.providerStreamId ?: return LiveNowNext()
            val credential = credentialStore.get(source.sourceId) as? SourceCredential.Xtream
                ?: return LiveNowNext()
            when (val result = xtreamClient.shortEpg(source.baseLocator, credential, streamId, limit = 4)) {
                is XtreamResult.Failure -> LiveNowNext()
                is XtreamResult.Success -> LiveGuidePolicy.nowNext(
                    programs = result.value.map { entry ->
                        LiveProgram(
                            title = entry.title.trim(),
                            startEpochSeconds = entry.startEpochSeconds,
                            endEpochSeconds = entry.endEpochSeconds,
                        )
                    },
                    nowEpochSeconds = nowMs / 1_000L,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            LiveNowNext()
        }
        guideCache[channelId] = GuideCacheEntry(nowMs, guide)
        return guide
    }

    override suspend fun resolvePlayback(channelId: String): LivePlaybackResolution {
''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/data/LiveRepositoryImpl.kt",
    '''    private fun failure(code: String, message: String): LivePlaybackResolution.Failure =
        LivePlaybackResolution.Failure(code = code, safeMessage = message)
}
''',
    '''    private fun failure(code: String, message: String): LivePlaybackResolution.Failure =
        LivePlaybackResolution.Failure(code = code, safeMessage = message)

    private companion object {
        const val GUIDE_CACHE_TTL_MS = 120_000L
    }
}
''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/core/OwnPlayServices.kt",
    '''            catalogDao = database.catalogDao(),
            credentialStore = credentialStore,
        )
''',
    '''            catalogDao = database.catalogDao(),
            credentialStore = credentialStore,
            xtreamClient = xtreamClient,
        )
''',
)

# Track audio capability in playback state and enable Media3 decoder fallback.
replace_once(
    "app/src/main/java/app/ownplay/mobile/playback/domain/PlaybackModels.kt",
    '''    activeTarget: VideoTarget = VideoTarget.NONE,
    errorCode: Int? = null,
)''',
    '''    activeTarget: VideoTarget = VideoTarget.NONE,
    audioTrackPresent: Boolean? = null,
    audioTrackSupported: Boolean? = null,
    audioTrackSelected: Boolean? = null,
    errorCode: Int? = null,
)''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/playback/Media3PlaybackController.kt",
    "import androidx.media3.common.Player\n",
    "import androidx.media3.common.Player\nimport androidx.media3.common.Tracks\n",
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/playback/Media3PlaybackController.kt",
    "import androidx.media3.exoplayer.ExoPlayer\n",
    "import androidx.media3.exoplayer.DefaultRenderersFactory\nimport androidx.media3.exoplayer.ExoPlayer\n",
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/playback/Media3PlaybackController.kt",
    '''        override fun onIsPlayingChanged(isPlaying: Boolean) {
            refreshSnapshot()
        }

        override fun onPlayerError(error: PlaybackException) {
''',
    '''        override fun onIsPlayingChanged(isPlaying: Boolean) {
            refreshSnapshot()
        }

        override fun onTracksChanged(tracks: Tracks) {
            refreshSnapshot()
        }

        override fun onPlayerError(error: PlaybackException) {
''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/playback/Media3PlaybackController.kt",
    '''    @OptIn(UnstableApi::class)
    private fun createPlayer(context: Context): ExoPlayer =
        ExoPlayer.Builder(context)
            .setLooper(Looper.getMainLooper())
            .build()
''',
    '''    @OptIn(UnstableApi::class)
    private fun createPlayer(context: Context): ExoPlayer {
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        return ExoPlayer.Builder(context, renderersFactory)
            .setLooper(Looper.getMainLooper())
            .build()
    }
''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/playback/Media3PlaybackController.kt",
    '''        val media = currentMedia
        mutableState.value = PlaybackSnapshot(
''',
    '''        val media = currentMedia
        val audio = currentAudioTrackStatus()
        mutableState.value = PlaybackSnapshot(
''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/playback/Media3PlaybackController.kt",
    '''            durationMs = player.duration.takeUnless { it == C.TIME_UNSET || it < 0L },
            activeTarget = ownership.activeTarget,
            errorCode = errorCode,
''',
    '''            durationMs = player.duration.takeUnless { it == C.TIME_UNSET || it < 0L },
            activeTarget = ownership.activeTarget,
            audioTrackPresent = audio.present,
            audioTrackSupported = audio.supported,
            audioTrackSelected = audio.selected,
            errorCode = errorCode,
''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/playback/Media3PlaybackController.kt",
    '''    private fun Int.toPlaybackPhase(): PlaybackPhase = when (this) {
''',
    '''    private data class AudioTrackStatus(
        val present: Boolean?,
        val supported: Boolean?,
        val selected: Boolean?,
    )

    private fun currentAudioTrackStatus(): AudioTrackStatus {
        val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (groups.isEmpty()) return AudioTrackStatus(null, null, null)
        return AudioTrackStatus(
            present = true,
            supported = groups.any { it.isSupported() },
            selected = groups.any { it.isSelected() },
        )
    }

    private fun Int.toPlaybackPhase(): PlaybackPhase = when (this) {
''',
)

# Live UI: hide utility categories robustly, lazy-load viewport EPG, rotate Preview into fullscreen,
# and retry the existing HLS candidate when the primary stream exposes unusable audio.
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt",
    "import android.view.SurfaceView\n",
    "import android.view.OrientationEventListener\nimport android.view.SurfaceView\n",
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt",
    "import app.ownplay.mobile.feature.live.domain.LiveEffect\n",
    "import app.ownplay.mobile.feature.live.domain.LiveEffect\nimport app.ownplay.mobile.feature.live.domain.LiveNowNext\nimport app.ownplay.mobile.feature.live.domain.LiveProgram\n",
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt",
    "import kotlinx.coroutines.CoroutineScope\n",
    "import java.time.Instant\nimport java.time.ZoneId\nimport java.time.format.DateTimeFormatter\nimport kotlinx.coroutines.CoroutineScope\n",
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt",
    '''    val channels = catalog?.channels.orEmpty()
    val categories = LiveBrowsePolicy.visibleCategories(catalog?.categories.orEmpty())
    var selectedCategoryKey by remember(catalog?.activeSourceId) { mutableStateOf<String?>(null) }
    val activeCategoryKey = LiveBrowsePolicy.activeCategoryKey(categories, selectedCategoryKey)
    val visibleChannels = activeCategoryKey?.let { key ->
        channels.filter { channel -> channel.categoryKey == key }
    } ?: channels
    val selectedChannel = channels.firstOrNull { it.channelId == presentationState.selectedChannelId }
''',
    '''    val channels = catalog?.channels.orEmpty()
    val rawCategories = catalog?.categories.orEmpty()
    val categories = LiveBrowsePolicy.visibleCategories(rawCategories)
    var selectedCategoryKey by remember(catalog?.activeSourceId) { mutableStateOf<String?>(null) }
    val activeCategoryKey = LiveBrowsePolicy.activeCategoryKey(categories, selectedCategoryKey)
    val visibleChannels = activeCategoryKey?.let { key ->
        channels.filter { channel -> channel.categoryKey == key }
    } ?: channels
    val selectedChannel = channels.firstOrNull { it.channelId == presentationState.selectedChannelId }
    val selectedGuide = rememberLiveGuide(liveRepository, selectedChannel?.channelId)
    val audioCompatibilityMessage = when {
        fallbackLoadRequest != null -> null
        playback.phase == PlaybackPhase.READY &&
            playback.audioTrackPresent == true &&
            playback.audioTrackSupported == false -> "Audio format is not supported by this device."
        playback.phase == PlaybackPhase.READY &&
            playback.audioTrackPresent == true &&
            playback.audioTrackSelected == false -> "The channel audio track could not be selected."
        else -> null
    }
''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt",
    '''    LaunchedEffect(playback.phase, fallbackLoadRequest) {
        if (playback.phase == PlaybackPhase.ERROR) {
            val fallback = fallbackLoadRequest ?: return@LaunchedEffect
            fallbackLoadRequest = null
            playbackController.load(fallback)
        }
    }
''',
    '''    LaunchedEffect(
        playback.phase,
        playback.audioTrackPresent,
        playback.audioTrackSupported,
        playback.audioTrackSelected,
        fallbackLoadRequest,
    ) {
        if (LivePlaybackFallbackPolicy.shouldUseFallback(playback)) {
            val fallback = fallbackLoadRequest ?: return@LaunchedEffect
            fallbackLoadRequest = null
            playbackController.load(fallback)
        }
    }
''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt",
    '''    BackHandler(enabled = presentationState.presentation != LivePresentation.BROWSE) {
        dispatch(LiveIntent.BackPressed)
    }
''',
    '''    val orientationContext = LocalContext.current
    DisposableEffect(
        orientationContext,
        presentationState.presentation,
        presentationState.selectedChannelId,
    ) {
        val selectedId = presentationState.selectedChannelId
        if (presentationState.presentation != LivePresentation.PREVIEW || selectedId == null) {
            onDispose { }
        } else {
            var landscapeTriggered = false
            val listener = object : OrientationEventListener(orientationContext) {
                override fun onOrientationChanged(orientation: Int) {
                    if (orientation == ORIENTATION_UNKNOWN) return
                    when {
                        LiveOrientationPolicy.isLandscape(orientation) && !landscapeTriggered -> {
                            landscapeTriggered = true
                            dispatch(LiveIntent.ChannelTapped(selectedId))
                        }
                        LiveOrientationPolicy.isPortrait(orientation) -> landscapeTriggered = false
                    }
                }
            }
            if (listener.canDetectOrientation()) listener.enable()
            onDispose { listener.disable() }
        }
    }

    BackHandler(enabled = presentationState.presentation != LivePresentation.BROWSE) {
        dispatch(LiveIntent.BackPressed)
    }
''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt",
    '''            playbackPhase = playback.phase,
            resolutionError = resolutionError,
            modifier = modifier,
''',
    '''            playbackPhase = playback.phase,
            resolutionError = resolutionError,
            guide = selectedGuide,
            audioCompatibilityMessage = audioCompatibilityMessage,
            modifier = modifier,
''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt",
    '''            catalog = catalog,
            channels = visibleChannels,
            categories = categories,
''',
    '''            catalog = catalog,
            channels = visibleChannels,
            categories = categories,
            liveRepository = liveRepository,
''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt",
    '''            playbackPhase = playback.phase,
            resolutionError = resolutionError,
            onChannelTapped = { channel ->
''',
    '''            playbackPhase = playback.phase,
            resolutionError = resolutionError,
            selectedGuide = selectedGuide,
            audioCompatibilityMessage = audioCompatibilityMessage,
            onChannelTapped = { channel ->
''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt",
    '''    channels: List<LiveChannel>,
    categories: List<LiveCategory>,
    selectedCategoryKey: String?,
''',
    '''    channels: List<LiveChannel>,
    categories: List<LiveCategory>,
    liveRepository: LiveRepository,
    selectedCategoryKey: String?,
''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt",
    '''    playbackPhase: PlaybackPhase,
    resolutionError: String?,
    onChannelTapped: (LiveChannel) -> Unit,
''',
    '''    playbackPhase: PlaybackPhase,
    resolutionError: String?,
    selectedGuide: LiveNowNext,
    audioCompatibilityMessage: String?,
    onChannelTapped: (LiveChannel) -> Unit,
''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt",
    '''            ) { index, channel ->
                val selected = channel.channelId == selectedChannel?.channelId
                Column(
''',
    '''            ) { index, channel ->
                val selected = channel.channelId == selectedChannel?.channelId
                val guide = if (selected) selectedGuide else rememberLiveGuide(liveRepository, channel.channelId)
                Column(
''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt",
    '''                            playbackPhase = playbackPhase,
                            resolutionError = resolutionError,
                        )
''',
    '''                            playbackPhase = playbackPhase,
                            resolutionError = resolutionError,
                            audioCompatibilityMessage = audioCompatibilityMessage,
                        )
''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt",
    '''                        channel = channel,
                        selected = selected,
                        onClick = { onChannelTapped(channel) },
''',
    '''                        channel = channel,
                        selected = selected,
                        guide = guide,
                        onClick = { onChannelTapped(channel) },
''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt",
    "                        NowPlayingPanel(selectedChannel = channel)\n",
    "                        NowPlayingPanel(selectedChannel = channel, guide = guide)\n",
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt",
    '''    playbackPhase: PlaybackPhase,
    resolutionError: String?,
) {
''',
    '''    playbackPhase: PlaybackPhase,
    resolutionError: String?,
    audioCompatibilityMessage: String?,
) {
''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt",
    '''                playbackPhase == PlaybackPhase.ERROR -> "Playback unavailable"
                playbackPhase == PlaybackPhase.BUFFERING -> "Loading ${selectedChannel.name}…"
''',
    '''                playbackPhase == PlaybackPhase.ERROR -> "Playback unavailable"
                audioCompatibilityMessage != null -> audioCompatibilityMessage
                playbackPhase == PlaybackPhase.BUFFERING -> "Loading ${selectedChannel.name}…"
''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt",
    '''private fun NowPlayingPanel(selectedChannel: LiveChannel?) {
''',
    '''private fun NowPlayingPanel(selectedChannel: LiveChannel?, guide: LiveNowNext) {
''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt",
    '''                Text(
                    text = "Now Playing",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OwnPlayColors.TextSecondary,
                )
                Text(
                    text = selectedChannel?.name ?: "Select a channel",
                    style = MaterialTheme.typography.titleLarge,
                    color = OwnPlayColors.TextPrimary,
                )
                Text(
                    text = if (selectedChannel == null) "Live preview is idle" else "Live channel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OwnPlayColors.TextSecondary,
                )
''',
    '''                Text(
                    text = "Now",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OwnPlayColors.TextSecondary,
                )
                Text(
                    text = guide.now?.title ?: selectedChannel?.name ?: "Select a channel",
                    style = MaterialTheme.typography.titleLarge,
                    color = OwnPlayColors.TextPrimary,
                )
                Text(
                    text = guide.now?.let(::programTimeRange) ?: "Guide unavailable",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OwnPlayColors.TextSecondary,
                )
''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt",
    '''                Text(
                    text = "Guide unavailable",
                    style = MaterialTheme.typography.titleMedium,
                    color = OwnPlayColors.TextPrimary,
                )
                Text(
                    text = "EPG is optional and never blocks playback",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OwnPlayColors.TextSecondary,
                )
''',
    '''                Text(
                    text = guide.next?.title ?: "Guide unavailable",
                    style = MaterialTheme.typography.titleMedium,
                    color = OwnPlayColors.TextPrimary,
                )
                Text(
                    text = guide.next?.let(::programTimeRange) ?: "EPG is optional and never blocks playback",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OwnPlayColors.TextSecondary,
                )
''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt",
    '''    channel: LiveChannel,
    selected: Boolean,
    onClick: () -> Unit,
''',
    '''    channel: LiveChannel,
    selected: Boolean,
    guide: LiveNowNext,
    onClick: () -> Unit,
''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt",
    '''                Text(
                    text = if (selected) "Previewing now" else "Live channel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OwnPlayColors.TextSecondary,
                )
''',
    '''                Text(
                    text = guide.now?.let { program -> "Now ${programTimeRange(program)} • ${program.title}" }
                        ?: if (selected) "Previewing now" else "Live channel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OwnPlayColors.TextSecondary,
                    maxLines = 1,
                )
                guide.next?.let { next ->
                    Text(
                        text = "Next ${programTimeRange(next)} • ${next.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OwnPlayColors.TextSecondary,
                        maxLines = 1,
                    )
                }
''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt",
    '''    playbackPhase: PlaybackPhase,
    resolutionError: String?,
    modifier: Modifier = Modifier,
) {
''',
    '''    playbackPhase: PlaybackPhase,
    resolutionError: String?,
    guide: LiveNowNext,
    audioCompatibilityMessage: String?,
    modifier: Modifier = Modifier,
) {
''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt",
    '''                        Text(
                            text = when {
                                resolutionError != null -> resolutionError
                                playbackPhase == PlaybackPhase.ERROR -> "Playback unavailable"
                                playbackPhase == PlaybackPhase.BUFFERING -> "Buffering live stream…"
                                else -> "Live • Guide unavailable"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = OwnPlayColors.TextSecondary,
                        )
''',
    '''                        Text(
                            text = when {
                                resolutionError != null -> resolutionError
                                playbackPhase == PlaybackPhase.ERROR -> "Playback unavailable"
                                audioCompatibilityMessage != null -> audioCompatibilityMessage
                                playbackPhase == PlaybackPhase.BUFFERING -> "Buffering live stream…"
                                guide.now != null -> "Now ${programTimeRange(guide.now)} • ${guide.now.title}"
                                else -> "Live • Guide unavailable"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = OwnPlayColors.TextSecondary,
                            maxLines = 1,
                        )
                        guide.next?.let { next ->
                            Text(
                                text = "Next ${programTimeRange(next)} • ${next.title}",
                                style = MaterialTheme.typography.bodySmall,
                                color = OwnPlayColors.TextSecondary,
                                maxLines = 1,
                            )
                        }
''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt",
    '''private fun channelNumber(
''',
    '''@Composable
private fun rememberLiveGuide(
    liveRepository: LiveRepository,
    channelId: String?,
): LiveNowNext {
    var guide by remember(liveRepository, channelId) { mutableStateOf(LiveNowNext()) }
    LaunchedEffect(liveRepository, channelId) {
        guide = channelId?.let { liveRepository.loadNowNext(it) } ?: LiveNowNext()
    }
    return guide
}

private fun programTimeRange(program: LiveProgram): String {
    val start = program.startEpochSeconds?.let(::formatEpgTime)
    val end = program.endEpochSeconds?.let(::formatEpgTime)
    return when {
        start != null && end != null -> "$start–$end"
        start != null -> start
        end != null -> "until $end"
        else -> ""
    }
}

private fun formatEpgTime(epochSeconds: Long): String = Instant
    .ofEpochSecond(epochSeconds)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("HH:mm"))

private fun channelNumber(
''',
)

# Tests for the user-reported regressions and pure policies.
write(
    "app/src/test/java/app/ownplay/mobile/sources/domain/ProviderCategoryVisibilityTest.kt",
    '''package app.ownplay.mobile.sources.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCategoryVisibilityTest {
    @Test
    fun `utility labels tolerate provider punctuation and suffixes`() {
        assertTrue(ProviderCategoryVisibility.isUtilityLabel("All"))
        assertTrue(ProviderCategoryVisibility.isUtilityLabel("ALL CHANNELS"))
        assertTrue(ProviderCategoryVisibility.isUtilityLabel("• Account Information •"))
        assertTrue(ProviderCategoryVisibility.isUtilityLabel("ACCOUNT_INFO [expires soon]"))
        assertFalse(ProviderCategoryVisibility.isUtilityLabel("All Sports"))
        assertFalse(ProviderCategoryVisibility.isUtilityLabel("News"))
    }
}
''',
)
write(
    "app/src/test/java/app/ownplay/mobile/feature/live/ui/LiveBrowsePolicyTest.kt",
    '''package app.ownplay.mobile.feature.live.ui

import app.ownplay.mobile.feature.live.domain.LiveCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveBrowsePolicyTest {
    @Test
    fun `provider utility categories are hidden and first real category becomes active`() {
        val categories = listOf(
            LiveCategory("all", "ALL CHANNELS", 0),
            LiveCategory("account", "• Account Information — expires soon", 1),
            LiveCategory("news", "News", 2),
            LiveCategory("sports", "All Sports", 3),
        )
        val visible = LiveBrowsePolicy.visibleCategories(categories)
        assertEquals(listOf("news", "sports"), visible.map { it.categoryKey })
        assertEquals("news", LiveBrowsePolicy.activeCategoryKey(visible, null))
        assertEquals("sports", LiveBrowsePolicy.activeCategoryKey(visible, "sports"))
        assertEquals("news", LiveBrowsePolicy.activeCategoryKey(visible, "missing"))
    }
}
''',
)
write(
    "app/src/test/java/app/ownplay/mobile/feature/library/ui/LibraryBrowsePolicyTest.kt",
    '''package app.ownplay.mobile.feature.library.ui

import app.ownplay.mobile.feature.library.domain.LibraryCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryBrowsePolicyTest {
    @Test
    fun `library uses provider categories without synthetic all`() {
        val categories = listOf(
            LibraryCategory("all", "All Movies", 0),
            LibraryCategory("action", "Action", 1),
            LibraryCategory("drama", "Drama", 2),
        )
        val visible = LibraryBrowsePolicy.visibleCategories(categories)
        assertEquals(listOf("action", "drama"), visible.map { it.categoryKey })
        assertEquals("action", LibraryBrowsePolicy.activeCategoryKey(visible, null))
    }
}
''',
)
write(
    "app/src/test/java/app/ownplay/mobile/feature/live/domain/LiveGuidePolicyTest.kt",
    '''package app.ownplay.mobile.feature.live.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveGuidePolicyTest {
    @Test
    fun `now next are selected from provider timestamps`() {
        val guide = LiveGuidePolicy.nowNext(
            programs = listOf(
                LiveProgram("Previous", 700, 900),
                LiveProgram("Current", 900, 1_100),
                LiveProgram("Next", 1_100, 1_300),
            ),
            nowEpochSeconds = 1_000,
        )
        assertEquals("Current", guide.now?.title)
        assertEquals("Next", guide.next?.title)
    }

    @Test
    fun `future-only guide exposes next without inventing now`() {
        val guide = LiveGuidePolicy.nowNext(
            programs = listOf(LiveProgram("Upcoming", 1_200, 1_400)),
            nowEpochSeconds = 1_000,
        )
        assertNull(guide.now)
        assertEquals("Upcoming", guide.next?.title)
    }
}
''',
)
write(
    "app/src/test/java/app/ownplay/mobile/feature/live/ui/LiveOrientationPolicyTest.kt",
    '''package app.ownplay.mobile.feature.live.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveOrientationPolicyTest {
    @Test
    fun `physical landscape bands are detected without rotating browse ui`() {
        assertTrue(LiveOrientationPolicy.isLandscape(90))
        assertTrue(LiveOrientationPolicy.isLandscape(270))
        assertFalse(LiveOrientationPolicy.isLandscape(0))
        assertTrue(LiveOrientationPolicy.isPortrait(0))
        assertTrue(LiveOrientationPolicy.isPortrait(180))
    }
}
''',
)
write(
    "app/src/test/java/app/ownplay/mobile/feature/live/ui/LivePlaybackFallbackPolicyTest.kt",
    '''package app.ownplay.mobile.feature.live.ui

import app.ownplay.mobile.playback.domain.PlaybackPhase
import app.ownplay.mobile.playback.domain.PlaybackSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePlaybackFallbackPolicyTest {
    @Test
    fun `player error uses fallback`() {
        assertTrue(LivePlaybackFallbackPolicy.shouldUseFallback(PlaybackSnapshot(phase = PlaybackPhase.ERROR)))
    }

    @Test
    fun `ready stream with unsupported audio uses fallback`() {
        assertTrue(
            LivePlaybackFallbackPolicy.shouldUseFallback(
                PlaybackSnapshot(
                    phase = PlaybackPhase.READY,
                    audioTrackPresent = true,
                    audioTrackSupported = false,
                    audioTrackSelected = false,
                ),
            ),
        )
    }

    @Test
    fun `ready stream with selected supported audio stays on primary`() {
        assertFalse(
            LivePlaybackFallbackPolicy.shouldUseFallback(
                PlaybackSnapshot(
                    phase = PlaybackPhase.READY,
                    audioTrackPresent = true,
                    audioTrackSupported = true,
                    audioTrackSelected = true,
                ),
            ),
        )
    }
}
''',
)

append_once(
    "docs/audit/STAGE_12_STABILIZATION.txt",
    "Physical QA round 3 findings and corrections",
    '''Physical QA round 3 findings and corrections
- Physical QA of stable-signer QA v4 reported remaining provider utility category labels (including Account Information variants), synthetic All chips in Movies/Series, browse UI rotation instead of Preview -> fullscreen rotation behavior, silent audio on some provider channels, and missing provider EPG in Live rows.
- Provider category presentation now normalizes punctuation/case, removes known utility-only All/Account Information labels without altering persisted provider metadata, and Library no longer synthesizes an All chip. The first real provider category becomes active deterministically.
- Normal application browsing is portrait-locked. While a Live Preview is open, physical landscape orientation is detected and transitions the existing selected channel into fullscreen without issuing a second channel load. Fullscreen requests sensor-landscape; leaving fullscreen returns to portrait.
- Media3 now enables lower-priority decoder fallback and extension-renderer discovery. Playback state records whether an audio track is present, supported, and selected. Live uses the existing one-shot .m3u8 candidate if the primary .ts stream errors or reaches READY with an unusable audio track; no second player/session is created.
- Existing Xtream get_short_epg support is now connected to Live presentation. EPG is loaded lazily for composed channel rows, cached briefly in memory, and rendered as Now/Next in rows plus selected preview/fullscreen context. EPG failure remains non-blocking and does not prevent playback.
- No Room schema change or migration is required: stable channel identity and provider stream ids already support EPG association at runtime.
- No provider URL, username, password, token, stream URL, signing password, private key, or other secret is recorded here.
- Exact final source validation and physical-device verification remain required. This source correction does not authorize or produce a new QA APK.''',
)

print("Stage 12 round 3 patch applied")
