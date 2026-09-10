package app.ownplay.mobile.sources.domain

import kotlinx.coroutines.flow.Flow

interface SourceRepository {
    fun observeSources(): Flow<List<Source>>

    fun observeActiveSource(): Flow<Source?>

    suspend fun addSource(input: NewSource): SourceResult<Source>

    suspend fun updateSource(input: SourceUpdate): SourceResult<Unit>

    suspend fun removeSource(sourceId: String): SourceResult<Unit>

    suspend fun selectSource(sourceId: String?): SourceResult<Unit>

    suspend fun refresh(sourceId: String): SourceResult<RefreshSummary>
}
