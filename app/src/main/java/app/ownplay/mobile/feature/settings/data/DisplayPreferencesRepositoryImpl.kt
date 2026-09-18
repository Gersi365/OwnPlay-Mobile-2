package app.ownplay.mobile.feature.settings.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import app.ownplay.mobile.feature.settings.domain.DisplayPreferences
import app.ownplay.mobile.feature.settings.domain.DisplayPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.rebuildDisplayPreferencesDataStore by preferencesDataStore(
    name = "ownplay_rebuild_display_preferences",
)

internal interface DisplayPreferencesStore {
    val preferences: Flow<DisplayPreferences>
    suspend fun setCompactMediaRows(enabled: Boolean)
}

internal class DisplayPreferencesDataStore(
    private val context: Context,
) : DisplayPreferencesStore {
    override val preferences: Flow<DisplayPreferences> =
        context.rebuildDisplayPreferencesDataStore.data.map { values ->
            DisplayPreferences(
                compactMediaRows = values[COMPACT_MEDIA_ROWS] ?: false,
            )
        }

    override suspend fun setCompactMediaRows(enabled: Boolean) {
        context.rebuildDisplayPreferencesDataStore.edit { values ->
            values[COMPACT_MEDIA_ROWS] = enabled
        }
    }

    private companion object {
        val COMPACT_MEDIA_ROWS = booleanPreferencesKey("compact_media_rows")
    }
}

internal class DataStoreDisplayPreferencesRepository(
    private val store: DisplayPreferencesStore,
) : DisplayPreferencesRepository {
    override val preferences: Flow<DisplayPreferences> = store.preferences

    override suspend fun setCompactMediaRows(enabled: Boolean): Boolean =
        try {
            store.setCompactMediaRows(enabled)
            true
        } catch (_: Exception) {
            false
        }
}
