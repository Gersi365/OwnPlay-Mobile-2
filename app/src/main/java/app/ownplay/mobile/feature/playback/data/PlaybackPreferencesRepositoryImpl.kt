package app.ownplay.mobile.feature.playback.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import app.ownplay.mobile.feature.playback.domain.PlaybackPreferences
import app.ownplay.mobile.feature.playback.domain.PlaybackPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.rebuildPlaybackPreferencesDataStore by preferencesDataStore(
    name = "ownplay_rebuild_playback_preferences",
)

internal interface PlaybackPreferencesStore {
    val preferences: Flow<PlaybackPreferences>
    suspend fun setAutomaticPictureInPicture(enabled: Boolean)
}

internal class PlaybackPreferencesDataStore(
    private val context: Context,
) : PlaybackPreferencesStore {
    override val preferences: Flow<PlaybackPreferences> =
        context.rebuildPlaybackPreferencesDataStore.data.map { values ->
            PlaybackPreferences(
                automaticPictureInPicture = values[AUTOMATIC_PIP] ?: true,
            )
        }

    override suspend fun setAutomaticPictureInPicture(enabled: Boolean) {
        context.rebuildPlaybackPreferencesDataStore.edit { values ->
            values[AUTOMATIC_PIP] = enabled
        }
    }

    private companion object {
        val AUTOMATIC_PIP = booleanPreferencesKey("automatic_picture_in_picture")
    }
}

internal class DataStorePlaybackPreferencesRepository(
    private val store: PlaybackPreferencesStore,
) : PlaybackPreferencesRepository {
    override val preferences: Flow<PlaybackPreferences> = store.preferences

    override suspend fun setAutomaticPictureInPicture(enabled: Boolean): Boolean =
        try {
            store.setAutomaticPictureInPicture(enabled)
            true
        } catch (_: Exception) {
            false
        }
}
