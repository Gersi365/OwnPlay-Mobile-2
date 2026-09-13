package app.ownplay.mobile.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.ownplay.mobile.feature.library.domain.LibraryMediaKind
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

private val Context.ownPlayLibraryVisibilityDataStore by preferencesDataStore(
    name = "ownplay_library_visibility",
)

data class LibraryVisibilitySnapshot(
    val hiddenContinueWatchingKeys: Set<String> = emptySet(),
    val hiddenDownloadIds: Set<String> = emptySet(),
) {
    fun isContinueWatchingHidden(
        sourceId: String,
        mediaKind: LibraryMediaKind,
        contentId: String,
    ): Boolean = LibraryVisibilityPreferences.continueWatchingKey(
        sourceId = sourceId,
        mediaKind = mediaKind,
        contentId = contentId,
    ) in hiddenContinueWatchingKeys

    fun isDownloadHidden(downloadId: String): Boolean = downloadId in hiddenDownloadIds
}

class LibraryVisibilityPreferences(
    private val context: Context,
) {
    val visibility: Flow<LibraryVisibilitySnapshot> = context.ownPlayLibraryVisibilityDataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emitAll(flowOf(emptyPreferences()))
            } else {
                throw throwable
            }
        }
        .map(::toSnapshot)
        .distinctUntilChanged()

    suspend fun hideContinueWatching(
        sourceId: String,
        mediaKind: LibraryMediaKind,
        contentId: String,
    ) = edit { preferences ->
        preferences[HIDDEN_CONTINUE_WATCHING] =
            preferences[HIDDEN_CONTINUE_WATCHING].orEmpty() + continueWatchingKey(sourceId, mediaKind, contentId)
    }

    suspend fun showContinueWatching(
        sourceId: String,
        mediaKind: LibraryMediaKind,
        contentId: String,
    ) = edit { preferences ->
        preferences[HIDDEN_CONTINUE_WATCHING] =
            preferences[HIDDEN_CONTINUE_WATCHING].orEmpty() - continueWatchingKey(sourceId, mediaKind, contentId)
    }

    suspend fun hideDownload(downloadId: String) {
        if (downloadId.isBlank()) return
        edit { preferences ->
            preferences[HIDDEN_DOWNLOADS] = preferences[HIDDEN_DOWNLOADS].orEmpty() + downloadId
        }
    }

    suspend fun showDownload(downloadId: String) {
        if (downloadId.isBlank()) return
        edit { preferences ->
            preferences[HIDDEN_DOWNLOADS] = preferences[HIDDEN_DOWNLOADS].orEmpty() - downloadId
        }
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.ownPlayLibraryVisibilityDataStore.edit(block)
    }

    private fun toSnapshot(preferences: Preferences): LibraryVisibilitySnapshot = LibraryVisibilitySnapshot(
        hiddenContinueWatchingKeys = preferences[HIDDEN_CONTINUE_WATCHING].orEmpty().toSet(),
        hiddenDownloadIds = preferences[HIDDEN_DOWNLOADS].orEmpty().toSet(),
    )

    companion object {
        private val HIDDEN_CONTINUE_WATCHING = stringSetPreferencesKey("hidden_continue_watching")
        private val HIDDEN_DOWNLOADS = stringSetPreferencesKey("hidden_downloads")
        private const val KEY_SEPARATOR = "\u001F"

        fun continueWatchingKey(
            sourceId: String,
            mediaKind: LibraryMediaKind,
            contentId: String,
        ): String = listOf(sourceId, mediaKind.name, contentId).joinToString(KEY_SEPARATOR)
    }
}
