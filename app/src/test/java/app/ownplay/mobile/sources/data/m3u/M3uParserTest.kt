package app.ownplay.mobile.sources.data.m3u

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {
    private val parser = M3uParser()

    @Test
    fun parsesQuotedMetadataUnicodeAndCommaInDisplayName() {
        val input = """
            \uFEFF#EXTM3U
            #EXTINF:-1 tvg-id="news-24" tvg-name="News 24" tvg-logo="https://img.test/logo.png" group-title="Lajme",News, Shqip
            https://stream.test/live.m3u8
        """.trimIndent().replace("\\uFEFF", "\uFEFF")

        val result = parser.parse(input)

        assertEquals(1, result.entries.size)
        val entry = result.entries.single()
        assertEquals("news-24", entry.tvgId)
        assertEquals("Lajme", entry.groupTitle)
        assertEquals("News, Shqip", entry.displayName)
        assertEquals("https://stream.test/live.m3u8", entry.streamLocator)
    }

    @Test
    fun malformedEntryDoesNotDiscardFollowingEntries() {
        val input = """
            #EXTM3U
            #EXTINF:-1 tvg-id="broken" Broken entry without comma
            #EXTINF:-1 group-title="Sports",Sports One
            https://stream.test/sports.m3u8
        """.trimIndent()

        val result = parser.parse(input)

        assertEquals(1, result.entries.size)
        assertEquals("Sports One", result.entries.single().displayName)
        assertTrue(result.diagnostics.isNotEmpty())
    }

    @Test
    fun supportsCrLfBlankLinesAndComments() {
        val input = "#EXTM3U\r\n\r\n# comment\r\n#EXTINF:-1,Channel\r\nhttps://stream.test/a\r\n"
        val result = parser.parse(input)
        assertEquals(1, result.entries.size)
    }

    @Test
    fun orphanLocatorIsDiagnosedWithoutCreatingEntry() {
        val result = parser.parse("#EXTM3U\nhttps://stream.test/orphan")
        assertTrue(result.entries.isEmpty())
        assertEquals("ORPHAN_STREAM_LOCATOR", result.diagnostics.single().code)
    }
}
