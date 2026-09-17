package app.ownplay.mobile

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import app.ownplay.mobile.app.OwnPlayApp
import app.ownplay.mobile.design.OwnPlayTheme
import app.ownplay.mobile.feature.playback.domain.PlaybackPictureInPicturePolicy
import app.ownplay.mobile.feature.playback.domain.PlaybackSessionState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var latestPlaybackState = PlaybackSessionState()

    private val ownPlayApplication: OwnPlayApplication
        get() = application as OwnPlayApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            ownPlayApplication.services.playbackSessionController.state.collect { state ->
                latestPlaybackState = state
                updatePictureInPictureParams(state)
            }
        }

        setContent {
            OwnPlayTheme {
                OwnPlayApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            ownPlayApplication.services.playbackSessionController.revalidateActiveTarget()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        if (isInPictureInPictureMode) return
        if (!PlaybackPictureInPicturePolicy.isEligible(latestPlaybackState)) return

        if (enterPictureInPictureMode(buildPictureInPictureParams(latestPlaybackState))) {
            ownPlayApplication.services.playbackSessionController
                .onPictureInPictureModeChanged(true)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        ownPlayApplication.services.playbackSessionController
            .onPictureInPictureModeChanged(isInPictureInPictureMode)
    }

    override fun onDestroy() {
        if (isFinishing && !isChangingConfigurations) {
            ownPlayApplication.services.playbackSessionController.clear()
        }
        super.onDestroy()
    }

    private fun updatePictureInPictureParams(state: PlaybackSessionState) {
        setPictureInPictureParams(buildPictureInPictureParams(state))
    }

    private fun buildPictureInPictureParams(state: PlaybackSessionState): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(PlaybackPictureInPicturePolicy.isEligible(state))
        }

        return builder.build()
    }
}
