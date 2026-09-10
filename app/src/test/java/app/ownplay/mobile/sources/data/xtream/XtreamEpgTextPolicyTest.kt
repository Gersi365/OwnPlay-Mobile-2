package app.ownplay.mobile.sources.data.xtream

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Test

class XtreamEpgTextPolicyTest {
    @Test
    fun plainProgramTitleIsPreserved() {
        assertEquals("Evening News", XtreamEpgTextPolicy.decode(" Evening News "))
    }

    @Test
    fun base64ProgramTitleIsDecoded() {
        val encoded = Base64.getEncoder().encodeToString("UEFA Champions League".toByteArray(Charsets.UTF_8))
        assertEquals("UEFA Champions League", XtreamEpgTextPolicy.decode(encoded))
    }

    @Test
    fun utf8AndHtmlEntitiesAreDecoded() {
        val encoded = Base64.getEncoder().encodeToString("Këngë &amp; Histori".toByteArray(Charsets.UTF_8))
        assertEquals("Këngë & Histori", XtreamEpgTextPolicy.decode(encoded))
    }

    @Test
    fun malformedCandidateFallsBackToRawText() {
        assertEquals("https://example.invalid/epg", XtreamEpgTextPolicy.decode("https://example.invalid/epg"))
    }
}
