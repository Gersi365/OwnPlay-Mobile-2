package app.ownplay.mobile.sources.data

import app.ownplay.mobile.sources.data.m3u.M3uClient
import app.ownplay.mobile.sources.data.m3u.M3uParser
import app.ownplay.mobile.sources.data.m3u.M3uResult
import app.ownplay.mobile.sources.data.xtream.OkHttpXtreamClient
import app.ownplay.mobile.sources.data.xtream.XtreamResult
import app.ownplay.mobile.sources.domain.Source
import app.ownplay.mobile.sources.domain.SourceCredential
import app.ownplay.mobile.sources.domain.SourceType
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Exercises the real HTTP adapter, JSON parser, catalog loader and refresh plan with fixed responses. */
class ProviderImportIntegrationTest {
    private val clients = mutableListOf<OkHttpClient>()
    private val source = Source("source", "Fixture", SourceType.XTREAM, "http://fixture.invalid", true, 0, 0)
    private val credential = SourceCredential.Xtream("fixture-user", "fixture-password")

    @After fun closeClients() {
        clients.forEach { it.dispatcher.executorService.shutdownNow(); it.connectionPool.evictAll() }
    }

    @Test fun `partial channel array never retires channels or their categories`() = runBlocking {
        val client = client(mapOf(
            "get_live_categories" to (200 to """[{"category_id":"1","category_name":"News"}]"""),
            "get_live_streams" to (200 to """[{"stream_id":7,"category_id":"1","name":"News"},null,{}]"""),
        ))
        val payload = loader(client).load(source, credential)
        val plan = RefreshPolicy.plan(10, payload)
        assertEquals(SectionStatus.PARTIAL, payload.liveChannels.status)
        assertEquals(1, payload.liveChannels.value?.size)
        assertTrue(CatalogSection.LIVE_CHANNELS in plan.successfulSections)
        assertFalse(CatalogSection.LIVE_CHANNELS in plan.authoritativeSections)
        assertFalse(CatalogSection.LIVE_CATEGORIES in plan.authoritativeSections)
        assertEquals("PARTIAL", plan.state)
    }

    @Test fun `failed category endpoint preserves deterministic relation in otherwise usable streams`() = runBlocking {
        val payload = loader(client(mapOf(
            "get_live_categories" to (503 to "unavailable"),
            "get_live_streams" to (200 to """[{"stream_id":7,"category_id":"1"}]"""),
        ))).load(source, credential)
        assertEquals(SectionStatus.FAILED, payload.liveCategories.status)
        assertEquals(StableIdentity.categoryId("source", "LIVE", "1"), payload.liveChannels.value?.single()?.categoryKey)
        assertEquals("PARTIAL", RefreshPolicy.plan(0, payload).state)
    }

    @Test fun `failed content endpoint never retires its cached category hierarchy`() = runBlocking {
        val payload = loader(client(mapOf(
            "get_live_categories" to (200 to "[]"),
            "get_live_streams" to (503 to "unavailable"),
        ))).load(source, credential)
        val plan = RefreshPolicy.plan(5, payload)
        assertFalse(CatalogSection.LIVE_CATEGORIES in plan.authoritativeSections)
        assertFalse(CatalogSection.LIVE_CHANNELS in plan.successfulSections)
    }

    @Test fun `incomplete category recovery does not invent a unique membership`() = runBlocking {
        val payload = loader(client(mapOf(
            "get_live_categories" to (200 to """[{"category_id":"1"},{"category_id":"2"}]"""),
            "get_live_streams" to (200 to """[{"stream_id":7,"category_id":null}]"""),
            "get_live_streams:1" to (200 to """[{"stream_id":7}]"""),
            "get_live_streams:2" to (503 to "unavailable"),
        ))).load(source, credential)
        assertEquals(SectionStatus.PARTIAL, payload.liveChannels.status)
        assertNull(payload.liveChannels.value?.single()?.categoryKey)
    }

    @Test fun `truncated JSON and all-invalid rows are failed inventories`() = runBlocking {
        for (body in listOf("""[{"stream_id":7}""", "[null,{},4]")) {
            val payload = loader(client(mapOf("get_live_streams" to (200 to body)))).load(source, credential)
            assertEquals(SectionStatus.FAILED, payload.liveChannels.status)
            assertFalse(CatalogSection.LIVE_CHANNELS in RefreshPolicy.plan(5, payload).authoritativeSections)
        }
    }

