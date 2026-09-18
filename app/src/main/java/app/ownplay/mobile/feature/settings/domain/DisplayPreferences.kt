package app.ownplay.mobile.feature.settings.domain

import kotlinx.coroutines.flow.Flow

data class DisplayPreferences(
    val compactMediaRows: Boolean = false,
)

interface DisplayPreferencesRepository {
    val preferences: Flow<DisplayPreferences>

    suspend fun setCompactMediaRows(enabled: Boolean): Boolean
}
