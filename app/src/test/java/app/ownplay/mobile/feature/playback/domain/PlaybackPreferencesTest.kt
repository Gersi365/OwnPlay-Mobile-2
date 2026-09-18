package app.ownplay.mobile.feature.playback.domain

import app.ownplay.mobile.sources.domain.SourceId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPreferencesTest {
    private val eligibleState = PlaybackSessionState(
        target = PlaybackTarget.Movie(SourceId("source-1"), "movie-1"),
        presentation = PlaybackPresentation.FULLSCREEN,
        readiness = PlaybackReadiness.PREPARED,
    )

    @Test
    fun automaticPictureInPictureRequiresBothPreferenceAndEligiblePlayback() {
        assertTrue(
            PlaybackAutomaticPictureInPicturePolicy.isEligible(
                PlaybackPreferences(automaticPictureInPicture = true),
                eligibleState,
            ),
        )
        assertFalse(
            PlaybackAutomaticPictureInPicturePolicy.isEligible(
                PlaybackPreferences(automaticPictureInPicture = false),
                eligibleState,
            ),
        )
        assertFalse(
            PlaybackAutomaticPictureInPicturePolicy.isEligible(
                PlaybackPreferences(automaticPictureInPicture = true),
                eligibleState.copy(readiness = PlaybackReadiness.PREPARING),
            ),
        )
    }
}