    @Test fun `malformed optional metadata leaves a valid record usable`() = runBlocking {
        val result = client(mapOf("get_vod_streams" to (200 to
            """[{"stream_id":7,"name":{},"stream_icon":[],"rating":{},"num":"bad"}]"""
        ))).vodStreams(source.baseLocator, credential) as XtreamResult.Success
        assertNull(result.warningCode)
        assertEquals("7", result.value.single().streamId)
        assertEquals("Untitled movie", result.value.single().name)
    }

    @Test fun `malformed series containers fail but explicit empty collections are valid`() = runBlocking {
        for (body in listOf("{}", """{"episodes":null}""", """{"episodes":"bad"}""", """{"episodes":{"1":[{}]}}""")) {
            assertTrue(client(mapOf("get_series_info" to (200 to body)))
                .seriesInfo(source.baseLocator, credential, "8") is XtreamResult.Failure)
        }
        for (body in listOf("""{"episodes":{}}""", """{"episodes":[]}""")) {
            val result = client(mapOf("get_series_info" to (200 to body)))
                .seriesInfo(source.baseLocator, credential, "8") as XtreamResult.Success
            assertNull(result.warningCode)
            assertTrue(result.value.episodes.isEmpty())
        }
    }

    @Test fun `partial episodes retain valid metadata and report incomplete inventory`() = runBlocking {
        val result = client(mapOf("get_series_info" to (200 to
            """{"info":{"plot":"Plot"},"episodes":{"1":[{"id":9,"title":"Episode"},null],"2":"bad"}}"""
        ))).seriesInfo(source.baseLocator, credential, "8") as XtreamResult.Success
        assertEquals("XTREAM_PARTIAL_EPISODES", result.warningCode)
        assertEquals("9", result.value.episodes.single().episodeId)
        assertEquals("Plot", result.value.metadata?.plot)
    }

    @Test fun `M3U structural loss retains usable rows without authorizing retirement`() = runBlocking {
        for (ending in listOf("#EXTINF:-1,Missing", "#EXTINF broken", "<html>error</html>")) {
            val payload = m3u("#EXTM3U\n#EXTINF:-1 tvg-id=\"a\",A\nhttp://fixture.invalid/a\n$ending")
            assertEquals(SectionStatus.PARTIAL, payload.liveChannels.status)
            assertEquals(1, payload.liveChannels.value?.size)
            assertFalse(CatalogSection.LIVE_CHANNELS in RefreshPolicy.plan(3, payload).authoritativeSections)
            assertFalse(CatalogSection.LIVE_CATEGORIES in RefreshPolicy.plan(3, payload).authoritativeSections)
        }
    }

    @Test fun `M3U malformed-only content fails and explicit empty playlist remains valid`() = runBlocking {
        for (body in listOf("", "<html>error</html>", "#EXTM3U\n#EXTINF:-1,Missing")) {
            assertEquals(SectionStatus.FAILED, m3u(body).liveChannels.status)
        }
        assertEquals(SectionStatus.SUCCESS, m3u("#EXTM3U\n").liveChannels.status)
    }

    @Test fun `repeated EXTINF records report the missing stream instead of hiding the loss`() = runBlocking {
        val payload = m3u("#EXTM3U\n#EXTINF:-1,Missing\n#EXTINF:-1,Good\nhttp://fixture.invalid/good")
        assertEquals(SectionStatus.PARTIAL, payload.liveChannels.status)
        assertEquals("Good", payload.liveChannels.value?.single()?.name)
    }

    private suspend fun m3u(body: String): ProviderRefreshPayload = loader(client(emptyMap()), body)
        .load(source.copy(type = SourceType.M3U), SourceCredential.M3uRemoteLocator("http://fixture.invalid/list.m3u"))

    private fun loader(client: OkHttpXtreamClient, playlist: String = "#EXTM3U") = SourceCatalogLoader(
        client, object : M3uClient { override suspend fun fetch(remoteLocator: String) = M3uResult.Success(playlist) }, M3uParser(),
    )

    private fun client(responses: Map<String, Pair<Int, String>>): OkHttpXtreamClient {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val action = request.url.queryParameter("action").orEmpty()
            val category = request.url.queryParameter("category_id")
            val response = responses["$action:$category"] ?: responses[action] ?: (200 to "[]")
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(response.first)
                .message("Fixture").body(response.second.toResponseBody()).build()
        }.build()
        clients += http
        return OkHttpXtreamClient(ProviderHttpTransport(http))
    }
}
