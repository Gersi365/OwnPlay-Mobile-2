from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match in {path}, found {count}: {old[:100]!r}")
    file.write_text(text.replace(old, new, 1))


# Expose app-local ExoPlayer gain through the playback controller.
replace_once(
    "app/src/main/java/app/ownplay/mobile/playback/PlaybackController.kt",
    "    suspend fun setPlayWhenReady(shouldPlay: Boolean)\n\n    suspend fun seekTo(positionMs: Long)\n",
    "    suspend fun setPlayWhenReady(shouldPlay: Boolean)\n\n    suspend fun setVolume(volume: Float)\n\n    suspend fun seekTo(positionMs: Long)\n",
)

replace_once(
    "app/src/main/java/app/ownplay/mobile/playback/domain/PlaybackModels.kt",
    "    val playWhenReady: Boolean = false,\n    val isPlaying: Boolean = false,\n    val positionMs: Long = 0L,\n",
    "    val playWhenReady: Boolean = false,\n    val isPlaying: Boolean = false,\n    val volume: Float = 1f,\n    val positionMs: Long = 0L,\n",
)

replace_once(
    "app/src/main/java/app/ownplay/mobile/playback/Media3PlaybackController.kt",
    "import app.ownplay.mobile.playback.domain.PlaybackStreamFormat\n",
    "import app.ownplay.mobile.playback.domain.PlaybackStreamFormat\nimport app.ownplay.mobile.playback.domain.PlayerLocalControlPolicy\n",
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/playback/Media3PlaybackController.kt",
    "    override suspend fun setPlayWhenReady(shouldPlay: Boolean) {\n        mutateOnPlayerThread {\n            player.playWhenReady = shouldPlay\n            refreshSnapshot()\n        }\n    }\n\n    override suspend fun seekTo(positionMs: Long) {\n",
    "    override suspend fun setPlayWhenReady(shouldPlay: Boolean) {\n        mutateOnPlayerThread {\n            player.playWhenReady = shouldPlay\n            refreshSnapshot()\n        }\n    }\n\n    override suspend fun setVolume(volume: Float) {\n        mutateOnPlayerThread {\n            player.volume = PlayerLocalControlPolicy.clampVolume(volume)\n            refreshSnapshot()\n        }\n    }\n\n    override suspend fun seekTo(positionMs: Long) {\n",
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/playback/Media3PlaybackController.kt",
    "            playWhenReady = player.playWhenReady,\n            isPlaying = player.isPlaying,\n            positionMs = player.currentPosition.coerceAtLeast(0L),\n",
    "            playWhenReady = player.playWhenReady,\n            isPlaying = player.isPlaying,\n            volume = player.volume,\n            positionMs = player.currentPosition.coerceAtLeast(0L),\n",
)

policy_path = Path("app/src/main/java/app/ownplay/mobile/playback/domain/PlayerLocalControlPolicy.kt")
if policy_path.exists():
    raise SystemExit(f"Refusing to overwrite existing {policy_path}")
policy_path.write_text('''package app.ownplay.mobile.playback.domain\n\nobject PlayerLocalControlPolicy {\n    const val VOLUME_STEP: Float = 0.05f\n    const val MIN_BRIGHTNESS: Float = 0.05f\n    const val MAX_LEVEL: Float = 1f\n    const val GESTURE_SENSITIVITY: Float = 1.2f\n\n    fun canHandlePlayerControls(\n        target: VideoTarget,\n        mediaId: String?,\n    ): Boolean = mediaId != null && (\n        target == VideoTarget.PREVIEW || target == VideoTarget.FULLSCREEN\n    )\n\n    fun clampVolume(value: Float): Float = value.coerceIn(0f, MAX_LEVEL)\n\n    fun clampBrightness(value: Float): Float = value.coerceIn(MIN_BRIGHTNESS, MAX_LEVEL)\n\n    fun volumeAfterStep(current: Float, direction: Int): Float {\n        val signedStep = when {\n            direction > 0 -> VOLUME_STEP\n            direction < 0 -> -VOLUME_STEP\n            else -> 0f\n        }\n        return clampVolume(current + signedStep)\n    }\n\n    fun normalizedGestureDelta(\n        deltaY: Float,\n        surfaceHeight: Float,\n    ): Float {\n        if (surfaceHeight <= 0f) return 0f\n        return (-deltaY / surfaceHeight * GESTURE_SENSITIVITY).coerceIn(-1f, 1f)\n    }\n}\n''')

