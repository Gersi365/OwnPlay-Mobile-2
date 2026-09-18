package app.ownplay.mobile.feature.playback.domain

import kotlinx.coroutines.flow.Flow

data class PlaybackPreferences(
    val automaticPictureInPicture: Boolean = true,
)

interface PlaybackPreferencesRepository {
    val preferences: Flow<PlaybackPreferences>

    suspend fun setAutomaticPictureInPicture(enabled: Boolean): Boolean
}

object PlaybackAutomaticPictureInPicturePolicy {
    fun isEligible(
        preferences: PlaybackPreferences,
        state: PlaybackSessionState,
    ): Boolean =
        preferences.automaticPictureInPicture && PlaybackPictureInPicturePolicy.isEligible(state)
}
