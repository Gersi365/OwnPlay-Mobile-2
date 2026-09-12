from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match in {path}, found {count}")
    file.write_text(text.replace(old, new, 1))


# 1) Carry one bounded alternate Xtream playback candidate without exposing its URI.
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/library/domain/LibraryModels.kt",
    '''    val uri: String,\n    val streamFormat: PlaybackStreamFormat,\n    val start: PlaybackStart,\n    val knownDurationMs: Long?,\n    val offline: Boolean = false,\n) {\n    override fun toString(): String =\n        "ResolvedLibraryPlayback(sourceId=$sourceId, contentId=$contentId, mediaKind=$mediaKind, uri=<redacted>, streamFormat=$streamFormat, start=$start, offline=$offline)"\n}\n''',
    '''    val uri: String,\n    val streamFormat: PlaybackStreamFormat,\n    val fallbackUri: String? = null,\n    val fallbackStreamFormat: PlaybackStreamFormat? = null,\n    val start: PlaybackStart,\n    val knownDurationMs: Long?,\n    val offline: Boolean = false,\n) {\n    override fun toString(): String =\n        "ResolvedLibraryPlayback(sourceId=$sourceId, contentId=$contentId, mediaKind=$mediaKind, uri=<redacted>, streamFormat=$streamFormat, fallbackUri=<redacted>, fallbackStreamFormat=$fallbackStreamFormat, start=$start, offline=$offline)"\n}\n''',
)

# 2) Generate one deterministic alternate candidate: HLS for progressive/unknown metadata,
#    extensionless when the provider already declared HLS.
locator = Path("app/src/main/java/app/ownplay/mobile/feature/library/data/LibraryPlaybackLocator.kt")
text = locator.read_text()
needle = '''    fun episodeUri(\n        baseUrl: String,\n        credential: SourceCredential.Xtream,\n        providerEpisodeId: String,\n        extension: String?,\n    ): String = XtreamUrlBuilder.streamUrl(\n        baseUrl = baseUrl,\n        credential = credential,\n        kind = "series",\n        providerId = providerEpisodeId,\n        extension = extension,\n    )\n\n'''
insert = needle + '''    fun movieFallbackUri(\n        baseUrl: String,\n        credential: SourceCredential.Xtream,\n        providerStreamId: String,\n        extension: String?,\n    ): String = XtreamUrlBuilder.streamUrl(\n        baseUrl = baseUrl,\n        credential = credential,\n        kind = "movie",\n        providerId = providerStreamId,\n        extension = alternateExtension(extension),\n    )\n\n    fun episodeFallbackUri(\n        baseUrl: String,\n        credential: SourceCredential.Xtream,\n        providerEpisodeId: String,\n        extension: String?,\n    ): String = XtreamUrlBuilder.streamUrl(\n        baseUrl = baseUrl,\n        credential = credential,\n        kind = "series",\n        providerId = providerEpisodeId,\n        extension = alternateExtension(extension),\n    )\n\n'''
if text.count(needle) != 1:
    raise SystemExit("LibraryPlaybackLocator episodeUri anchor mismatch")
text = text.replace(needle, insert, 1)
needle2 = '''    fun streamFormatFor(uri: String): PlaybackStreamFormat {\n'''
replacement2 = '''    private fun alternateExtension(extension: String?): String? {\n        val normalized = extension\n            ?.trim()\n            ?.removePrefix(".")\n            ?.lowercase(Locale.US)\n            ?.takeIf(String::isNotBlank)\n        return if (normalized == "m3u8") null else "m3u8"\n    }\n\n    fun streamFormatFor(uri: String): PlaybackStreamFormat {\n'''
if text.count(needle2) != 1:
    raise SystemExit("LibraryPlaybackLocator streamFormatFor anchor mismatch")
locator.write_text(text.replace(needle2, replacement2, 1))

