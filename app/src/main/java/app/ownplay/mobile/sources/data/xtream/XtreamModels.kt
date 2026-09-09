package app.ownplay.mobile.sources.data.xtream

data class XtreamAccountInfo(
    val authenticated: Boolean,
    val status: String?,
    val expirationEpochSeconds: Long?,
)

data class XtreamCategory(
    val providerKey: String,
    val name: String,
    val providerOrder: Int,
)

data class XtreamLiveStream(
    val streamId: String,
    val categoryId: String?,
    val name: String,
    val epgChannelId: String?,
    val streamIcon: String?,
    val providerOrder: Int,
)

data class XtreamMovie(
    val streamId: String,
    val categoryId: String?,
    val name: String,
    val posterUrl: String?,
    val extension: String?,
    val rating: String?,
    val providerOrder: Int,
)

data class XtreamSeries(
    val seriesId: String,
    val categoryId: String?,
    val name: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val description: String?,
    val rating: String?,
    val providerOrder: Int,
)

data class XtreamEpisode(
    val episodeId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val durationSeconds: Long?,
    val extension: String?,
)

data class XtreamSeriesInfo(
    val seriesId: String,
    val episodes: List<XtreamEpisode>,
)

data class XtreamEpgEntry(
    val title: String,
    val startEpochSeconds: Long?,
    val endEpochSeconds: Long?,
)

sealed interface XtreamResult<out T> {
    data class Success<T>(val value: T) : XtreamResult<T>
    data class Failure(val code: String) : XtreamResult<Nothing>
}
