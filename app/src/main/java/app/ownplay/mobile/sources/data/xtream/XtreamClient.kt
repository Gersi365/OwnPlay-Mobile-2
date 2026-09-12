package app.ownplay.mobile.sources.data.xtream

import app.ownplay.mobile.sources.domain.SourceCredential

interface XtreamClient {
    suspend fun authenticate(
        baseUrl: String,
        credential: SourceCredential.Xtream,
    ): XtreamResult<XtreamAccountInfo>

    suspend fun liveCategories(baseUrl: String, credential: SourceCredential.Xtream): XtreamResult<List<XtreamCategory>>
    suspend fun liveStreams(
        baseUrl: String,
        credential: SourceCredential.Xtream,
        categoryId: String? = null,
    ): XtreamResult<List<XtreamLiveStream>>
    suspend fun vodCategories(baseUrl: String, credential: SourceCredential.Xtream): XtreamResult<List<XtreamCategory>>
    suspend fun vodStreams(baseUrl: String, credential: SourceCredential.Xtream): XtreamResult<List<XtreamMovie>>
    suspend fun seriesCategories(baseUrl: String, credential: SourceCredential.Xtream): XtreamResult<List<XtreamCategory>>
    suspend fun series(baseUrl: String, credential: SourceCredential.Xtream): XtreamResult<List<XtreamSeries>>
    suspend fun seriesInfo(baseUrl: String, credential: SourceCredential.Xtream, seriesId: String): XtreamResult<XtreamSeriesInfo>
    suspend fun shortEpg(baseUrl: String, credential: SourceCredential.Xtream, streamId: String, limit: Int = 2): XtreamResult<List<XtreamEpgEntry>>
}
