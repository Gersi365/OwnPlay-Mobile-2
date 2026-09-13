from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, found {count}: {old[:120]!r}")
    file.write_text(text.replace(old, new, 1))


# MainActivity: centralize fullscreen orientation behavior for Live + Library + offline.
main = "app/src/main/java/app/ownplay/mobile/MainActivity.kt"
replace_once(
    main,
    "import android.content.res.Configuration\nimport android.os.Build\nimport android.os.Bundle\nimport android.util.Rational\nimport android.view.KeyEvent\nimport android.view.WindowManager\n",
    "import android.content.res.Configuration\nimport android.database.ContentObserver\nimport android.os.Build\nimport android.os.Bundle\nimport android.os.Handler\nimport android.os.Looper\nimport android.os.SystemClock\nimport android.provider.Settings\nimport android.util.Rational\nimport android.view.KeyEvent\nimport android.view.OrientationEventListener\nimport android.view.WindowManager\n",
)
replace_once(
    main,
    "import androidx.activity.enableEdgeToEdge\n",
    "import androidx.activity.enableEdgeToEdge\nimport androidx.compose.runtime.mutableStateOf\n",
)
replace_once(
    main,
    "import app.ownplay.mobile.playback.domain.PictureInPicturePolicy\n",
    "import app.ownplay.mobile.playback.domain.FullscreenOrientationLatch\nimport app.ownplay.mobile.playback.domain.PictureInPicturePolicy\n",
)
replace_once(
    main,
    "    private var resumePlaybackAfterBackground = false\n    private var appPlaybackVolume = 1f\n",
    "    private var resumePlaybackAfterBackground = false\n    private var appPlaybackVolume = 1f\n    private val systemAutoRotateEnabled = mutableStateOf(false)\n    private val fullscreenOrientationLatch = FullscreenOrientationLatch()\n    private var autoRotateObserver: ContentObserver? = null\n    private var fullscreenOrientationListener: OrientationEventListener? = null\n",
)
replace_once(
    main,
    "        ownPlayApplication = application as OwnPlayApplication\n        services = ownPlayApplication.services\n\n        lifecycleScope.launch {\n",
    "        ownPlayApplication = application as OwnPlayApplication\n        services = ownPlayApplication.services\n        installSystemAutoRotateObserver()\n        installFullscreenOrientationListener()\n\n        lifecycleScope.launch {\n",
)
replace_once(
    main,
    "        val currentTarget = services.playbackController.state.value.activeTarget\n        when {\n            isInPictureInPictureMode && currentTarget == VideoTarget.FULLSCREEN -> lifecycleScope.launch {\n                services.playbackController.transferVideoTarget(VideoTarget.PIP)\n            }\n\n            !isInPictureInPictureMode &&\n                contentFullscreen &&\n                currentTarget == VideoTarget.PIP -> lifecycleScope.launch {\n                services.playbackController.transferVideoTarget(VideoTarget.FULLSCREEN)\n            }\n        }\n    }\n\n    override fun onConfigurationChanged(newConfig: Configuration) {\n        super.onConfigurationChanged(newConfig)\n        if (\n            contentFullscreen &&\n            newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE &&\n            requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR\n        ) {\n            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR\n        }\n    }\n\n    override fun onStop() {\n",
    "        val currentTarget = services.playbackController.state.value.activeTarget\n        when {\n            isInPictureInPictureMode && currentTarget == VideoTarget.FULLSCREEN -> lifecycleScope.launch {\n                services.playbackController.transferVideoTarget(VideoTarget.PIP)\n            }\n\n            !isInPictureInPictureMode &&\n                contentFullscreen &&\n                currentTarget == VideoTarget.PIP -> lifecycleScope.launch {\n                services.playbackController.transferVideoTarget(VideoTarget.FULLSCREEN)\n            }\n        }\n        updateFullscreenOrientationListener()\n    }\n\n    override fun onDestroy() {\n        fullscreenOrientationListener?.disable()\n        fullscreenOrientationListener = null\n        autoRotateObserver?.let(contentResolver::unregisterContentObserver)\n        autoRotateObserver = null\n        super.onDestroy()\n    }\n\n    override fun onStop() {\n",
)
replace_once(
    main,
    "    private fun handleContentFullscreenChanged(fullscreen: Boolean) {\n        contentFullscreen = fullscreen\n        updatePictureInPictureParams()\n        window.decorView.postOnAnimation {\n            if (contentFullscreen != fullscreen || isFinishing || isDestroyed) {\n                return@postOnAnimation\n            }\n            setContentOrientation(fullscreen)\n            setImmersiveFullscreen(fullscreen)\n        }\n    }\n\n    private fun setContentOrientation(fullscreen: Boolean) {\n        requestedOrientation = if (fullscreen) {\n            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE\n        } else {\n            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT\n        }\n    }\n",
    "    private fun installSystemAutoRotateObserver() {\n        refreshSystemAutoRotateEnabled()\n        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {\n            override fun onChange(selfChange: Boolean) {\n                refreshSystemAutoRotateEnabled()\n            }\n        }\n        autoRotateObserver = observer\n        contentResolver.registerContentObserver(\n            Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),\n            false,\n            observer,\n        )\n    }\n\n    private fun refreshSystemAutoRotateEnabled() {\n        val enabled = Settings.System.getInt(\n            contentResolver,\n            Settings.System.ACCELEROMETER_ROTATION,\n            0,\n        ) == 1\n        if (systemAutoRotateEnabled.value == enabled) return\n\n        systemAutoRotateEnabled.value = enabled\n        fullscreenOrientationLatch.reset()\n        if (contentFullscreen) {\n            setContentOrientation(fullscreen = true)\n        }\n        updateFullscreenOrientationListener()\n    }\n\n    private fun installFullscreenOrientationListener() {\n        fullscreenOrientationListener = object : OrientationEventListener(this) {\n            override fun onOrientationChanged(orientation: Int) {\n                if (\n                    orientation == ORIENTATION_UNKNOWN ||\n                    !contentFullscreen ||\n                    !systemAutoRotateEnabled.value ||\n                    isInPictureInPictureMode\n                ) {\n                    return\n                }\n                if (\n                    fullscreenOrientationLatch.onOrientation(\n                        orientationDegrees = orientation,\n                        elapsedRealtimeMillis = SystemClock.elapsedRealtime(),\n                    )\n                ) {\n                    fullscreenOrientationLatch.reset()\n                    onBackPressedDispatcher.onBackPressed()\n                }\n            }\n        }\n        updateFullscreenOrientationListener()\n    }\n\n    private fun updateFullscreenOrientationListener() {\n        val listener = fullscreenOrientationListener ?: return\n        val shouldListen =\n            contentFullscreen && systemAutoRotateEnabled.value && !isInPictureInPictureMode\n        if (shouldListen && listener.canDetectOrientation()) {\n            listener.enable()\n        } else {\n            listener.disable()\n        }\n    }\n\n    private fun handleContentFullscreenChanged(fullscreen: Boolean) {\n        contentFullscreen = fullscreen\n        fullscreenOrientationLatch.reset()\n        setContentOrientation(fullscreen)\n        updateFullscreenOrientationListener()\n        updatePictureInPictureParams()\n        window.decorView.postOnAnimation {\n            if (contentFullscreen != fullscreen || isFinishing || isDestroyed) {\n                return@postOnAnimation\n            }\n            setImmersiveFullscreen(fullscreen)\n        }\n    }\n\n    private fun setContentOrientation(fullscreen: Boolean) {\n        requestedOrientation = when {\n            !fullscreen -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT\n            systemAutoRotateEnabled.value -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE\n            else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE\n        }\n    }\n",
)