# Compose gesture modifier operates only on the video region. It modifies the app Window
# brightness and ExoPlayer gain; it never writes global brightness or AudioManager volume.
ui_path = Path("app/src/main/java/app/ownplay/mobile/playback/ui/PlayerLocalControls.kt")
if ui_path.exists():
    raise SystemExit(f"Refusing to overwrite existing {ui_path}")
ui_path.parent.mkdir(parents=True, exist_ok=True)
ui_path.write_text('''package app.ownplay.mobile.playback.ui\n\nimport android.app.Activity\nimport android.content.Context\nimport android.content.ContextWrapper\nimport android.provider.Settings\nimport androidx.compose.foundation.gestures.detectVerticalDragGestures\nimport androidx.compose.runtime.remember\nimport androidx.compose.ui.Modifier\nimport androidx.compose.ui.composed\nimport androidx.compose.ui.input.pointer.consume\nimport androidx.compose.ui.input.pointer.pointerInput\nimport androidx.compose.ui.platform.LocalContext\nimport app.ownplay.mobile.playback.PlaybackController\nimport app.ownplay.mobile.playback.domain.PlayerLocalControlPolicy\nimport kotlinx.coroutines.CoroutineScope\nimport kotlinx.coroutines.launch\n\nfun Modifier.playerLocalVerticalControls(\n    playbackController: PlaybackController,\n    controllerScope: CoroutineScope,\n): Modifier = composed {\n    val context = LocalContext.current\n    val activity = remember(context) { context.findActivity() }\n\n    this.pointerInput(playbackController, activity) {\n        var mode: PlayerGestureMode? = null\n        var localVolume = playbackController.state.value.volume\n\n        detectVerticalDragGestures(\n            onDragStart = { start ->\n                mode = if (start.x < size.width / 2f) {\n                    PlayerGestureMode.BRIGHTNESS\n                } else {\n                    PlayerGestureMode.VOLUME\n                }\n                localVolume = playbackController.state.value.volume\n            },\n            onDragEnd = { mode = null },\n            onDragCancel = { mode = null },\n            onVerticalDrag = { change, dragAmount ->\n                change.consume()\n                val delta = PlayerLocalControlPolicy.normalizedGestureDelta(\n                    deltaY = dragAmount,\n                    surfaceHeight = size.height.toFloat(),\n                )\n                when (mode) {\n                    PlayerGestureMode.BRIGHTNESS -> activity?.let { host ->\n                        host.setOwnPlayBrightness(host.currentOwnPlayBrightness() + delta)\n                    }\n                    PlayerGestureMode.VOLUME -> {\n                        localVolume = PlayerLocalControlPolicy.clampVolume(localVolume + delta)\n                        controllerScope.launch {\n                            playbackController.setVolume(localVolume)\n                        }\n                    }\n                    null -> Unit\n                }\n            },\n        )\n    }\n}\n\nprivate fun Context.findActivity(): Activity? {\n    var current: Context? = this\n    while (current is ContextWrapper) {\n        if (current is Activity) return current\n        current = current.baseContext\n    }\n    return current as? Activity\n}\n\nprivate fun Activity.currentOwnPlayBrightness(): Float {\n    val override = window.attributes.screenBrightness\n    if (override >= 0f) return PlayerLocalControlPolicy.clampBrightness(override)\n\n    val systemBrightness = runCatching {\n        Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)\n    }.getOrDefault(128)\n    return PlayerLocalControlPolicy.clampBrightness(systemBrightness / 255f)\n}\n\nprivate fun Activity.setOwnPlayBrightness(value: Float) {\n    val attributes = window.attributes\n    attributes.screenBrightness = PlayerLocalControlPolicy.clampBrightness(value)\n    window.attributes = attributes\n}\n\nprivate enum class PlayerGestureMode {\n    BRIGHTNESS,\n    VOLUME,\n}\n''')

