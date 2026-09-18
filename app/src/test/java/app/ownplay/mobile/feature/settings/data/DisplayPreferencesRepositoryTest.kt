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
    fun displayPreferencesPersistThroughRepository() = runBlocking {
        val store = FakeDisplayPreferencesStore()
        val repository = DataStoreDisplayPreferencesRepository(store)

        assertTrue(repository.setCompactMediaRows(true))
        assertTrue(repository.setShowChannelLogos(false))
        assertTrue(repository.setPreferTvgName(true))
        val current = repository.preferences.first()
        assertTrue(current.compactMediaRows)
        assertFalse(current.showChannelLogos)
        assertTrue(current.preferTvgName)
    }

    @Test
    fun storageFailureIsReportedWithoutInventingSuccess() = runBlocking {
        val store = FakeDisplayPreferencesStore(failWrites = true)
        val repository = DataStoreDisplayPreferencesRepository(store)

        assertFalse(repository.setCompactMediaRows(true))
        assertFalse(repository.setShowChannelLogos(false))
        assertFalse(repository.setPreferTvgName(true))
        assertFalse(repository.preferences.first().compactMediaRows)
        assertTrue(repository.preferences.first().showChannelLogos)
        assertFalse(repository.preferences.first().preferTvgName)
    }
}

private class FakeDisplayPreferencesStore(
    private val failWrites: Boolean = false,
) : DisplayPreferencesStore {
    private val state = MutableStateFlow(DisplayPreferences())
    override val preferences: Flow<DisplayPreferences> = state

    override suspend fun setCompactMediaRows(enabled: Boolean) = update { copy(compactMediaRows = enabled) }
    override suspend fun setShowChannelLogos(enabled: Boolean) = update { copy(showChannelLogos = enabled) }
    override suspend fun setPreferTvgName(enabled: Boolean) = update { copy(preferTvgName = enabled) }

    private fun update(block: DisplayPreferences.() -> DisplayPreferences) {
        if (failWrites) error("storage failed")
        state.value = state.value.block()
    }
}
