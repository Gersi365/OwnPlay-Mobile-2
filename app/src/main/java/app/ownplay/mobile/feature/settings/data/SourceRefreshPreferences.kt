package app.ownplay.mobile.feature.settings.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.ownplay.mobile.feature.settings.domain.SourceRefreshSchedule
import app.ownplay.mobile.sources.domain.SourceId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.rebuildRefreshPreferencesDataStore by preferencesDataStore(
    name = "ownplay_rebuild_refresh_preferences",
)

internal interface SourceRefreshScheduleStore {
    fun observe(sourceId: SourceId): Flow<SourceRefreshSchedule>
    suspend fun current(sourceId: SourceId): SourceRefreshSchedule
    suspend fun set(sourceId: SourceId, schedule: SourceRefreshSchedule)
    suspend fun clear(sourceId: SourceId)
}

internal class SourceRefreshSchedulePreferences(
    private val context: Context,
) : SourceRefreshScheduleStore {
    override fun observe(sourceId: SourceId): Flow<SourceRefreshSchedule> =
        context.rebuildRefreshPreferencesDataStore.data.map { preferences ->
            decode(preferences[key(sourceId)])
        }

    override suspend fun current(sourceId: SourceId): SourceRefreshSchedule =
        decode(context.rebuildRefreshPreferencesDataStore.data.first()[key(sourceId)])

    override suspend fun set(sourceId: SourceId, schedule: SourceRefreshSchedule) {
        context.rebuildRefreshPreferencesDataStore.edit { preferences ->
            preferences[key(sourceId)] = schedule.name
        }
    }

    override suspend fun clear(sourceId: SourceId) {
        context.rebuildRefreshPreferencesDataStore.edit { preferences ->
            preferences.remove(key(sourceId))
        }
    }

    private fun key(sourceId: SourceId) = stringPreferencesKey("source_refresh_${sourceId.value}")

    private fun decode(value: String?): SourceRefreshSchedule =
        value?.let { runCatching { SourceRefreshSchedule.valueOf(it) }.getOrNull() }
            ?: SourceRefreshSchedule.MANUAL
}
