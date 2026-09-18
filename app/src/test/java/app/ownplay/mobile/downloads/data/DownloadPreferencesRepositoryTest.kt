package app.ownplay.mobile.downloads.data

import app.ownplay.mobile.downloads.domain.DownloadPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPreferencesRepositoryTest {
    @Test
    fun unmeteredPreferencePersistsThroughRepository() = runBlocking {
        val store = FakeDownloadPreferencesStore()
        val repository = DataStoreDownloadPreferencesRepository(store)

        assertTrue(repository.setUnmeteredNetworkOnly(true))
        assertTrue(repository.preferences.first().unmeteredNetworkOnly)
        assertTrue(repository.current().unmeteredNetworkOnly)
    }

    @Test
    fun storageFailureDoesNotReportSuccess() = runBlocking {
        val store = FakeDownloadPreferencesStore(failWrites = true)
        val repository = DataStoreDownloadPreferencesRepository(store)

        assertFalse(repository.setUnmeteredNetworkOnly(true))
        assertFalse(repository.preferences.first().unmeteredNetworkOnly)
    }
}

private class FakeDownloadPreferencesStore(
    private val failWrites: Boolean = false,
) : DownloadPreferencesStore {
    private val state = MutableStateFlow(DownloadPreferences())
    override val preferences: Flow<DownloadPreferences> = state

    override suspend fun setUnmeteredNetworkOnly(enabled: Boolean) {
        if (failWrites) error("storage failed")
        state.value = DownloadPreferences(unmeteredNetworkOnly = enabled)
    }
}
