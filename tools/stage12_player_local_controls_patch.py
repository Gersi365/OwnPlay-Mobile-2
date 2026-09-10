from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match in {path}, found {count}: {old[:80]!r}")
    file.write_text(text.replace(old, new, 1))


# PlaybackController: expose app-local player volume without touching system AudioManager.
replace_once(
    "app/src/main/java/app/ownplay/mobile/playback/PlaybackController.kt",
    "    suspend fun setPlayWhenReady(shouldPlay: Boolean)\n\n    suspend fun seekTo(positionMs: Long)\n",
    "    suspend fun setPlayWhenReady(shouldPlay: Boolean)\n\n    suspend fun setVolume(volume: Float)\n\n    suspend fun seekTo(positionMs: Long)\n",
)

# Media3 controller: change only ExoPlayer's local gain.
replace_once(
    "app/src/main/java/app/ownplay/mobile/playback/Media3PlaybackController.kt",
    "    override suspend fun setPlayWhenReady(shouldPlay: Boolean) {\n        mutateOnPlayerThread {\n            player.playWhenReady = shouldPlay\n            refreshSnapshot()\n        }\n    }\n\n    override suspend fun seekTo(positionMs: Long) {\n",
    "    override suspend fun setPlayWhenReady(shouldPlay: Boolean) {\n        mutateOnPlayerThread {\n            player.playWhenReady = shouldPlay\n            refreshSnapshot()\n        }\n    }\n\n    override suspend fun setVolume(volume: Float) {\n        mutateOnPlayerThread {\n            player.volume = PlayerLocalControlPolicy.clampVolume(volume)\n        }\n    }\n\n    override suspend fun seekTo(positionMs: Long) {\n",
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/playback/Media3PlaybackController.kt",
    "import app.ownplay.mobile.playback.domain.PlaybackStreamFormat\n",
    "import app.ownplay.mobile.playback.domain.PlaybackStreamFormat\nimport app.ownplay.mobile.playback.domain.PlayerLocalControlPolicy\n",
)

# Pure policy keeps gesture/step behavior deterministic and testable.
policy_path = Path("app/src/main/java/app/ownplay/mobile/playback/domain/PlayerLocalControlPolicy.kt")
if policy_path.exists():
    raise SystemExit(f"Refusing to overwrite existing {policy_path}")
policy_path.write_text('''package app.ownplay.mobile.playback.domain\n\nobject PlayerLocalControlPolicy {\n    const val VOLUME_STEP: Float = 0.05f\n    const val MIN_BRIGHTNESS: Float = 0.05f\n    const val MAX_LEVEL: Float = 1f\n    const val GESTURE_SENSITIVITY: Float = 1.2f\n\n    fun canHandlePlayerControls(\n        target: VideoTarget,\n        mediaId: String?,\n    ): Boolean = mediaId != null && (\n        target == VideoTarget.PREVIEW || target == VideoTarget.FULLSCREEN\n    )\n\n    fun clampVolume(value: Float): Float = value.coerceIn(0f, MAX_LEVEL)\n\n    fun clampBrightness(value: Float): Float = value.coerceIn(MIN_BRIGHTNESS, MAX_LEVEL)\n\n    fun volumeAfterStep(current: Float, direction: Int): Float {\n        val signedStep = when {\n            direction > 0 -> VOLUME_STEP\n            direction < 0 -> -VOLUME_STEP\n            else -> 0f\n        }\n        return clampVolume(current + signedStep)\n    }\n\n    fun normalizedGestureDelta(\n        deltaY: Float,\n        surfaceHeight: Float,\n    ): Float {\n        if (surfaceHeight <= 0f) return 0f\n        return (-deltaY / surfaceHeight * GESTURE_SENSITIVITY).coerceIn(-1f, 1f)\n    }\n}\n''')

