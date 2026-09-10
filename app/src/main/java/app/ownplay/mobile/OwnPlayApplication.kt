package app.ownplay.mobile

import android.app.Application
import app.ownplay.mobile.core.OwnPlayServices
import app.ownplay.mobile.feature.settings.domain.ProviderRefreshSchedulePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class OwnPlayApplication : Application() {
    val services: OwnPlayServices by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        OwnPlayServices.create(this)
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            services.settingsPreferences.settings
                .map(ProviderRefreshSchedulePolicy::intervalHours)
                .distinctUntilChanged()
                .collect(services.providerRefreshScheduler::syncIntervalHours)
        }
    }

    fun stopPlaybackForActivityFinish() {
        applicationScope.launch {
            services.playbackController.stop(clearMedia = true)
        }
    }
}