# Activity handles only hardware +/- and resetting the app-window brightness override.
main = "app/src/main/java/app/ownplay/mobile/MainActivity.kt"
replace_once(
    main,
    "import android.os.Bundle\nimport android.util.Rational\n",
    "import android.os.Bundle\nimport android.util.Rational\nimport android.view.KeyEvent\nimport android.view.WindowManager\n",
)
replace_once(
    main,
    "import app.ownplay.mobile.playback.domain.PlaybackSnapshot\nimport app.ownplay.mobile.playback.domain.VideoTarget\n",
    "import app.ownplay.mobile.playback.domain.PlaybackSnapshot\nimport app.ownplay.mobile.playback.domain.PlayerLocalControlPolicy\nimport app.ownplay.mobile.playback.domain.VideoTarget\n",
)
replace_once(
    main,
    "    private var playbackSnapshot = PlaybackSnapshot()\n    private var resumePlaybackAfterBackground = false\n",
    "    private var playbackSnapshot = PlaybackSnapshot()\n    private var resumePlaybackAfterBackground = false\n    private var appPlaybackVolume = 1f\n",
)
replace_once(
    main,
    "                pictureInPictureEnabled = enabled\n                playbackSnapshot = playback\n                updatePictureInPictureParams()\n",
    "                pictureInPictureEnabled = enabled\n                playbackSnapshot = playback\n                appPlaybackVolume = playback.volume\n                if (playback.mediaId == null) {\n                    resetOwnPlayBrightness()\n                }\n                updatePictureInPictureParams()\n",
)
replace_once(
    main,
    "    override fun onStart() {\n",
    '''    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {\n        if (handlePlayerVolumeKey(keyCode)) {\n            return true\n        }\n        return super.onKeyDown(keyCode, event)\n    }\n\n    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {\n        if (isPlayerVolumeKey(keyCode) && isPlayerLocalControlActive()) {\n            return true\n        }\n        return super.onKeyUp(keyCode, event)\n    }\n\n    override fun onStart() {\n''',
)
replace_once(
    main,
    "    private fun setContentOrientation(fullscreen: Boolean) {\n",
    '''    private fun handlePlayerVolumeKey(keyCode: Int): Boolean {\n        if (!isPlayerVolumeKey(keyCode) || !isPlayerLocalControlActive()) {\n            return false\n        }\n        val direction = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) 1 else -1\n        appPlaybackVolume = PlayerLocalControlPolicy.volumeAfterStep(appPlaybackVolume, direction)\n        lifecycleScope.launch {\n            services.playbackController.setVolume(appPlaybackVolume)\n        }\n        return true\n    }\n\n    private fun isPlayerVolumeKey(keyCode: Int): Boolean =\n        keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN\n\n    private fun isPlayerLocalControlActive(): Boolean =\n        PlayerLocalControlPolicy.canHandlePlayerControls(\n            target = playbackSnapshot.activeTarget,\n            mediaId = playbackSnapshot.mediaId,\n        )\n\n    private fun resetOwnPlayBrightness() {\n        if (window.attributes.screenBrightness == WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {\n            return\n        }\n        val attributes = window.attributes\n        attributes.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE\n        window.attributes = attributes\n    }\n\n    private fun setContentOrientation(fullscreen: Boolean) {\n''',
)