# Live: remove the old Live-only sensor state machine. MainActivity now owns the shared contract.
live = "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt"
replace_once(live, "import android.view.OrientationEventListener\n", "")
replace_once(
    live,
    "    var waitingForInitialChannels by remember(catalog?.activeSourceId) { mutableStateOf(false) }\n    var orientationFullscreenArmed by remember { mutableStateOf(true) }\n    var lastStableOrientationDegrees by remember { mutableStateOf<Int?>(null) }\n    var searchVisible by remember(catalog?.activeSourceId) { mutableStateOf(false) }\n",
    "    var waitingForInitialChannels by remember(catalog?.activeSourceId) { mutableStateOf(false) }\n    var searchVisible by remember(catalog?.activeSourceId) { mutableStateOf(false) }\n",
)
old_orientation_block = '''    val orientationContext = LocalContext.current\n    DisposableEffect(\n        orientationContext,\n        presentationState.presentation,\n        presentationState.selectedChannelId,\n    ) {\n        val selectedId = presentationState.selectedChannelId\n        if (presentationState.presentation == LivePresentation.BROWSE || selectedId == null) {\n            onDispose { }\n        } else {\n            var landscapeTriggered = false\n            var portraitExitTriggered = false\n            val listener = object : OrientationEventListener(orientationContext) {\n                override fun onOrientationChanged(orientation: Int) {\n                    if (orientation == ORIENTATION_UNKNOWN) return\n                    val previousStableOrientation = lastStableOrientationDegrees\n                    when {\n                        LiveOrientationPolicy.isPortrait(orientation) -> {\n                            lastStableOrientationDegrees = orientation\n                            landscapeTriggered = false\n                            orientationFullscreenArmed = true\n                            if (\n                                presentationState.presentation == LivePresentation.FULLSCREEN &&\n                                LiveOrientationPolicy.shouldAutoExitFullscreen(\n                                    previousOrientationDegrees = previousStableOrientation,\n                                    orientationDegrees = orientation,\n                                ) &&\n                                !portraitExitTriggered\n                            ) {\n                                portraitExitTriggered = true\n                                dispatch(LiveIntent.BackPressed)\n                            }\n                        }\n                        LiveOrientationPolicy.isLandscape(orientation) -> {\n                            lastStableOrientationDegrees = orientation\n                            if (\n                                presentationState.presentation == LivePresentation.PREVIEW &&\n                                LiveOrientationPolicy.shouldAutoEnterFullscreen(\n                                    orientationDegrees = orientation,\n                                    armed = orientationFullscreenArmed,\n                                ) &&\n                                !landscapeTriggered\n                            ) {\n                                landscapeTriggered = true\n                                orientationFullscreenArmed = false\n                                dispatch(LiveIntent.ChannelTapped(selectedId))\n                            }\n                        }\n                    }\n                }\n            }\n            if (listener.canDetectOrientation()) listener.enable()\n            onDispose { listener.disable() }\n        }\n    }\n\n'''
replace_once(live, old_orientation_block, "")
replace_once(
    live,
    "    BackHandler(enabled = presentationState.presentation != LivePresentation.BROWSE) {\n        if (presentationState.presentation == LivePresentation.FULLSCREEN) {\n            orientationFullscreenArmed = false\n        }\n        dispatch(LiveIntent.BackPressed)\n    }\n",
    "    BackHandler(enabled = presentationState.presentation != LivePresentation.BROWSE) {\n        dispatch(LiveIntent.BackPressed)\n    }\n",
)
replace_once(
    live,
    "            onBackToPreview = {\n                orientationFullscreenArmed = false\n                dispatch(LiveIntent.BackPressed)\n            },\n",
    "            onBackToPreview = { dispatch(LiveIntent.BackPressed) },\n",
)