# MainActivity owns device-key interception and the app-window brightness override.
main = "app/src/main/java/app/ownplay/mobile/MainActivity.kt"
replace_once(
    main,
    "import android.content.res.Configuration\nimport android.os.Build\nimport android.os.Bundle\nimport android.util.Rational\n",
    "import android.content.res.Configuration\nimport android.graphics.Rect\nimport android.os.Build\nimport android.os.Bundle\nimport android.provider.Settings\nimport android.util.Rational\nimport android.view.KeyEvent\nimport android.view.MotionEvent\nimport android.view.SurfaceView\nimport android.view.View\nimport android.view.ViewConfiguration\nimport android.view.ViewGroup\nimport android.view.WindowManager\n",
)
replace_once(
    main,
    "import app.ownplay.mobile.playback.domain.PlaybackSnapshot\nimport app.ownplay.mobile.playback.domain.VideoTarget\n",
    "import app.ownplay.mobile.playback.domain.PlaybackSnapshot\nimport app.ownplay.mobile.playback.domain.PlayerLocalControlPolicy\nimport app.ownplay.mobile.playback.domain.VideoTarget\n",
)
replace_once(
    main,
    "import kotlinx.coroutines.flow.combine\nimport kotlinx.coroutines.launch\n",
    "import kotlin.math.abs\nimport kotlinx.coroutines.flow.combine\nimport kotlinx.coroutines.launch\n",
)
replace_once(
    main,
    "    private var resumePlaybackAfterBackground = false\n",
    "    private var resumePlaybackAfterBackground = false\n    private var appPlaybackVolume = 1f\n    private var playerGestureTracking = false\n    private var playerGestureConsumed = false\n    private var playerGestureStartY = 0f\n    private var playerGestureLastY = 0f\n    private var playerGestureSurfaceHeight = 1f\n    private var playerGestureMode: PlayerGestureMode? = null\n    private val playerGestureSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop.toFloat() }\n",
)
replace_once(
    main,
    "                pictureInPictureEnabled = enabled\n                playbackSnapshot = playback\n                updatePictureInPictureParams()\n",
    "                pictureInPictureEnabled = enabled\n                playbackSnapshot = playback\n                if (playback.mediaId == null) {\n                    resetAppBrightness()\n                }\n                updatePictureInPictureParams()\n",
)
replace_once(
    main,
    "    override fun onStart() {\n",
    '''    override fun dispatchKeyEvent(event: KeyEvent): Boolean {\n        if (PlayerLocalControlPolicy.canHandlePlayerControls(\n                target = playbackSnapshot.activeTarget,\n                mediaId = playbackSnapshot.mediaId,\n            ) && (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)\n        ) {\n            if (event.action == KeyEvent.ACTION_DOWN) {\n                val direction = if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) 1 else -1\n                appPlaybackVolume = PlayerLocalControlPolicy.volumeAfterStep(appPlaybackVolume, direction)\n                lifecycleScope.launch {\n                    services.playbackController.setVolume(appPlaybackVolume)\n                }\n            }\n            return true\n        }\n        return super.dispatchKeyEvent(event)\n    }\n\n    override fun dispatchTouchEvent(event: MotionEvent): Boolean {\n        when (event.actionMasked) {\n            MotionEvent.ACTION_DOWN -> {\n                resetPlayerGestureTracking()\n                val bounds = activePlaybackSurfaceBounds()\n                if (bounds != null && bounds.contains(event.rawX.toInt(), event.rawY.toInt())) {\n                    playerGestureTracking = true\n                    playerGestureStartY = event.rawY\n                    playerGestureLastY = event.rawY\n                    playerGestureSurfaceHeight = bounds.height().toFloat().coerceAtLeast(1f)\n                    playerGestureMode = if (event.rawX < bounds.exactCenterX()) {\n                        PlayerGestureMode.BRIGHTNESS\n                    } else {\n                        PlayerGestureMode.VOLUME\n                    }\n                }\n                return super.dispatchTouchEvent(event)\n            }\n\n            MotionEvent.ACTION_MOVE -> {\n                if (!playerGestureTracking || event.pointerCount != 1) {\n                    return super.dispatchTouchEvent(event)\n                }\n\n                if (!playerGestureConsumed && abs(event.rawY - playerGestureStartY) >= playerGestureSlop) {\n                    playerGestureConsumed = true\n                    cancelChildTouchDispatch(event)\n                    applyPlayerGestureDelta(event.rawY - playerGestureStartY)\n                    playerGestureLastY = event.rawY\n                    return true\n                }\n\n                if (playerGestureConsumed) {\n                    applyPlayerGestureDelta(event.rawY - playerGestureLastY)\n                    playerGestureLastY = event.rawY\n                    return true\n                }\n                return super.dispatchTouchEvent(event)\n            }\n\n            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {\n                val consumed = playerGestureTracking && playerGestureConsumed\n                resetPlayerGestureTracking()\n                return if (consumed) true else super.dispatchTouchEvent(event)\n            }\n        }\n        return super.dispatchTouchEvent(event)\n    }\n\n    override fun onStart() {\n''',
)
replace_once(
    main,
    "    private fun setContentOrientation(fullscreen: Boolean) {\n",
    '''    private fun applyPlayerGestureDelta(deltaY: Float) {\n        val delta = PlayerLocalControlPolicy.normalizedGestureDelta(\n            deltaY = deltaY,\n            surfaceHeight = playerGestureSurfaceHeight,\n        )\n        when (playerGestureMode) {\n            PlayerGestureMode.BRIGHTNESS -> setAppBrightness(currentAppBrightness() + delta)\n            PlayerGestureMode.VOLUME -> {\n                appPlaybackVolume = PlayerLocalControlPolicy.clampVolume(appPlaybackVolume + delta)\n                lifecycleScope.launch {\n                    services.playbackController.setVolume(appPlaybackVolume)\n                }\n            }\n            null -> Unit\n        }\n    }\n\n    private fun currentAppBrightness(): Float {\n        val windowOverride = window.attributes.screenBrightness\n        if (windowOverride >= 0f) {\n            return PlayerLocalControlPolicy.clampBrightness(windowOverride)\n        }\n        val systemBrightness = runCatching {\n            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)\n        }.getOrDefault(128)\n        return PlayerLocalControlPolicy.clampBrightness(systemBrightness / 255f)\n    }\n\n    private fun setAppBrightness(value: Float) {\n        val attributes = window.attributes\n        attributes.screenBrightness = PlayerLocalControlPolicy.clampBrightness(value)\n        window.attributes = attributes\n    }\n\n    private fun resetAppBrightness() {\n        if (window.attributes.screenBrightness == WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {\n            return\n        }\n        val attributes = window.attributes\n        attributes.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE\n        window.attributes = attributes\n    }\n\n    private fun activePlaybackSurfaceBounds(): Rect? {\n        if (!PlayerLocalControlPolicy.canHandlePlayerControls(\n                target = playbackSnapshot.activeTarget,\n                mediaId = playbackSnapshot.mediaId,\n            )\n        ) {\n            return null\n        }\n        val surfaces = mutableListOf<SurfaceView>()\n        collectVisibleSurfaceViews(window.decorView, surfaces)\n        val surface = surfaces.maxByOrNull { it.width.toLong() * it.height.toLong() } ?: return null\n        val location = IntArray(2)\n        surface.getLocationOnScreen(location)\n        return Rect(\n            location[0],\n            location[1],\n            location[0] + surface.width,\n            location[1] + surface.height,\n        )\n    }\n\n    private fun collectVisibleSurfaceViews(view: View, destination: MutableList<SurfaceView>) {\n        when (view) {\n            is SurfaceView -> if (view.isShown && view.width > 0 && view.height > 0) {\n                destination += view\n            }\n            is ViewGroup -> repeat(view.childCount) { index ->\n                collectVisibleSurfaceViews(view.getChildAt(index), destination)\n            }\n        }\n    }\n\n    private fun cancelChildTouchDispatch(event: MotionEvent) {\n        val cancel = MotionEvent.obtain(event)\n        cancel.action = MotionEvent.ACTION_CANCEL\n        super.dispatchTouchEvent(cancel)\n        cancel.recycle()\n    }\n\n    private fun resetPlayerGestureTracking() {\n        playerGestureTracking = false\n        playerGestureConsumed = false\n        playerGestureStartY = 0f\n        playerGestureLastY = 0f\n        playerGestureSurfaceHeight = 1f\n        playerGestureMode = null\n    }\n\n    private fun setContentOrientation(fullscreen: Boolean) {\n''',
)
replace_once(
    main,
    "    private fun supportsPictureInPicture(): Boolean =\n        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)\n}\n",
    "    private fun supportsPictureInPicture(): Boolean =\n        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)\n\n    private enum class PlayerGestureMode {\n        BRIGHTNESS,\n        VOLUME,\n    }\n}\n",
)

