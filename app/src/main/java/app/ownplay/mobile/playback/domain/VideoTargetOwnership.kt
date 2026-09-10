package app.ownplay.mobile.playback.domain

enum class VideoTarget {
    NONE,
    PREVIEW,
    FULLSCREEN,
    PIP,
}

data class VideoTargetOwnership(
    val activeTarget: VideoTarget = VideoTarget.NONE,
    val generation: Long = 0L,
)

sealed interface VideoTargetEvent {
    data class Acquire(
        val target: VideoTarget,
    ) : VideoTargetEvent

    data class Release(
        val target: VideoTarget,
    ) : VideoTargetEvent
}

object VideoTargetOwnershipReducer {
    fun reduce(
        state: VideoTargetOwnership,
        event: VideoTargetEvent,
    ): VideoTargetOwnership = when (event) {
        is VideoTargetEvent.Acquire -> {
            require(event.target != VideoTarget.NONE) { "NONE cannot be acquired as a video target." }
            if (state.activeTarget == event.target) {
                state
            } else {
                state.copy(
                    activeTarget = event.target,
                    generation = state.generation + 1L,
                )
            }
        }

        is VideoTargetEvent.Release -> {
            require(event.target != VideoTarget.NONE) { "NONE cannot be released as a video target." }
            if (state.activeTarget != event.target) {
                state
            } else {
                state.copy(
                    activeTarget = VideoTarget.NONE,
                    generation = state.generation + 1L,
                )
            }
        }
    }
}