# Keep the active provider category chip visible after gesture navigation.
replace_once(
    live,
    ") {\n    LazyRow(\n        contentPadding = PaddingValues(horizontal = OwnPlaySpacing.Lg),\n        horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs),\n    ) {\n",
    ") {\n    val listState = rememberLazyListState()\n    val selectedItemIndex = when {\n        favoritesOnly && favoriteCount > 0 -> 0\n        selectedCustomGroupId != null -> {\n            val groupIndex = customGroups.indexOfFirst { it.groupId == selectedCustomGroupId }\n            if (groupIndex >= 0) (if (favoriteCount > 0) 1 else 0) + groupIndex else -1\n        }\n        selectedCategoryKey != null -> {\n            val categoryIndex = categories.indexOfFirst { it.categoryKey == selectedCategoryKey }\n            if (categoryIndex >= 0) {\n                (if (favoriteCount > 0) 1 else 0) + customGroups.size + categoryIndex\n            } else {\n                -1\n            }\n        }\n        else -> -1\n    }\n\n    LaunchedEffect(selectedItemIndex) {\n        if (selectedItemIndex < 0) return@LaunchedEffect\n        repeat(4) {\n            if (listState.layoutInfo.totalItemsCount > selectedItemIndex) {\n                listState.animateScrollToItem(selectedItemIndex)\n                return@LaunchedEffect\n            }\n            delay(16)\n        }\n    }\n\n    LazyRow(\n        state = listState,\n        contentPadding = PaddingValues(horizontal = OwnPlaySpacing.Lg),\n        horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs),\n    ) {\n",
)

