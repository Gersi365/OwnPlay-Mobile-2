package app.ownplay.mobile.feature.playback.data

import app.ownplay.mobile.feature.playback.domain.PlaybackPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPreferencesRepositoryTest {
    @Test
    fun automaticPictureInPicturePersistsThroughRepository() = runBlocking {
        val store = FakePlaybackPreferencesStore()
        val repository = DataStorePlaybackPreferencesRepository(store)

        assertTrue(repository.setAutomaticPictureInPicture(false))
        assertFalse(repository.preferences.first().automaticPictureInPicture)
    }

    @Test
    fun storageFailureDoesNotReportSuccess() = runBlocking {
        val store = FakePlaybackPreferencesStore(failWrites = true)
        val repository = DataStorePlaybackPreferencesRepository(store)

        assertFalse(repository.setAutomaticPictureInPicture(false))
        assertTrue(repository.preferences.first().automaticPictureInPicture)
    }
}

private class FakePlaybackPreferencesStore(
    private val failWrites: Boolean = false,
) : PlaybackPreferencesStore {
    private val state = MutableStateFlow(PlaybackPreferences())
    override val preferences: Flow<PlaybackPreferences> = state

    override suspend fun setAutomaticPictureInPicture(enabled: Boolean) {
        if (failWrites) error("storage failed")
        state.value = PlaybackPreferences(automaticPictureInPicture = enabled)
    }
}
