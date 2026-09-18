package app.ownplay.mobile.sources.data

import app.ownplay.mobile.data.security.SourceSecret
import app.ownplay.mobile.sources.data.m3u.M3uClient
import app.ownplay.mobile.sources.data.m3u.M3uEntry
import app.ownplay.mobile.sources.data.m3u.M3uParseResult
import app.ownplay.mobile.sources.data.xtream.XtreamCategory
import app.ownplay.mobile.sources.data.xtream.XtreamClient
import app.ownplay.mobile.sources.data.xtream.XtreamConnection
import app.ownplay.mobile.sources.data.xtream.XtreamLiveStream
import app.ownplay.mobile.sources.data.xtream.XtreamMovie
import app.ownplay.mobile.sources.data.xtream.XtreamMovieDetail
import app.ownplay.mobile.sources.data.xtream.XtreamSeries
import app.ownplay.mobile.sources.data.xtream.XtreamSeriesDetail
import app.ownplay.mobile.sources.domain.SourceId
import app.ownplay.mobile.sources.domain.SourceType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SourceCatalogLoaderDispatcherTest {
    @Test
    fun loadMovesCatalogWorkOffCallerThread() = runBlocking {
        val callerThread = Thread.currentThread().name
        var loaderThread: String? = null
        val m3uClient = object : M3uClient {
            override suspend fun fetch(remoteUrl: String): M3uParseResult {
                loaderThread = Thread.currentThread().name
                return M3uParseResult(
                    entries = listOf(
                        M3uEntry(
                            name = "QA Channel",
                            groupTitle = "QA",
                            tvgId = "qa-channel",
                            tvgName = "QA Channel",
                            logoUrl = null,
                            streamUrl = "https://example.com/live.ts",
                        ),
                    ),
                    skippedEntries = 0,
                )
            }
        }
        val loader = DefaultSourceCatalogLoader(
            xtreamClient = UnsupportedXtreamClient,
            m3uClient = m3uClient,
        )

        val snapshot = loader.load(
            sourceId = SourceId("source-1"),
            sourceType = SourceType.M3U,
            baseLocator = "example.com",
            secret = SourceSecret.M3uRemote(
                playlistUrl = "https://example.com/list.m3u",
                epgUrl = null,
            ),
        )

        assertEquals(1, snapshot.liveChannels.size)
        assertNotEquals(callerThread, loaderThread)
    }
}

private object UnsupportedXtreamClient : XtreamClient {
    override suspend fun liveCategories(connection: XtreamConnection): List<XtreamCategory> =
        error("Not used")

    override suspend fun liveStreams(connection: XtreamConnection): List<XtreamLiveStream> =
        error("Not used")

    override suspend fun movieCategories(connection: XtreamConnection): List<XtreamCategory> =
        error("Not used")

    override suspend fun movies(connection: XtreamConnection): List<XtreamMovie> =
        error("Not used")

    override suspend fun movieInfo(
        connection: XtreamConnection,
        movieId: String,
    ): XtreamMovieDetail = error("Not used")

    override suspend fun seriesCategories(connection: XtreamConnection): List<XtreamCategory> =
        error("Not used")

    override suspend fun series(connection: XtreamConnection): List<XtreamSeries> =
        error("Not used")

    override suspend fun seriesInfo(
        connection: XtreamConnection,
        seriesId: String,
    ): XtreamSeriesDetail = error("Not used")
}
