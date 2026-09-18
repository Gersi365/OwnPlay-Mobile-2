package app.ownplay.mobile.feature.settings.backup.data

import app.ownplay.mobile.data.prefs.ActiveSourceSelectionStore
import app.ownplay.mobile.downloads.domain.DownloadPreferencesRepository
import app.ownplay.mobile.feature.playback.domain.PlaybackPreferencesRepository
import app.ownplay.mobile.feature.settings.data.SourceRefreshScheduleStore
import app.ownplay.mobile.feature.settings.data.SourceRefreshScheduler
import app.ownplay.mobile.feature.settings.domain.DisplayPreferencesRepository
import app.ownplay.mobile.feature.settings.domain.SourceRefreshSchedule
import app.ownplay.mobile.feature.settings.backup.domain.BackupGlobalSettings
import app.ownplay.mobile.sources.domain.SourceId
import kotlinx.coroutines.flow.first

data class BackupPreferenceSnapshot(
    val globalSettings: BackupGlobalSettings,
    val activeSourceId: String?,
    val refreshSchedules: Map<String, SourceRefreshSchedule>,
)

internal class BackupPreferenceGateway(
    private val activeSourceStore: ActiveSourceSelectionStore,
    private val displayRepository: DisplayPreferencesRepository,
    private val playbackRepository: PlaybackPreferencesRepository,
    private val downloadRepository: DownloadPreferencesRepository,
    private val refreshStore: SourceRefreshScheduleStore,
    private val refreshScheduler: SourceRefreshScheduler,
) {
    suspend fun snapshot(sourceIds: Collection<String>): BackupPreferenceSnapshot =
        BackupPreferenceSnapshot(
            globalSettings = BackupGlobalSettings(
                display = displayRepository.preferences.first(),
                playback = playbackRepository.preferences.first(),
                downloads = downloadRepository.current(),
            ),
            activeSourceId = activeSourceStore.currentSelectedSourceId(),
            refreshSchedules = sourceIds.associateWith { sourceId ->
                refreshStore.current(SourceId(sourceId))
            },
        )

    suspend fun apply(
        globalSettings: BackupGlobalSettings,
        activeSourceId: String?,
        refreshSchedules: Map<String, SourceRefreshSchedule>,
    ) {
        check(displayRepository.setCompactMediaRows(globalSettings.display.compactMediaRows))
        check(playbackRepository.setAutomaticPictureInPicture(globalSettings.playback.automaticPictureInPicture))
        check(downloadRepository.setUnmeteredNetworkOnly(globalSettings.downloads.unmeteredNetworkOnly))
        activeSourceStore.setSelectedSourceId(activeSourceId)
        refreshSchedules.forEach { (sourceId, schedule) ->
            refreshStore.set(SourceId(sourceId), schedule)
        }
    }

    suspend fun restore(snapshot: BackupPreferenceSnapshot) {
        check(displayRepository.setCompactMediaRows(snapshot.globalSettings.display.compactMediaRows))
        check(playbackRepository.setAutomaticPictureInPicture(snapshot.globalSettings.playback.automaticPictureInPicture))
        check(downloadRepository.setUnmeteredNetworkOnly(snapshot.globalSettings.downloads.unmeteredNetworkOnly))
        activeSourceStore.setSelectedSourceId(snapshot.activeSourceId)
        snapshot.refreshSchedules.forEach { (sourceId, schedule) ->
            refreshStore.set(SourceId(sourceId), schedule)
        }
    }

    fun syncRefreshSchedules(
        schedules: Map<String, SourceRefreshSchedule>,
        enabledSourceIds: Set<String>,
    ): Int {
        var failures = 0
        schedules.forEach { (sourceId, schedule) ->
            if (sourceId !in enabledSourceIds) return@forEach
            if (runCatching { refreshScheduler.apply(SourceId(sourceId), schedule) }.isFailure) {
                failures += 1
            }
        }
        return failures
    }
}
