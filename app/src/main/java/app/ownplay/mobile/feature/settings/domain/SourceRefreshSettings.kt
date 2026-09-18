package app.ownplay.mobile.feature.settings.domain

import app.ownplay.mobile.sources.domain.SourceId
import app.ownplay.mobile.sources.domain.SourceRefreshFailureCategory
import kotlinx.coroutines.flow.Flow

enum class SourceRefreshSchedule(
    val displayName: String,
    val repeatHours: Long?,
) {
    MANUAL("Manual only", null),
    EVERY_6_HOURS("Every 6 hours", 6L),
    EVERY_12_HOURS("Every 12 hours", 12L),
    DAILY("Daily", 24L),
}

interface SourceRefreshScheduleRepository {
    fun observeSchedule(sourceId: SourceId): Flow<SourceRefreshSchedule>

    suspend fun setSchedule(
        sourceId: SourceId,
        schedule: SourceRefreshSchedule,
    ): Boolean
}

object SourceRefreshRetryPolicy {
    fun shouldRetry(category: SourceRefreshFailureCategory): Boolean = when (category) {
        SourceRefreshFailureCategory.NETWORK,
        SourceRefreshFailureCategory.TIMEOUT,
        SourceRefreshFailureCategory.TRANSIENT_PROVIDER,
        -> true
        SourceRefreshFailureCategory.AUTHENTICATION,
        SourceRefreshFailureCategory.INVALID_PAYLOAD,
        SourceRefreshFailureCategory.PROVIDER,
        SourceRefreshFailureCategory.STORAGE,
        SourceRefreshFailureCategory.UNKNOWN,
        -> false
    }
}