# Wire the reusable gesture modifier into Live Preview and Live fullscreen.
live = "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt"
replace_once(
    live,
    "import app.ownplay.mobile.playback.domain.VideoTarget\n",
    "import app.ownplay.mobile.playback.domain.VideoTarget\nimport app.ownplay.mobile.playback.ui.playerLocalVerticalControls\n",
)
replace_once(
    live,
    "            .fillMaxWidth()\n            .aspectRatio(16f / 9f)\n            .clip(OwnPlayShapeTokens.Medium)\n",
    "            .fillMaxWidth()\n            .aspectRatio(16f / 9f)\n            .playerLocalVerticalControls(playbackController, controllerScope)\n            .clip(OwnPlayShapeTokens.Medium)\n",
)
replace_once(
    live,
    "            modifier = Modifier\n                .fillMaxSize()\n                .clickable(\n                    interactionSource = interactionSource,\n",
    "            modifier = Modifier\n                .fillMaxSize()\n                .playerLocalVerticalControls(playbackController, controllerScope)\n                .clickable(\n                    interactionSource = interactionSource,\n",
)

# VOD/episode/offline fullscreen uses the same app-local gesture contract.
library = "app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryShell.kt"
replace_once(
    library,
    "import app.ownplay.mobile.playback.domain.VideoTarget\n",
    "import app.ownplay.mobile.playback.domain.VideoTarget\nimport app.ownplay.mobile.playback.ui.playerLocalVerticalControls\n",
)
replace_once(
    library,
    "            modifier = Modifier\n                .fillMaxSize()\n                .clickable(interactionSource = interactionSource, indication = null) {\n",
    "            modifier = Modifier\n                .fillMaxSize()\n                .playerLocalVerticalControls(playbackController, scope)\n                .clickable(interactionSource = interactionSource, indication = null) {\n",
)

# Focused deterministic policy tests.
test_path = Path("app/src/test/java/app/ownplay/mobile/playback/PlayerLocalControlPolicyTest.kt")
if test_path.exists():
    raise SystemExit(f"Refusing to overwrite existing {test_path}")
test_path.parent.mkdir(parents=True, exist_ok=True)
test_path.write_text('''package app.ownplay.mobile.playback\n\nimport app.ownplay.mobile.playback.domain.PlayerLocalControlPolicy\nimport app.ownplay.mobile.playback.domain.VideoTarget\nimport org.junit.Assert.assertEquals\nimport org.junit.Assert.assertFalse\nimport org.junit.Assert.assertTrue\nimport org.junit.Test\n\nclass PlayerLocalControlPolicyTest {\n    @Test\n    fun `controls are active only for preview or fullscreen media`() {\n        assertTrue(PlayerLocalControlPolicy.canHandlePlayerControls(VideoTarget.PREVIEW, "channel"))\n        assertTrue(PlayerLocalControlPolicy.canHandlePlayerControls(VideoTarget.FULLSCREEN, "movie"))\n        assertFalse(PlayerLocalControlPolicy.canHandlePlayerControls(VideoTarget.PIP, "channel"))\n        assertFalse(PlayerLocalControlPolicy.canHandlePlayerControls(VideoTarget.NONE, "channel"))\n        assertFalse(PlayerLocalControlPolicy.canHandlePlayerControls(VideoTarget.PREVIEW, null))\n    }\n\n    @Test\n    fun `volume hardware steps clamp to player range`() {\n        assertEquals(1f, PlayerLocalControlPolicy.volumeAfterStep(0.99f, 1), 0.0001f)\n        assertEquals(0f, PlayerLocalControlPolicy.volumeAfterStep(0.01f, -1), 0.0001f)\n        assertEquals(0.55f, PlayerLocalControlPolicy.volumeAfterStep(0.5f, 1), 0.0001f)\n    }\n\n    @Test\n    fun `brightness never reaches an unusable black window`() {\n        assertEquals(0.05f, PlayerLocalControlPolicy.clampBrightness(-1f), 0.0001f)\n        assertEquals(1f, PlayerLocalControlPolicy.clampBrightness(2f), 0.0001f)\n    }\n\n    @Test\n    fun `upward swipe increases local level and downward swipe decreases it`() {\n        assertTrue(PlayerLocalControlPolicy.normalizedGestureDelta(-100f, 500f) > 0f)\n        assertTrue(PlayerLocalControlPolicy.normalizedGestureDelta(100f, 500f) < 0f)\n    }\n}\n''')
