package app.ownplay.mobile.feature.settings.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.ownplay.mobile.OwnPlayApplication
import app.ownplay.mobile.feature.settings.domain.BackupResult
import app.ownplay.mobile.sources.domain.SourceResult
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

class ProviderRefreshScheduler(
    context: Context,
) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun syncIntervalHours(intervalHours: Long?) {
        if (intervalHours == null) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<ProviderRefreshWorker>(intervalHours, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private companion object {
        const val WORK_NAME = "ownplay-provider-refresh"
    }
}

class ProviderRefreshWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? OwnPlayApplication ?: return Result.failure()
        val services = app.services
        val sources = services.sourceRepository.observeSources().first()
            .filter { source -> source.enabled && !source.requiresCredentials }

        sources.forEach { source ->
            when (services.sourceRepository.refresh(source.sourceId)) {
                is SourceResult.Success -> {
                    when (services.backupRepository.applyPendingForSource(source.sourceId)) {
                        is BackupResult.Success -> Unit
                        is BackupResult.Failure -> Unit
                    }
                }
                is SourceResult.Failure -> Unit
            }
        }
        return Result.success()
    }
}
