package app.ownplay.mobile.feature.settings.data

import app.ownplay.mobile.feature.settings.domain.DisplayPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayPreferencesRepositoryTest {
    @Test
    fun compactMediaRowsPersistsThroughRepository() = runBlocking {
        val store = FakeDisplayPreferencesStore()
        val repository = DataStoreDisplayPreferencesRepository(store)

        assertTrue(repository.setCompactMediaRows(true))
        assertTrue(repository.preferences.first().compactMediaRows)
    }

    @Test
    fun storageFailureIsReportedWithoutInventingSuccess() = runBlocking {
        val store = FakeDisplayPreferencesStore(failWrites = true)
        val repository = DataStoreDisplayPreferencesRepository(store)

        assertFalse(repository.setCompactMediaRows(true))
        assertFalse(repository.preferences.first().compactMediaRows)
    }
}

private class FakeDisplayPreferencesStore(
    private val failWrites: Boolean = false,
) : DisplayPreferencesStore {
    private val state = MutableStateFlow(DisplayPreferences())
    override val preferences: Flow<DisplayPreferences> = state

    override suspend fun setCompactMediaRows(enabled: Boolean) {
        if (failWrites) error("storage failed")
        state.value = DisplayPreferences(compactMediaRows = enabled)
    }
}
