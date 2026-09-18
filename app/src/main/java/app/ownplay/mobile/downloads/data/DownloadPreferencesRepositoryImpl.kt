package app.ownplay.mobile.downloads.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import app.ownplay.mobile.downloads.domain.DownloadPreferences
import app.ownplay.mobile.downloads.domain.DownloadPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.rebuildDownloadPreferencesDataStore by preferencesDataStore(
    name = "ownplay_rebuild_download_preferences",
)

internal interface DownloadPreferencesStore {
    val preferences: Flow<DownloadPreferences>
    suspend fun setUnmeteredNetworkOnly(enabled: Boolean)
}

internal class DownloadPreferencesDataStore(
    private val context: Context,
) : DownloadPreferencesStore {
    override val preferences: Flow<DownloadPreferences> =
        context.rebuildDownloadPreferencesDataStore.data.map { values ->
            DownloadPreferences(
                unmeteredNetworkOnly = values[UNMETERED_NETWORK_ONLY] ?: false,
            )
        }

    override suspend fun setUnmeteredNetworkOnly(enabled: Boolean) {
        context.rebuildDownloadPreferencesDataStore.edit { values ->
            values[UNMETERED_NETWORK_ONLY] = enabled
        }
    }

    private companion object {
        val UNMETERED_NETWORK_ONLY = booleanPreferencesKey("unmetered_network_only")
    }
}

internal class DownloadNotificationPermissionPreferences(
    private val context: Context,
) {
    val prompted: Flow<Boolean> = context.rebuildDownloadPreferencesDataStore.data
        .map { values -> values[NOTIFICATION_PERMISSION_PROMPTED] ?: false }

    suspend fun markPrompted() {
        context.rebuildDownloadPreferencesDataStore.edit { values ->
            values[NOTIFICATION_PERMISSION_PROMPTED] = true
        }
    }

    private companion object {
        val NOTIFICATION_PERMISSION_PROMPTED =
            booleanPreferencesKey("notification_permission_prompted")
    }
}

internal class DataStoreDownloadPreferencesRepository(
    private val store: DownloadPreferencesStore,
) : DownloadPreferencesRepository {
    override val preferences: Flow<DownloadPreferences> =
        store.preferences.catch { emit(DownloadPreferences()) }

    override suspend fun current(): DownloadPreferences =
        runCatching { store.preferences.first() }.getOrDefault(DownloadPreferences())

    override suspend fun setUnmeteredNetworkOnly(enabled: Boolean): Boolean =
        try {
            store.setUnmeteredNetworkOnly(enabled)
            true
        } catch (_: Exception) {
            false
        }
}
