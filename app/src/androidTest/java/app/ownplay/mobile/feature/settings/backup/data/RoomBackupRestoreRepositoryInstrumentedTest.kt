package app.ownplay.mobile.feature.settings.backup.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.ownplay.mobile.data.db.OwnPlayDatabase
import app.ownplay.mobile.data.prefs.ActiveSourceSelectionStore
import app.ownplay.mobile.downloads.domain.DownloadPreferences
import app.ownplay.mobile.downloads.domain.DownloadPreferencesRepository
import app.ownplay.mobile.feature.live.domain.LiveOrganizationMode
import app.ownplay.mobile.feature.playback.domain.PlaybackPreferences
import app.ownplay.mobile.feature.playback.domain.PlaybackPreferencesRepository
import app.ownplay.mobile.feature.settings.data.SourceRefreshScheduleStore
import app.ownplay.mobile.feature.settings.data.SourceRefreshScheduler
import app.ownplay.mobile.feature.settings.domain.DisplayPreferences
import app.ownplay.mobile.feature.settings.domain.DisplayPreferencesRepository
import app.ownplay.mobile.feature.settings.domain.SourceRefreshSchedule
import app.ownplay.mobile.feature.settings.backup.domain.BackupCatalogKind
import app.ownplay.mobile.feature.settings.backup.domain.BackupCategoryPersonalization
import app.ownplay.mobile.feature.settings.backup.domain.BackupExportResult
import app.ownplay.mobile.feature.settings.backup.domain.BackupGlobalSettings
import app.ownplay.mobile.feature.settings.backup.domain.BackupRestorePreview
import app.ownplay.mobile.feature.settings.backup.domain.BackupRestoreResult
import app.ownplay.mobile.feature.settings.backup.domain.BackupSourceDefinition
import app.ownplay.mobile.feature.settings.backup.domain.BackupSourceSettings
import app.ownplay.mobile.feature.settings.backup.domain.OwnPlayBackupEnvelope
import app.ownplay.mobile.feature.settings.backup.domain.OwnPlayBackupPayload
import app.ownplay.mobile.sources.domain.SourceId
import app.ownplay.mobile.sources.domain.SourceType
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomBackupRestoreRepositoryInstrumentedTest {
    private lateinit var database: OwnPlayDatabase
    private lateinit var preferences: PreferenceFixture
    private lateinit var repository: RoomBackupRestoreRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OwnPlayDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferences = PreferenceFixture()
        repository = RoomBackupRestoreRepository(
            database = database,
            sourceDao = database.sourceDao(),
            backupDao = database.backupDao(),
            preferences = preferences.gateway(),
            now = { Instant.parse("2026-09-18T08:00:00Z") },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun restoreCreatesStableDisabledSourceWithoutCredentialReference() = runBlocking {
        val json = backupJson()
        assertTrue(repository.previewRestore(json) is BackupRestorePreview.Ready)

        val result = repository.restore(json)
        assertTrue(result is BackupRestoreResult.Success)

        val restored = database.sourceDao().get("source-restored")!!
        assertEquals("Restored source", restored.displayName)
        assertEquals("https://example.com/portal", restored.baseLocator)
        assertFalse(restored.enabled)
        assertNull(restored.credentialReference)
        assertEquals(
            SourceRefreshSchedule.DAILY,
            preferences.refresh.currentOrNull(SourceId("source-restored")),
        )
        assertNull(preferences.active.value)
        assertTrue(preferences.scheduler.applied.isEmpty())
        assertEquals(1, database.backupDao().getCategoryPersonalization().size)

        val exported = repository.exportJson()
        assertTrue(exported is BackupExportResult.Success)
        val exportedJson = (exported as BackupExportResult.Success).json
        assertFalse(exportedJson.contains("password", ignoreCase = true))
        assertFalse(exportedJson.contains("credentialReference", ignoreCase = true))
    }

    @Test
    fun preferenceFailureRollsBackRoomAndCompensatesGlobalPreferences() = runBlocking {
        preferences.playback.failNext = true

        val result = repository.restore(backupJson())

        assertTrue(result is BackupRestoreResult.StorageFailure)
        assertTrue(database.sourceDao().getAll().isEmpty())
        assertFalse(preferences.display.state.value.compactMediaRows)
        assertTrue(preferences.display.state.value.showChannelLogos)
        assertFalse(preferences.display.state.value.preferTvgName)
        assertTrue(preferences.playback.state.value.automaticPictureInPicture)
        assertFalse(preferences.download.state.value.unmeteredNetworkOnly)
        assertNull(preferences.active.value)
    }

    private fun backupJson(): String = BackupJsonCodec.encode(
        OwnPlayBackupEnvelope(
            createdAt = "2026-09-18T07:30:00Z",
            payload = OwnPlayBackupPayload(
                sources = listOf(
                    BackupSourceDefinition(
                        sourceId = "source-restored",
                        type = SourceType.XTREAM,
                        displayName = "Restored source",
                        baseLocator = "https://example.com/portal",
                        enabled = true,
                    ),
                ),
                activeSourceId = "source-restored",
                globalSettings = BackupGlobalSettings(
                    display = DisplayPreferences(compactMediaRows = true, showChannelLogos = false, preferTvgName = true),
                    playback = PlaybackPreferences(automaticPictureInPicture = false),
                    downloads = DownloadPreferences(unmeteredNetworkOnly = true),
                ),
                sourceSettings = listOf(
                    BackupSourceSettings(
                        sourceId = "source-restored",
                        refreshSchedule = SourceRefreshSchedule.DAILY,
                        liveOrganizationMode = LiveOrganizationMode.OWNPLAY,
                    ),
                ),
                categoryPersonalization = listOf(
                    BackupCategoryPersonalization(
                        sourceId = "source-restored",
                        kind = BackupCatalogKind.LIVE,
                        categoryKey = "news",
                        hidden = true,
                        manualOrder = 2,
                    ),
                ),
            ),
        ),
    )
}

private class PreferenceFixture {
    val active = FakeActiveSourceStore()
    val display = FakeDisplayRepository()
    val playback = FakePlaybackRepository()
    val download = FakeDownloadRepository()
    val refresh = FakeRefreshStore()
    val scheduler = FakeRefreshScheduler()

    fun gateway() = BackupPreferenceGateway(
        activeSourceStore = active,
        displayRepository = display,
        playbackRepository = playback,
        downloadRepository = download,
        refreshStore = refresh,
        refreshScheduler = scheduler,
    )
}

private class FakeActiveSourceStore : ActiveSourceSelectionStore {
    private val selected = MutableStateFlow<String?>(null)
    var value: String?
        get() = selected.value
        set(value) { selected.value = value }

    override val selectedSourceId: Flow<String?> = selected
    override suspend fun currentSelectedSourceId(): String? = selected.value
    override suspend fun setSelectedSourceId(sourceId: String?) {
        selected.value = sourceId
    }
}

private class FakeDisplayRepository : DisplayPreferencesRepository {
    val state = MutableStateFlow(DisplayPreferences())
    override val preferences: Flow<DisplayPreferences> = state

    override suspend fun setCompactMediaRows(enabled: Boolean): Boolean = update { copy(compactMediaRows = enabled) }
    override suspend fun setShowChannelLogos(enabled: Boolean): Boolean = update { copy(showChannelLogos = enabled) }
    override suspend fun setPreferTvgName(enabled: Boolean): Boolean = update { copy(preferTvgName = enabled) }

    private fun update(block: DisplayPreferences.() -> DisplayPreferences): Boolean {
        state.value = state.value.block()
        return true
    }
}
private class FakePlaybackRepository : PlaybackPreferencesRepository {
    val state = MutableStateFlow(PlaybackPreferences())
    var failNext: Boolean = false
    override val preferences: Flow<PlaybackPreferences> = state

    override suspend fun setAutomaticPictureInPicture(enabled: Boolean): Boolean {
        if (failNext) {
            failNext = false
            return false
        }
        state.value = PlaybackPreferences(automaticPictureInPicture = enabled)
        return true
    }
}

private class FakeDownloadRepository : DownloadPreferencesRepository {
    val state = MutableStateFlow(DownloadPreferences())
    override val preferences: Flow<DownloadPreferences> = state
    override suspend fun current(): DownloadPreferences = state.value

    override suspend fun setUnmeteredNetworkOnly(enabled: Boolean): Boolean {
        state.value = DownloadPreferences(unmeteredNetworkOnly = enabled)
        return true
    }
}

private class FakeRefreshStore : SourceRefreshScheduleStore {
    private val values = mutableMapOf<String, SourceRefreshSchedule>()
    override fun observe(sourceId: SourceId): Flow<SourceRefreshSchedule> =
        MutableStateFlow(values[sourceId.value] ?: SourceRefreshSchedule.MANUAL)

    override suspend fun current(sourceId: SourceId): SourceRefreshSchedule =
        values[sourceId.value] ?: SourceRefreshSchedule.MANUAL

    override suspend fun currentOrNull(sourceId: SourceId): SourceRefreshSchedule? =
        values[sourceId.value]

    override suspend fun set(sourceId: SourceId, schedule: SourceRefreshSchedule) {
        values[sourceId.value] = schedule
    }

    override suspend fun clear(sourceId: SourceId) {
        values.remove(sourceId.value)
    }
}

private class FakeRefreshScheduler : SourceRefreshScheduler {
    val applied = mutableListOf<Pair<SourceId, SourceRefreshSchedule>>()

    override fun apply(sourceId: SourceId, schedule: SourceRefreshSchedule) {
        applied += sourceId to schedule
    }

    override fun cancel(sourceId: SourceId) = Unit
}
