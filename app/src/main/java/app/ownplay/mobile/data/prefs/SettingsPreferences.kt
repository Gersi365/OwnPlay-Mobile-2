package app.ownplay.mobile.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.ownplay.mobile.feature.settings.domain.ProviderRefreshInterval
import app.ownplay.mobile.feature.settings.domain.SettingsSnapshot
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

private val Context.ownPlaySettingsDataStore by preferencesDataStore(name = "ownplay_settings")

class SettingsPreferences(
    private val context: Context,
) {
    val settings: Flow<SettingsSnapshot> = context.ownPlaySettingsDataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emitAll(flowOf(emptyPreferences()))
            } else {
                throw throwable
            }
        }
        .map(::toSnapshot)
        .distinctUntilChanged()

    suspend fun setPictureInPictureEnabled(enabled: Boolean) = edit {
        it[PICTURE_IN_PICTURE] = enabled
    }

    suspend fun setResumePlaybackEnabled(enabled: Boolean) = edit {
        it[RESUME_PLAYBACK] = enabled
    }

    suspend fun setAutoRefreshProviders(enabled: Boolean) = edit {
        it[AUTO_REFRESH_PROVIDERS] = enabled
    }

    suspend fun setProviderRefreshInterval(interval: ProviderRefreshInterval) = edit {
        it[PROVIDER_REFRESH_INTERVAL] = interval.name
    }

    suspend fun setShowChannelLogos(enabled: Boolean) = edit {
        it[SHOW_CHANNEL_LOGOS] = enabled
    }

    suspend fun replace(snapshot: SettingsSnapshot) = edit { preferences ->
        preferences[PICTURE_IN_PICTURE] = snapshot.pictureInPictureEnabled
        preferences[RESUME_PLAYBACK] = snapshot.resumePlaybackEnabled
        preferences[AUTO_REFRESH_PROVIDERS] = snapshot.autoRefreshProviders
        preferences[PROVIDER_REFRESH_INTERVAL] = snapshot.providerRefreshInterval.name
        preferences[SHOW_CHANNEL_LOGOS] = snapshot.showChannelLogos
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.ownPlaySettingsDataStore.edit(block)
    }

    private fun toSnapshot(preferences: Preferences): SettingsSnapshot = SettingsSnapshot(
        pictureInPictureEnabled = preferences[PICTURE_IN_PICTURE] ?: true,
        resumePlaybackEnabled = preferences[RESUME_PLAYBACK] ?: true,
        autoRefreshProviders = preferences[AUTO_REFRESH_PROVIDERS] ?: true,
        providerRefreshInterval = preferences[PROVIDER_REFRESH_INTERVAL]
            ?.let { stored -> runCatching { ProviderRefreshInterval.valueOf(stored) }.getOrNull() }
            ?: ProviderRefreshInterval.SIX_HOURS,
        showChannelLogos = preferences[SHOW_CHANNEL_LOGOS] ?: true,
    )

    private companion object {
        val PICTURE_IN_PICTURE = booleanPreferencesKey("picture_in_picture_enabled")
        val RESUME_PLAYBACK = booleanPreferencesKey("resume_playback_enabled")
        val AUTO_REFRESH_PROVIDERS = booleanPreferencesKey("auto_refresh_providers")
        val PROVIDER_REFRESH_INTERVAL = stringPreferencesKey("provider_refresh_interval")
        val SHOW_CHANNEL_LOGOS = booleanPreferencesKey("show_channel_logos")
    }
}