# Remove channel numbering from the Live browse list only.
replace_once(live, "            else -> channels.forEachIndexed { index, channel ->\n", "            else -> channels.forEach { channel ->\n")
replace_once(
    live,
    "                        ChannelRow(\n                            number = (index + 1).toString().padStart(3, '0'),\n                            channel = channel,\n",
    "                        ChannelRow(\n                            channel = channel,\n",
)
replace_once(
    live,
    "private fun ChannelRow(\n    number: String,\n    channel: LiveChannel,\n",
    "private fun ChannelRow(\n    channel: LiveChannel,\n",
)
replace_once(
    live,
    "            Text(\n                text = number,\n                style = MaterialTheme.typography.labelMedium,\n                color = OwnPlayColors.TextMuted,\n                modifier = Modifier.width(36.dp),\n            )\n",
    "",
)

# Retire the Live-only orientation policy: fullscreen behavior is now shared across all playback.
Path("app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveOrientationPolicy.kt").unlink()
Path("app/src/test/java/app/ownplay/mobile/feature/live/ui/LiveOrientationPolicyTest.kt").unlink()

# Shared orientation policy with wide bands, dead zones, and dwell-time hysteresis.
policy = '''package app.ownplay.mobile.playback.domain\n\nenum class PhysicalOrientationBand {\n    PORTRAIT,\n    LANDSCAPE,\n    TRANSITION,\n}\n\nobject FullscreenOrientationPolicy {\n    const val STABILITY_MILLIS: Long = 500L\n\n    fun classify(orientationDegrees: Int): PhysicalOrientationBand = when {\n        orientationDegrees in 60..120 || orientationDegrees in 240..300 ->\n            PhysicalOrientationBand.LANDSCAPE\n        orientationDegrees in 0..30 ||\n            orientationDegrees in 150..210 ||\n            orientationDegrees in 330..359 -> PhysicalOrientationBand.PORTRAIT\n        else -> PhysicalOrientationBand.TRANSITION\n    }\n}\n\nclass FullscreenOrientationLatch(\n    private val stabilityMillis: Long = FullscreenOrientationPolicy.STABILITY_MILLIS,\n) {\n    private var candidate = PhysicalOrientationBand.TRANSITION\n    private var candidateSinceMillis: Long? = null\n    private var landscapeConfirmed = false\n    private var exitEmitted = false\n\n    init {\n        require(stabilityMillis >= 0L) { \"Stability duration must not be negative.\" }\n    }\n\n    fun reset() {\n        candidate = PhysicalOrientationBand.TRANSITION\n        candidateSinceMillis = null\n        landscapeConfirmed = false\n        exitEmitted = false\n    }\n\n    fun onOrientation(\n        orientationDegrees: Int,\n        elapsedRealtimeMillis: Long,\n    ): Boolean {\n        if (exitEmitted) return false\n\n        val observed = FullscreenOrientationPolicy.classify(orientationDegrees)\n        if (observed == PhysicalOrientationBand.TRANSITION) {\n            candidate = PhysicalOrientationBand.TRANSITION\n            candidateSinceMillis = null\n            return false\n        }\n\n        if (observed != candidate) {\n            candidate = observed\n            candidateSinceMillis = elapsedRealtimeMillis\n            return false\n        }\n\n        val candidateSince = candidateSinceMillis ?: elapsedRealtimeMillis.also {\n            candidateSinceMillis = it\n        }\n        if (elapsedRealtimeMillis - candidateSince < stabilityMillis) return false\n\n        if (!landscapeConfirmed) {\n            if (observed == PhysicalOrientationBand.LANDSCAPE) {\n                landscapeConfirmed = true\n            }\n            return false\n        }\n\n        if (observed == PhysicalOrientationBand.PORTRAIT) {\n            exitEmitted = true\n            return true\n        }\n        return false\n    }\n}\n'''
Path("app/src/main/java/app/ownplay/mobile/playback/domain/FullscreenOrientationPolicy.kt").write_text(policy)

