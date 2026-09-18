package app.ownplay.mobile.downloads.domain

import kotlinx.coroutines.flow.Flow

data class DownloadPreferences(
    val unmeteredNetworkOnly: Boolean = false,
)

interface DownloadPreferencesRepository {
    val preferences: Flow<DownloadPreferences>

    suspend fun current(): DownloadPreferences

    suspend fun setUnmeteredNetworkOnly(enabled: Boolean): Boolean
}

object DownloadNetworkPreferencePolicy {
    fun requiresUnmeteredNetwork(preferences: DownloadPreferences): Boolean =
        preferences.unmeteredNetworkOnly
}
