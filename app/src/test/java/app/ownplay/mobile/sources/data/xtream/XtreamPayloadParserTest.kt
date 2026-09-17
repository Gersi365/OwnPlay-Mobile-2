package app.ownplay.mobile.sources.data.xtream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XtreamPayloadParserTest {
    @Test
    fun `categories preserve provider array order`() {
        val categories = XtreamPayloadParser.categories(
            """
            [
              {"category_id":"20","category_name":"Albania"},
              {"category_id":"10","category_name":"Italia"}
            ]
            """.trimIndent(),
        )

        assertEquals(listOf(0, 1), categories.map { it.providerOrder })
        assertEquals(listOf("20", "10"), categories.map { it.providerCategoryId })
    }

    @Test
    fun `live streams accept numeric stream id and skip incomplete rows`() {
        val streams = XtreamPayloadParser.liveStreams(
            """
            [
              {"stream_id":42,"name":"News HD","category_id":"7","epg_channel_id":"news.al"},
              {"stream_id":43,"category_id":"7"}
            ]
            """.trimIndent(),
        )

        assertEquals(1, streams.size)
        assertEquals("42", streams.single().streamId)
        assertEquals("news.al", streams.single().tvgId)
    }

    @Test
    fun `missing optional movie metadata remains null`() {
        val movies = XtreamPayloadParser.movies(
            """[{"stream_id":"5","name":"Movie"}]""",
        )

        assertEquals(1, movies.size)
        assertNull(movies.single().categoryId)
        assertNull(movies.single().posterUrl)
    }
}