test = '''package app.ownplay.mobile.playback.domain\n\nimport org.junit.Assert.assertEquals\nimport org.junit.Assert.assertFalse\nimport org.junit.Assert.assertTrue\nimport org.junit.Test\n\nclass FullscreenOrientationPolicyTest {\n    @Test\n    fun `orientation bands include dead zones to avoid hand jitter`() {\n        assertEquals(PhysicalOrientationBand.PORTRAIT, FullscreenOrientationPolicy.classify(0))\n        assertEquals(PhysicalOrientationBand.PORTRAIT, FullscreenOrientationPolicy.classify(180))\n        assertEquals(PhysicalOrientationBand.LANDSCAPE, FullscreenOrientationPolicy.classify(90))\n        assertEquals(PhysicalOrientationBand.LANDSCAPE, FullscreenOrientationPolicy.classify(270))\n        assertEquals(PhysicalOrientationBand.TRANSITION, FullscreenOrientationPolicy.classify(45))\n        assertEquals(PhysicalOrientationBand.TRANSITION, FullscreenOrientationPolicy.classify(135))\n    }\n\n    @Test\n    fun `portrait cannot exit fullscreen before stable physical landscape was confirmed`() {\n        val latch = FullscreenOrientationLatch(stabilityMillis = 500L)\n\n        assertFalse(latch.onOrientation(0, 0L))\n        assertFalse(latch.onOrientation(5, 750L))\n        assertFalse(latch.onOrientation(10, 1_500L))\n    }\n\n    @Test\n    fun `fullscreen exit requires stable landscape then stable portrait`() {\n        val latch = FullscreenOrientationLatch(stabilityMillis = 500L)\n\n        assertFalse(latch.onOrientation(90, 0L))\n        assertFalse(latch.onOrientation(92, 499L))\n        assertFalse(latch.onOrientation(92, 500L))\n\n        assertFalse(latch.onOrientation(10, 600L))\n        assertFalse(latch.onOrientation(8, 1_099L))\n        assertTrue(latch.onOrientation(8, 1_100L))\n        assertFalse(latch.onOrientation(0, 1_700L))\n    }\n\n    @Test\n    fun `transition band resets dwell timing`() {\n        val latch = FullscreenOrientationLatch(stabilityMillis = 500L)\n\n        assertFalse(latch.onOrientation(90, 0L))\n        assertFalse(latch.onOrientation(45, 400L))\n        assertFalse(latch.onOrientation(90, 450L))\n        assertFalse(latch.onOrientation(90, 949L))\n        assertFalse(latch.onOrientation(90, 950L))\n\n        assertFalse(latch.onOrientation(0, 1_000L))\n        assertFalse(latch.onOrientation(0, 1_499L))\n        assertTrue(latch.onOrientation(0, 1_500L))\n    }\n}\n'''
Path("app/src/test/java/app/ownplay/mobile/playback/domain/FullscreenOrientationPolicyTest.kt").write_text(test)

print("Stage 32 patch applied")
