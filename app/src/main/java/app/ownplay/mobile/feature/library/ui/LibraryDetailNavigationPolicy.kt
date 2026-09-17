package app.ownplay.mobile.feature.library.ui

import app.ownplay.mobile.feature.library.domain.LibraryMediaKind

internal sealed interface LibraryDetailTargetStage33 {
    data class Movie(val movieId: String) : LibraryDetailTargetStage33
    data class Series(val seriesId: String, val episodeId: String? = null) : LibraryDetailTargetStage33
}

internal object LibraryDetailNavigationPolicyStage33 {
    fun target(
        mediaKind: LibraryMediaKind,
        contentId: String,
        episodeSeriesId: String? = null,
    ): LibraryDetailTargetStage33? {
        if (contentId.isBlank()) return null
        return when (mediaKind) {
            LibraryMediaKind.MOVIE -> LibraryDetailTargetStage33.Movie(contentId)
            LibraryMediaKind.EPISODE -> episodeSeriesId
                ?.takeIf(String::isNotBlank)
                ?.let { LibraryDetailTargetStage33.Series(it, contentId) }
        }
    }
}