# 3) Resolve primary + one alternate URI for Movies and Episodes.
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/library/data/LibraryRepositoryImpl.kt",
    '''            val uri = LibraryPlaybackLocator.movieUri(\n                baseUrl = source.baseLocator,\n                credential = credential,\n                providerStreamId = movie.providerStreamId,\n                extension = movie.extension,\n            )\n            LibraryPlaybackResolution.Success(\n''',
    '''            val uri = LibraryPlaybackLocator.movieUri(\n                baseUrl = source.baseLocator,\n                credential = credential,\n                providerStreamId = movie.providerStreamId,\n                extension = movie.extension,\n            )\n            val fallbackUri = LibraryPlaybackLocator.movieFallbackUri(\n                baseUrl = source.baseLocator,\n                credential = credential,\n                providerStreamId = movie.providerStreamId,\n                extension = movie.extension,\n            ).takeUnless { it == uri }\n            LibraryPlaybackResolution.Success(\n''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/library/data/LibraryRepositoryImpl.kt",
    '''                    uri = uri,\n                    streamFormat = LibraryPlaybackLocator.streamFormatFor(uri),\n                    start = LibraryStartPolicy.resolve(startMode, progress?.positionMs),\n''',
    '''                    uri = uri,\n                    streamFormat = LibraryPlaybackLocator.streamFormatFor(uri),\n                    fallbackUri = fallbackUri,\n                    fallbackStreamFormat = fallbackUri?.let(LibraryPlaybackLocator::streamFormatFor),\n                    start = LibraryStartPolicy.resolve(startMode, progress?.positionMs),\n''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/library/data/LibraryRepositoryImpl.kt",
    '''            val uri = LibraryPlaybackLocator.episodeUri(\n                baseUrl = source.baseLocator,\n                credential = credential,\n                providerEpisodeId = episode.providerEpisodeId,\n                extension = episode.extension,\n            )\n            LibraryPlaybackResolution.Success(\n''',
    '''            val uri = LibraryPlaybackLocator.episodeUri(\n                baseUrl = source.baseLocator,\n                credential = credential,\n                providerEpisodeId = episode.providerEpisodeId,\n                extension = episode.extension,\n            )\n            val fallbackUri = LibraryPlaybackLocator.episodeFallbackUri(\n                baseUrl = source.baseLocator,\n                credential = credential,\n                providerEpisodeId = episode.providerEpisodeId,\n                extension = episode.extension,\n            ).takeUnless { it == uri }\n            LibraryPlaybackResolution.Success(\n''',
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/library/data/LibraryRepositoryImpl.kt",
    '''                    uri = uri,\n                    streamFormat = LibraryPlaybackLocator.streamFormatFor(uri),\n                    start = LibraryStartPolicy.resolve(startMode, resumePosition),\n''',
    '''                    uri = uri,\n                    streamFormat = LibraryPlaybackLocator.streamFormatFor(uri),\n                    fallbackUri = fallbackUri,\n                    fallbackStreamFormat = fallbackUri?.let(LibraryPlaybackLocator::streamFormatFor),\n                    start = LibraryStartPolicy.resolve(startMode, resumePosition),\n''',
)

# 4) One-shot startup fallback in the existing Library player. It is disabled after READY,
#    so a later network interruption cannot silently restart the title from its initial start position.
shell_path = "app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryShell.kt"
replace_once(
    shell_path,
    '''    val catalog by catalogFlow.collectAsState(initial = null)\n    val downloads by downloadsFlow.collectAsState(initial = emptyList())\n''',
    '''    val catalog by catalogFlow.collectAsState(initial = null)\n    val downloads by downloadsFlow.collectAsState(initial = emptyList())\n    val playbackState by playbackController.state.collectAsState()\n''',
)
replace_once(
    shell_path,
    '''    var resolutionError by remember { mutableStateOf<String?>(null) }\n    var activePlayback by remember { mutableStateOf<ResolvedLibraryPlayback?>(null) }\n''',
    '''    var resolutionError by remember { mutableStateOf<String?>(null) }\n    var activePlayback by remember { mutableStateOf<ResolvedLibraryPlayback?>(null) }\n    var fallbackLoadRequest by remember { mutableStateOf<PlaybackLoadRequest?>(null) }\n    var fallbackEligible by remember { mutableStateOf(false) }\n''',
)
replace_once(
    shell_path,
    '''            is LibraryPlaybackResolution.Success -> {\n                scope.launch {\n                    playbackController.load(resolved.value.toLoadRequest())\n                    activePlayback = resolved.value\n                }\n            }\n\n            is LibraryPlaybackResolution.Failure -> resolutionError = resolved.safeMessage\n''',
    '''            is LibraryPlaybackResolution.Success -> {\n                fallbackLoadRequest = resolved.value.toFallbackLoadRequest()\n                fallbackEligible = fallbackLoadRequest != null\n                scope.launch {\n                    playbackController.load(resolved.value.toLoadRequest())\n                    activePlayback = resolved.value\n                }\n            }\n\n            is LibraryPlaybackResolution.Failure -> {\n                fallbackLoadRequest = null\n                fallbackEligible = false\n                resolutionError = resolved.safeMessage\n            }\n''',
)
replace_once(
    shell_path,
    '''    LaunchedEffect(activePlayback) {\n        onFullscreenChanged(activePlayback != null)\n    }\n\n    DisposableEffect(Unit) {\n''',
    '''    LaunchedEffect(activePlayback) {\n        onFullscreenChanged(activePlayback != null)\n    }\n\n    LaunchedEffect(\n        activePlayback?.contentId,\n        playbackState.mediaId,\n        playbackState.phase,\n        fallbackEligible,\n        fallbackLoadRequest,\n    ) {\n        val active = activePlayback ?: return@LaunchedEffect\n        if (playbackState.mediaId != active.contentId) return@LaunchedEffect\n        if (playbackState.phase == PlaybackPhase.READY) {\n            fallbackEligible = false\n            return@LaunchedEffect\n        }\n        if (playbackState.phase == PlaybackPhase.ERROR && fallbackEligible) {\n            val fallback = fallbackLoadRequest ?: return@LaunchedEffect\n            fallbackEligible = false\n            fallbackLoadRequest = null\n            playbackController.load(fallback)\n        }\n    }\n\n    DisposableEffect(Unit) {\n''',
)
replace_once(
    shell_path,
    '''            onClose = {\n                activePlayback = null\n                resolutionError = null\n            },\n''',
    '''            onClose = {\n                activePlayback = null\n                fallbackLoadRequest = null\n                fallbackEligible = false\n                resolutionError = null\n            },\n''',
)
load_request_anchor = '''private fun ResolvedLibraryPlayback.toLoadRequest(): PlaybackLoadRequest = PlaybackLoadRequest(\n    media = PlaybackMedia(\n        id = contentId,\n        uri = uri,\n        title = title,\n        kind = if (offline) {\n            PlaybackKind.OFFLINE\n        } else {\n            when (mediaKind) {\n                LibraryMediaKind.MOVIE -> PlaybackKind.MOVIE\n                LibraryMediaKind.EPISODE -> PlaybackKind.EPISODE\n            }\n        },\n        streamFormat = streamFormat,\n    ),\n    start = start,\n)\n\n'''
fallback_helper = load_request_anchor + '''private fun ResolvedLibraryPlayback.toFallbackLoadRequest(): PlaybackLoadRequest? {\n    val alternateUri = fallbackUri ?: return null\n    return PlaybackLoadRequest(\n        media = PlaybackMedia(\n            id = contentId,\n            uri = alternateUri,\n            title = title,\n            kind = when {\n                offline -> PlaybackKind.OFFLINE\n                mediaKind == LibraryMediaKind.MOVIE -> PlaybackKind.MOVIE\n                else -> PlaybackKind.EPISODE\n            },\n            streamFormat = fallbackStreamFormat ?: streamFormat,\n        ),\n        start = start,\n    )\n}\n\n'''
replace_once(shell_path, load_request_anchor, fallback_helper)

