package app.ownplay.mobile.feature.playback.domain

internal data class PlaybackPositionSnapshot(
    val positionMs: Long,
    val durationMs: Long?,
    val ended: Boolean,
)

internal data class LibraryPlaybackProgress(
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
)

internal interface PlaybackProgressEngine {
    fun positionSnapshot(): PlaybackPositionSnapshot?

    fun seekTo(positionMs: Long): Boolean
}

internal interface LibraryPlaybackProgressStore {
    suspend fun load(target: PlaybackTarget.Library): LibraryPlaybackProgress?

    fun record(
        target: PlaybackTarget.Library,
        progress: LibraryPlaybackProgress,
    )

    fun close() = Unit
}

internal object LibraryPlaybackProgressPolicy {
    fun checkpoint(snapshot: PlaybackPositionSnapshot?): LibraryPlaybackProgress? {
        val duration = snapshot?.durationMs?.takeIf { it > 0L } ?: return null
        val position = if (snapshot.ended) {
            duration
        } else {
            snapshot.positionMs.coerceIn(0L, duration)
        }
        if (!snapshot.ended && position <= 0L) return null

        return LibraryPlaybackProgress(
            positionMs = position,
            durationMs = duration,
            completed = snapshot.ended,
        )
    }

    fun resumePosition(progress: LibraryPlaybackProgress?): Long? {
        val value = progress ?: return null
        if (value.completed) return null
        if (value.durationMs <= 0L) return null
        return value.positionMs.takeIf { it in 1 until value.durationMs }
    }
}
