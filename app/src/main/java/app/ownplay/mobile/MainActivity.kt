package app.ownplay.mobile

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import app.ownplay.mobile.app.OwnPlayApp
import app.ownplay.mobile.core.OwnPlayServices
import app.ownplay.mobile.playback.domain.PictureInPicturePolicy
import app.ownplay.mobile.playback.domain.PlaybackSnapshot
import app.ownplay.mobile.playback.domain.VideoTarget
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var services: OwnPlayServices
    private var contentFullscreen = false
    private var pictureInPictureEnabled = true
    private var playbackSnapshot = PlaybackSnapshot()
    private var resumePlaybackAfterBackground = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        services = (application as OwnPlayApplication).services

        lifecycleScope.launch {
            combine(
                services.settingsPreferences.settings,
                services.playbackController.state,
            ) { settings, playback ->
                settings.pictureInPictureEnabled to playback
            }.collect { (enabled, playback) ->
                pictureInPictureEnabled = enabled
                playbackSnapshot = playback
                updatePictureInPictureParams()
            }
        }

        setContent {
            OwnPlayApp(
                services = services,
                onFullscreenChanged = { fullscreen ->
                    contentFullscreen = fullscreen
                    updatePictureInPictureParams()
                },
            )
        }
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
                isFinishing -> lifecycleScope.launch {
                    resumePlaybackAfterBackground = false
                    services.playbackController.stop(clearMedia = true)
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