# Focused unit tests for local-only level handling.
test_path = Path("app/src/test/java/app/ownplay/mobile/playback/PlayerLocalControlPolicyTest.kt")
if test_path.exists():
    raise SystemExit(f"Refusing to overwrite existing {test_path}")
test_path.parent.mkdir(parents=True, exist_ok=True)
test_path.write_text('''package app.ownplay.mobile.playback\n\nimport app.ownplay.mobile.playback.domain.PlayerLocalControlPolicy\nimport app.ownplay.mobile.playback.domain.VideoTarget\nimport org.junit.Assert.assertEquals\nimport org.junit.Assert.assertFalse\nimport org.junit.Assert.assertTrue\nimport org.junit.Test\n\nclass PlayerLocalControlPolicyTest {\n    @Test\n    fun `controls are active only for preview or fullscreen media`() {\n        assertTrue(PlayerLocalControlPolicy.canHandlePlayerControls(VideoTarget.PREVIEW, "channel"))\n        assertTrue(PlayerLocalControlPolicy.canHandlePlayerControls(VideoTarget.FULLSCREEN, "movie"))\n        assertFalse(PlayerLocalControlPolicy.canHandlePlayerControls(VideoTarget.PIP, "channel"))\n        assertFalse(PlayerLocalControlPolicy.canHandlePlayerControls(VideoTarget.NONE, "channel"))\n        assertFalse(PlayerLocalControlPolicy.canHandlePlayerControls(VideoTarget.PREVIEW, null))\n    }\n\n    @Test\n    fun `volume hardware steps clamp to player range`() {\n        assertEquals(1f, PlayerLocalControlPolicy.volumeAfterStep(0.99f, 1), 0.0001f)\n        assertEquals(0f, PlayerLocalControlPolicy.volumeAfterStep(0.01f, -1), 0.0001f)\n        assertEquals(0.55f, PlayerLocalControlPolicy.volumeAfterStep(0.5f, 1), 0.0001f)\n    }\n\n    @Test\n    fun `brightness never reaches an unusable black window`() {\n        assertEquals(0.05f, PlayerLocalControlPolicy.clampBrightness(-1f), 0.0001f)\n        assertEquals(1f, PlayerLocalControlPolicy.clampBrightness(2f), 0.0001f)\n    }\n\n    @Test\n    fun `upward swipe increases local level and downward swipe decreases it`() {\n        assertTrue(PlayerLocalControlPolicy.normalizedGestureDelta(-100f, 500f) > 0f)\n        assertTrue(PlayerLocalControlPolicy.normalizedGestureDelta(100f, 500f) < 0f)\n    }\n}\n''')
