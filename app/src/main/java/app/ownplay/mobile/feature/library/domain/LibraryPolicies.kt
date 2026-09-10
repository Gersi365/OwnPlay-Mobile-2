package app.ownplay.mobile.feature.library.domain

import app.ownplay.mobile.playback.domain.PlaybackStart
import java.util.Locale

object LibraryStartPolicy {
    fun resolve(
        startMode: LibraryStartMode,
        savedPositionMs: Long?,
    ): PlaybackStart = when (startMode) {
        LibraryStartMode.RESUME -> savedPositionMs
            ?.takeIf { it > 0L }
            ?.let(PlaybackStart::Resume)
            ?: PlaybackStart.Beginning

        LibraryStartMode.BEGINNING -> PlaybackStart.Beginning
    }
}

object LibraryCompletionPolicy {
    const val COMPLETION_PERCENT: Long = 95L

    fun isComplete(
        positionMs: Long,
        durationMs: Long,
        ended: Boolean,
    ): Boolean {
        if (ended) return true
        if (durationMs <= 0L) return false
        val boundedPosition = positionMs.coerceAtLeast(0L)
        val threshold = durationMs * COMPLETION_PERCENT / 100L
        return boundedPosition >= threshold
    }
}

object LibraryOrderingPolicy {
    fun movies(items: List<LibraryMovie>): List<LibraryMovie> = items.sortedWith(
        compareBy<LibraryMovie> { it.providerOrder }
            .thenBy { it.name.lowercase(Locale.US) }
            .thenBy { it.movieId },
    )

    fun series(items: List<LibrarySeries>): List<LibrarySeries> = items.sortedWith(
        compareBy<LibrarySeries> { it.providerOrder }
            .thenBy { it.name.lowercase(Locale.US) }
            .thenBy { it.seriesId },
    )

    fun continueWatching(items: List<ContinueWatchingItem>): List<ContinueWatchingItem> = items.sortedWith(
        compareByDescending<ContinueWatchingItem> { it.updatedAt }
            .thenBy { it.contentId },
    )

    fun downloadedMedia(items: List<LibraryDownloadedMedia>): List<LibraryDownloadedMedia> = items.sortedWith(
        compareByDescending<LibraryDownloadedMedia> { it.createdAt }
            .thenBy { it.downloadId },
    )
}
