package app.ownplay.mobile

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import app.ownplay.mobile.app.OwnPlayApp
import app.ownplay.mobile.core.OwnPlayServices
import app.ownplay.mobile.playback.domain.PictureInPicturePolicy
import app.ownplay.mobile.playback.domain.PlaybackSnapshot
import app.ownplay.mobile.playback.domain.PlayerLocalControlPolicy
import app.ownplay.mobile.playback.domain.VideoTarget
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var ownPlayApplication: OwnPlayApplication
    private lateinit var services: OwnPlayServices
    private var contentFullscreen = false
    private var pictureInPictureEnabled = true
    private var playbackSnapshot = PlaybackSnapshot()
    private var resumePlaybackAfterBackground = false
    private var appPlaybackVolume = 1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ownPlayApplication = application as OwnPlayApplication
        services = ownPlayApplication.services

        lifecycleScope.launch {
            combine(
                services.settingsPreferences.settings,
                services.playbackController.state,
            ) { settings, playback ->
                settings.pictureInPictureEnabled to playback
            }.collect { (enabled, playback) ->
                pictureInPictureEnabled = enabled
                playbackSnapshot = playback
                appPlaybackVolume = playback.volume
                if (playback.mediaId == null) {
                    resetOwnPlayBrightness()
                }
                updatePictureInPictureParams()
            }
        }

        setContent {
            OwnPlayApp(
                services = services,
                onFullscreenChanged = { fullscreen ->
                    contentFullscreen = fullscreen
                    setContentOrientation(fullscreen)
                    setImmersiveFullscreen(fullscreen)
                    updatePictureInPictureParams()
                },
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
                contentFullscreen &&
                currentTarget == VideoTarget.PIP -> lifecycleScope.launch {
                services.playbackController.transferVideoTarget(VideoTarget.FULLSCREEN)
            }
        }
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

    private fun setContentOrientation(fullscreen: Boolean) {
        requestedOrientation = if (fullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

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
        contentFullscreen = contentFullscreen,
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
