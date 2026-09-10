package app.ownplay.mobile.sources.domain

data class SourceManagementState(
    val sources: List<Source> = emptyList(),
    val activeSourceId: String? = null,
    val refreshingSourceIds: Set<String> = emptySet(),
    val lastErrorCode: String? = null,
)

sealed interface SourceManagementAction {
    data class SourcesChanged(val sources: List<Source>) : SourceManagementAction
    data class ActiveSourceResolved(val sourceId: String?) : SourceManagementAction
    data class RefreshStarted(val sourceId: String) : SourceManagementAction
    data class RefreshFinished(val sourceId: String, val errorCode: String? = null) : SourceManagementAction
    data object ErrorCleared : SourceManagementAction
}

object SourceManagementReducer {
    fun reduce(
        state: SourceManagementState,
        action: SourceManagementAction,
    ): SourceManagementState = when (action) {
        is SourceManagementAction.SourcesChanged -> state.copy(sources = action.sources)
        is SourceManagementAction.ActiveSourceResolved -> state.copy(activeSourceId = action.sourceId)
        is SourceManagementAction.RefreshStarted -> state.copy(
            refreshingSourceIds = state.refreshingSourceIds + action.sourceId,
            lastErrorCode = null,
        )
        is SourceManagementAction.RefreshFinished -> state.copy(
            refreshingSourceIds = state.refreshingSourceIds - action.sourceId,
            lastErrorCode = action.errorCode,
        )
        SourceManagementAction.ErrorCleared -> state.copy(lastErrorCode = null)
    }
}
