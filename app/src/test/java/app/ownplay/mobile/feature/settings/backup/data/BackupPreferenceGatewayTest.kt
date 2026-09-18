package app.ownplay.mobile.feature.settings.backup.data

import app.ownplay.mobile.data.prefs.ActiveSourceSelectionStore
import app.ownplay.mobile.downloads.domain.DownloadPreferences
import app.ownplay.mobile.downloads.domain.DownloadPreferencesRepository
import app.ownplay.mobile.feature.playback.domain.PlaybackPreferences
import app.ownplay.mobile.feature.playback.domain.PlaybackPreferencesRepository
import app.ownplay.mobile.feature.settings.data.SourceRefreshScheduleStore
import app.ownplay.mobile.feature.settings.data.SourceRefreshScheduler
import app.ownplay.mobile.feature.settings.domain.DisplayPreferences
import app.ownplay.mobile.feature.settings.domain.DisplayPreferencesRepository
import app.ownplay.mobile.feature.settings.domain.SourceRefreshSchedule
import app.ownplay.mobile.sources.domain.SourceId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPreferenceGatewayTest {
    @Test
    fun snapshotAndApplyCoverGlobalActiveAndPerSourceSettings() = runBlocking {
        val fixture = Fixture()
        fixture.active.value = "source-1"
        fixture.refresh.values["source-1"] = SourceRefreshSchedule.EVERY_6_HOURS
        val gateway = fixture.gateway()

        val snapshot = gateway.snapshot(listOf("source-1"))
        assertEquals("source-1", snapshot.activeSourceId)
        assertEquals(SourceRefreshSchedule.EVERY_6_HOURS, snapshot.refreshSchedules["source-1"])
        gateway.apply(
            globalSettings = app.ownplay.mobile.feature.settings.backup.domain.BackupGlobalSettings(
                display = DisplayPreferences(compactMediaRows = true),
                playback = PlaybackPreferences(automaticPictureInPicture = false),
                downloads = DownloadPreferences(unmeteredNetworkOnly = true),
            ),
            activeSourceId = null,
            refreshSchedules = mapOf("source-1" to SourceRefreshSchedule.DAILY),
        )

        assertTrue(fixture.display.state.value.compactMediaRows)
        assertFalse(fixture.playback.state.value.automaticPictureInPicture)
        assertTrue(fixture.download.state.value.unmeteredNetworkOnly)
        assertEquals(null, fixture.active.value)
        assertEquals(SourceRefreshSchedule.DAILY, fixture.refresh.values["source-1"])
    }

    @Test
    fun restoreReinstatesSnapshotAfterAppliedChanges() = runBlocking {
        val fixture = Fixture()
        fixture.active.value = "source-1"
        fixture.refresh.values["source-1"] = SourceRefreshSchedule.EVERY_6_HOURS
        val gateway = fixture.gateway()
        val before = gateway.snapshot(listOf("source-1"))

        gateway.apply(
            globalSettings = app.ownplay.mobile.feature.settings.backup.domain.BackupGlobalSettings(
                display = DisplayPreferences(compactMediaRows = true),
                playback = PlaybackPreferences(automaticPictureInPicture = false),
                downloads = DownloadPreferences(unmeteredNetworkOnly = true),
            ),
            activeSourceId = null,
            refreshSchedules = mapOf("source-1" to SourceRefreshSchedule.DAILY),
        )
        gateway.restore(before)

        assertFalse(fixture.display.state.value.compactMediaRows)
        assertTrue(fixture.playback.state.value.automaticPictureInPicture)
        assertFalse(fixture.download.state.value.unmeteredNetworkOnly)
        assertEquals("source-1", fixture.active.value)
        assertEquals(SourceRefreshSchedule.EVERY_6_HOURS, fixture.refresh.values["source-1"])
    }

    @Test
    fun schedulerSyncOnlyTargetsEnabledSourcesAndCountsFailures() {
        val fixture = Fixture()
        fixture.scheduler.failOn += "source-2"
        val failures = fixture.gateway().syncRefreshSchedules(
            schedules = mapOf(
                "source-1" to SourceRefreshSchedule.DAILY,
                "source-2" to SourceRefreshSchedule.EVERY_12_HOURS,
                "source-3" to SourceRefreshSchedule.EVERY_6_HOURS,
            ),
            enabledSourceIds = setOf("source-1", "source-2"),
        )

        assertEquals(1, failures)
        assertEquals(listOf("source-1", "source-2"), fixture.scheduler.applied)
    }
}

private class Fixture {
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
    val state = MutableStateFlow<String?>(null)
    var value: String?
        get() = state.value
        set(value) { state.value = value }
    override val selectedSourceId: Flow<String?> = state
    override suspend fun currentSelectedSourceId(): String? = state.value
    override suspend fun setSelectedSourceId(sourceId: String?) { state.value = sourceId }
}

private class FakeDisplayRepository : DisplayPreferencesRepository {
    val state = MutableStateFlow(DisplayPreferences())
    override val preferences: Flow<DisplayPreferences> = state
    override suspend fun setCompactMediaRows(enabled: Boolean): Boolean {
        state.value = DisplayPreferences(enabled)
        return true
    }
}

private class FakePlaybackRepository : PlaybackPreferencesRepository {
    val state = MutableStateFlow(PlaybackPreferences())
    override val preferences: Flow<PlaybackPreferences> = state
    override suspend fun setAutomaticPictureInPicture(enabled: Boolean): Boolean {
        state.value = PlaybackPreferences(enabled)
        return true
    }
}

private class FakeDownloadRepository : DownloadPreferencesRepository {
    val state = MutableStateFlow(DownloadPreferences())
    override val preferences: Flow<DownloadPreferences> = state
    override suspend fun current(): DownloadPreferences = state.value
    override suspend fun setUnmeteredNetworkOnly(enabled: Boolean): Boolean {
        state.value = DownloadPreferences(enabled)
        return true
    }
}

private class FakeRefreshStore : SourceRefreshScheduleStore {
    val values = mutableMapOf<String, SourceRefreshSchedule>()
    override fun observe(sourceId: SourceId): Flow<SourceRefreshSchedule> =
        MutableStateFlow(values[sourceId.value] ?: SourceRefreshSchedule.MANUAL)
    override suspend fun current(sourceId: SourceId): SourceRefreshSchedule =
        values[sourceId.value] ?: SourceRefreshSchedule.MANUAL
    override suspend fun set(sourceId: SourceId, schedule: SourceRefreshSchedule) {
        values[sourceId.value] = schedule
    }
    override suspend fun clear(sourceId: SourceId) {
        values.remove(sourceId.value)
    }
}

private class FakeRefreshScheduler : SourceRefreshScheduler {
    val applied = mutableListOf<String>()
    val failOn = mutableSetOf<String>()
    override fun apply(sourceId: SourceId, schedule: SourceRefreshSchedule) {
        applied += sourceId.value
        if (sourceId.value in failOn) error("scheduler failure")
    }
    override fun cancel(sourceId: SourceId) = Unit
}
