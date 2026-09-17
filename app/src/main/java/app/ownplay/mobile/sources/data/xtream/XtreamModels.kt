package app.ownplay.mobile.sources.data.xtream

data class XtreamConnection(
    val baseUrl: String,
    val username: String,
    val password: String,
) {
    override fun toString(): String =
        "XtreamConnection(baseUrl=<redacted>, username=<redacted>, password=<redacted>)"
}

data class XtreamCategory(
    val providerCategoryId: String,
    val name: String,
    val providerOrder: Int,
)

data class XtreamLiveStream(
    val streamId: String,
    val categoryId: String?,
    val name: String,
    val tvgId: String?,
    val logoUrl: String?,
    val containerExtension: String?,
    val providerOrder: Int,
)

data class XtreamMovie(
    val streamId: String,
    val categoryId: String?,
    val name: String,
    val posterUrl: String?,
    val containerExtension: String?,
    val rating: String?,
    val providerOrder: Int,
)

data class XtreamMovieDetail(
    val name: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val plot: String?,
    val releaseDate: String?,
    val year: String?,
    val runtimeMs: Long?,
    val rating: String?,
)

data class XtreamSeries(
    val seriesId: String,
    val categoryId: String?,
    val name: String,
    val posterUrl: String?,
    val description: String?,
    val rating: String?,
    val providerOrder: Int,
)

data class XtreamSeriesEpisode(
    val providerEpisodeId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val containerExtension: String?,
    val durationMs: Long?,
)

data class XtreamSeriesDetail(
    val episodes: List<XtreamSeriesEpisode>,
)