# 5) Harden Media3 HTTP playback transport for provider redirects while preserving the single player.
controller_path = "app/src/main/java/app/ownplay/mobile/playback/Media3PlaybackController.kt"
replace_once(
    controller_path,
    '''import androidx.media3.decoder.ffmpeg.FfmpegLibrary\nimport androidx.media3.exoplayer.DefaultRenderersFactory\nimport androidx.media3.exoplayer.ExoPlayer\n''',
    '''import androidx.media3.datasource.DefaultDataSource\nimport androidx.media3.datasource.DefaultHttpDataSource\nimport androidx.media3.decoder.ffmpeg.FfmpegLibrary\nimport androidx.media3.exoplayer.DefaultRenderersFactory\nimport androidx.media3.exoplayer.ExoPlayer\nimport androidx.media3.exoplayer.source.DefaultMediaSourceFactory\n''',
)
replace_once(
    controller_path,
    '''        val renderersFactory = DefaultRenderersFactory(context)\n            .setEnableDecoderFallback(true)\n            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)\n        return ExoPlayer.Builder(context, renderersFactory)\n            .setLooper(Looper.getMainLooper())\n            .build()\n''',
    '''        val renderersFactory = DefaultRenderersFactory(context)\n            .setEnableDecoderFallback(true)\n            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)\n        val httpDataSourceFactory = DefaultHttpDataSource.Factory()\n            .setAllowCrossProtocolRedirects(true)\n            .setUserAgent("OwnPlay Android")\n        val mediaSourceFactory = DefaultMediaSourceFactory(\n            DefaultDataSource.Factory(context, httpDataSourceFactory),\n        )\n        return ExoPlayer.Builder(context, renderersFactory)\n            .setMediaSourceFactory(mediaSourceFactory)\n            .setLooper(Looper.getMainLooper())\n            .build()\n''',
)

