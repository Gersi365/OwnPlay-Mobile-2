package app.ownplay.mobile.feature.settings.domain

enum class ProviderRefreshInterval(
    val hours: Long,
    val summary: String,
) {
    SIX_HOURS(6, "Every 6 hours"),
    TWELVE_HOURS(12, "Every 12 hours"),
    DAILY(24, "Daily"),
    ;

    fun next(): ProviderRefreshInterval = when (this) {
        SIX_HOURS -> TWELVE_HOURS
        TWELVE_HOURS -> DAILY
        DAILY -> SIX_HOURS
    }
}

data class SettingsSnapshot(
    val pictureInPictureEnabled: Boolean = true,
    val resumePlaybackEnabled: Boolean = true,
    val autoRefreshProviders: Boolean = true,
    val providerRefreshInterval: ProviderRefreshInterval = ProviderRefreshInterval.SIX_HOURS,
    val showChannelLogos: Boolean = true,
)

object ProviderRefreshSchedulePolicy {
    fun intervalHours(settings: SettingsSnapshot): Long? =
        settings.providerRefreshInterval.hours.takeIf { settings.autoRefreshProviders }
}
