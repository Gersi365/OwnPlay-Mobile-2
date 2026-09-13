package app.ownplay.mobile

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Rational
import android.view.KeyEvent
import android.view.OrientationEventListener
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import app.ownplay.mobile.app.ContentFullscreenKind
import app.ownplay.mobile.app.OwnPlayApp
import app.ownplay.mobile.core.OwnPlayServices
import app.ownplay.mobile.playback.domain.FullscreenOrientationLatch
import app.ownplay.mobile.playback.domain.PhysicalOrientationBand
import app.ownplay.mobile.playback.domain.PictureInPicturePolicy
import app.ownplay.mobile.playback.domain.PlaybackKind
import app.ownplay.mobile.playback.domain.PlaybackSnapshot
import app.ownplay.mobile.playback.domain.PlayerLocalControlPolicy
import app.ownplay.mobile.playback.domain.StableOrientationLatch
import app.ownplay.mobile.playback.domain.VideoTarget
import app.ownplay.mobile.playback.ui.PlayerLocalControlHud
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var ownPlayApplication: OwnPlayApplication
    private lateinit var services: OwnPlayServices
    private var contentFullscreenKind = ContentFullscreenKind.NONE
    private var pictureInPictureEnabled = true
    private var playbackSnapshot = PlaybackSnapshot()
    private var resumePlaybackAfterBackground = false
    private var appPlaybackVolume = 1f
    private val systemAutoRotateEnabled = mutableStateOf(false)
    private val liveAutoFullscreenRequestToken = mutableIntStateOf(0)
    private val liveAutoPreviewRequestToken = mutableIntStateOf(0)
    private val livePreviewLandscapeLatch = StableOrientationLatch(
        targetBand = PhysicalOrientationBand.LANDSCAPE,
    )
    private val liveFullscreenOrientationLatch = FullscreenOrientationLatch()
    private var autoRotateObserver: ContentObserver? = null
    private var fullscreenOrientationListener: OrientationEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ownPlayApplication = application as OwnPlayApplication
        services = ownPlayApplication.services
        installSystemAutoRotateObserver()
        installFullscreenOrientationListener()

        lifecycleScope.launch {
            combine(
                services.settingsPreferences.settings,
                services.playbackController.state,
            ) { settings, playback ->
                settings.pictureInPictureEnabled to playback
            }.collect { (enabled, playback) ->
                val wasLivePreview = isLivePreview(playbackSnapshot)
                pictureInPictureEnabled = enabled
                playbackSnapshot = playback
                appPlaybackVolume = playback.volume
                if (playback.mediaId == null) {
                    resetOwnPlayBrightness()
                }
                if (wasLivePreview != isLivePreview(playback)) {
                    livePreviewLandscapeLatch.reset()
                    updateFullscreenOrientationListener()
                }
                updatePictureInPictureParams()
            }
        }

        setContent {
            OwnPlayApp(
                services = services,
                liveAutoFullscreenRequestToken = liveAutoFullscreenRequestToken.intValue,
                liveAutoPreviewRequestToken = liveAutoPreviewRequestToken.intValue,
                onExitConfirmed = { finish() },
                onFullscreenChanged = ::handleContentFullscreenChanged,
            )
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (handlePlayerVolumeKey(keyCode)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (isPlayerVolumeKey(keyCode) && isPlayerLocalControlActive()) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onStart() {
        super.onStart()
        if (resumePlaybackAfterBackground && !isInPictureInPictureMode) {
            resumePlaybackAfterBackground = false
            lifecycleScope.launch {
                services.playbackController.setPlayWhenReady(true)
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            supportsPictureInPicture() &&
            canEnterPictureInPicture()
        ) {
            runCatching {
                enterPictureInPictureMode(buildPictureInPictureParams())
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

        if (isInPictureInPictureMode && resumePlaybackAfterBackground) {
            resumePlaybackAfterBackground = false
            lifecycleScope.launch {
                services.playbackController.setPlayWhenReady(true)
            }
        }

        val currentTarget = services.playbackController.state.value.activeTarget
        when {
            isInPictureInPictureMode && currentTarget == VideoTarget.FULLSCREEN -> lifecycleScope.launch {
                services.playbackController.transferVideoTarget(VideoTarget.PIP)
            }

            !isInPictureInPictureMode &&
                contentFullscreenKind.isFullscreen &&
                currentTarget == VideoTarget.PIP -> lifecycleScope.launch {
                services.playbackController.transferVideoTarget(VideoTarget.FULLSCREEN)
            }
        }
        resetOrientationLatches()
        updateFullscreenOrientationListener()
    }

    override fun onDestroy() {
        fullscreenOrientationListener?.disable()
        fullscreenOrientationListener = null
        autoRotateObserver?.let(contentResolver::unregisterContentObserver)
        autoRotateObserver = null
        super.onDestroy()
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            when {
                isFinishing -> {
                    resumePlaybackAfterBackground = false
                    ownPlayApplication.stopPlaybackForActivityFinish()
                }

                !isInPictureInPictureMode -> {
                    if (!resumePlaybackAfterBackground) {
                        resumePlaybackAfterBackground = playbackSnapshot.playWhenReady
                    }
                    lifecycleScope.launch {
                        services.playbackController.setPlayWhenReady(false)
                    }
                }
            }
        }
        super.onStop()
    }

    private fun handlePlayerVolumeKey(keyCode: Int): Boolean {
        if (!isPlayerVolumeKey(keyCode) || !isPlayerLocalControlActive()) {
            return false
        }
        val direction = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) 1 else -1
        appPlaybackVolume = PlayerLocalControlPolicy.volumeAfterStep(appPlaybackVolume, direction)
        PlayerLocalControlHud.showVolume(appPlaybackVolume)
        lifecycleScope.launch {
            services.playbackController.setVolume(appPlaybackVolume)
        }
        return true
    }

    private fun isPlayerVolumeKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

    private fun isPlayerLocalControlActive(): Boolean =
        PlayerLocalControlPolicy.canHandlePlayerControls(
            target = playbackSnapshot.activeTarget,
            mediaId = playbackSnapshot.mediaId,
        )

    private fun resetOwnPlayBrightness() {
        if (window.attributes.screenBrightness == WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
            return
        }
        val attributes = window.attributes
        attributes.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = attributes
    }

    private fun installSystemAutoRotateObserver() {
        refreshSystemAutoRotateEnabled()
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                refreshSystemAutoRotateEnabled()
            }
        }
        autoRotateObserver = observer
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
            false,
            observer,
        )
    }

    private fun refreshSystemAutoRotateEnabled() {
        val enabled = Settings.System.getInt(
            contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            0,
        ) == 1
        if (systemAutoRotateEnabled.value == enabled) return

        systemAutoRotateEnabled.value = enabled
        resetOrientationLatches()
        setContentOrientation(contentFullscreenKind)
        updateFullscreenOrientationListener()
    }

    private fun installFullscreenOrientationListener() {
        fullscreenOrientationListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                if (
                    orientation == ORIENTATION_UNKNOWN ||
                    !systemAutoRotateEnabled.value ||
                    isInPictureInPictureMode
                ) {
                    return
                }

                val now = SystemClock.elapsedRealtime()
                when {
                    contentFullscreenKind == ContentFullscreenKind.LIVE -> {
                        if (
                            liveFullscreenOrientationLatch.onOrientation(
                                orientationDegrees = orientation,
                                elapsedRealtimeMillis = now,
                            )
                        ) {
                            liveFullscreenOrientationLatch.reset()
                            liveAutoPreviewRequestToken.intValue += 1
                        }
                    }

                    isLivePreview() -> {
                        if (
                            livePreviewLandscapeLatch.onOrientation(
                                orientationDegrees = orientation,
                                elapsedRealtimeMillis = now,
                            )
                        ) {
                            livePreviewLandscapeLatch.reset()
                            liveAutoFullscreenRequestToken.intValue += 1
                        }
                    }
                }
            }
        }
        updateFullscreenOrientationListener()
    }

    private fun updateFullscreenOrientationListener() {
        val listener = fullscreenOrientationListener ?: return
        val shouldListen =
            systemAutoRotateEnabled.value &&
                !isInPictureInPictureMode &&
                (
                    contentFullscreenKind == ContentFullscreenKind.LIVE ||
                        isLivePreview()
                    )
        if (shouldListen && listener.canDetectOrientation()) {
            listener.enable()
        } else {
            listener.disable()
        }
    }

    private fun handleContentFullscreenChanged(kind: ContentFullscreenKind) {
        contentFullscreenKind = kind
        resetOrientationLatches()
        setContentOrientation(kind)
        updateFullscreenOrientationListener()
        updatePictureInPictureParams()
        window.decorView.postOnAnimation {
            if (contentFullscreenKind != kind || isFinishing || isDestroyed) {
                return@postOnAnimation
            }
            setImmersiveFullscreen(kind.isFullscreen)
        }
    }

    private fun setContentOrientation(kind: ContentFullscreenKind) {
        requestedOrientation = when (kind) {
            ContentFullscreenKind.NONE -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ContentFullscreenKind.LIVE -> if (systemAutoRotateEnabled.value) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            ContentFullscreenKind.LIBRARY -> if (systemAutoRotateEnabled.value) {
                ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            } else {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }
    }

    private fun resetOrientationLatches() {
        livePreviewLandscapeLatch.reset()
        liveFullscreenOrientationLatch.reset()
    }

    private fun isLivePreview(snapshot: PlaybackSnapshot = playbackSnapshot): Boolean =
        contentFullscreenKind == ContentFullscreenKind.NONE &&
            snapshot.kind == PlaybackKind.LIVE &&
            snapshot.mediaId != null &&
            snapshot.activeTarget == VideoTarget.PREVIEW

    private fun setImmersiveFullscreen(fullscreen: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (fullscreen) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun canEnterPictureInPicture(): Boolean = PictureInPicturePolicy.canEnter(
        enabled = pictureInPictureEnabled,
        contentFullscreen = contentFullscreenKind.isFullscreen,
        playback = playbackSnapshot,
    )

    private fun updatePictureInPictureParams() {
        if (!supportsPictureInPicture()) {
            return
        }
        runCatching {
            setPictureInPictureParams(buildPictureInPictureParams())
        }
    }

    private fun buildPictureInPictureParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder
                .setAutoEnterEnabled(canEnterPictureInPicture())
                .setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun supportsPictureInPicture(): Boolean =
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

}