# 6) Focused locator/fallback tests and URI-redaction coverage.
test_path = "app/src/test/java/app/ownplay/mobile/feature/library/data/LibraryPlaybackLocatorTest.kt"
replace_once(
    test_path,
    '''    @Test\n    fun `non HLS locator uses automatic stream format`() {\n        assertEquals(\n            PlaybackStreamFormat.AUTO,\n            LibraryPlaybackLocator.streamFormatFor("https://provider.example/movie/a/b/42.mp4"),\n        )\n    }\n\n''',
    '''    @Test\n    fun `non HLS locator uses automatic stream format`() {\n        assertEquals(\n            PlaybackStreamFormat.AUTO,\n            LibraryPlaybackLocator.streamFormatFor("https://provider.example/movie/a/b/42.mp4"),\n        )\n    }\n\n    @Test\n    fun `progressive movie gets one HLS alternate candidate`() {\n        val fallback = LibraryPlaybackLocator.movieFallbackUri(\n            baseUrl = "https://provider.example",\n            credential = credential,\n            providerStreamId = "42",\n            extension = "mp4",\n        )\n\n        assertEquals(\n            "https://provider.example/movie/viewer%20name/p%40ss%20word/42.m3u8",\n            fallback,\n        )\n        assertEquals(PlaybackStreamFormat.HLS, LibraryPlaybackLocator.streamFormatFor(fallback))\n    }\n\n    @Test\n    fun `HLS episode gets extensionless alternate candidate`() {\n        val fallback = LibraryPlaybackLocator.episodeFallbackUri(\n            baseUrl = "https://provider.example",\n            credential = credential,\n            providerEpisodeId = "84",\n            extension = ".m3u8",\n        )\n\n        assertEquals(\n            "https://provider.example/series/viewer%20name/p%40ss%20word/84",\n            fallback,\n        )\n        assertEquals(PlaybackStreamFormat.AUTO, LibraryPlaybackLocator.streamFormatFor(fallback))\n    }\n\n''',
)
replace_once(
    test_path,
    '''            uri = uri,\n            streamFormat = PlaybackStreamFormat.AUTO,\n            start = PlaybackStart.Beginning,\n''',
    '''            uri = uri,\n            streamFormat = PlaybackStreamFormat.AUTO,\n            fallbackUri = "$uri-fallback-secret",\n            fallbackStreamFormat = PlaybackStreamFormat.HLS,\n            start = PlaybackStart.Beginning,\n''',
)
replace_once(
    test_path,
    '''        assertFalse(text.contains(uri))\n        assertFalse(text.contains("viewer"))\n''',
    '''        assertFalse(text.contains(uri))\n        assertFalse(text.contains("fallback-secret"))\n        assertFalse(text.contains("viewer"))\n''',
)

# 7) Source audit evidence.
audit = Path("docs/audit/STAGE22_LIBRARY_PLAYBACK_COMPATIBILITY_SOURCE_AUDIT.md")
if audit.exists():
    raise SystemExit("Stage 22 audit already exists")
audit.write_text('''# Stage 22 — Library playback compatibility source audit\n\n## Trigger and evidence\n\n- Physical QA of QA v24 (Stage 21 exact source `f33d06003e0aaac5cc18cab96a0c7e3cd9729a3d`) reported that Movies and Series enter buffering and then show `Playback unavailable`.\n- The report establishes a physical playback failure but does not expose a credential-safe Media3 transport error code, so this stage does not claim a single proven provider-side root cause.\n- Source inspection verified a compatibility gap: Live already has a bounded alternate stream candidate, while Library Movies/Episodes resolved exactly one Xtream URI and stopped after a player error.\n\n## Corrections\n\n1. Movie/Episode playback now carries one runtime-only alternate Xtream candidate.\n   - For progressive/unknown provider metadata, the alternate is the provider's HLS `.m3u8` route.\n   - If the provider already reports HLS, the alternate is the extensionless route.\n   - Only one alternate is retained; there is no unbounded extension guessing.\n2. Library playback consumes the alternate only when the primary fails during startup. Once the primary reaches READY, fallback eligibility is disabled so a later network interruption cannot silently restart media from its initial start position.\n3. Media3 keeps the existing single ExoPlayer/session/surface architecture but now uses an explicit HTTP data-source factory that permits cross-protocol redirects and sends an OwnPlay user agent.\n4. Primary and fallback credential-bearing URIs remain runtime-only and redacted from `ResolvedLibraryPlayback.toString()`.\n5. Offline playback is unchanged and receives no network fallback candidate.\n\n## Boundaries\n\n- No Room entity/schema/version change.\n- No credential storage change.\n- No Live reducer/session/surface ownership change.\n- No download state-machine or public-download hierarchy change.\n- No APK/AAB generation is authorized by this source stage.\n- Physical playback PASS cannot be claimed until a later explicitly authorized QA APK is tested against the same provider.\n\n## Validation contract\n\n- Exact Stage 21 parent must be `f33d06003e0aaac5cc18cab96a0c7e3cd9729a3d`.\n- Focused tests cover alternate URI selection, HLS/AUTO format classification, and redaction of both primary and fallback URIs.\n- Standard source validation must pass compileDebugKotlin, compileDebugUnitTestKotlin/testDebugUnitTest, lintDebug, Room schema cleanliness, and the explicit no-APK/AAB guard.\n''')

print("Stage 22 patch applied")
