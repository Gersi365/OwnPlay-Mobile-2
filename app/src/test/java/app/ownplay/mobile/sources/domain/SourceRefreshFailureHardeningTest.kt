package app.ownplay.mobile.sources.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SourceRefreshFailureHardeningTest {
    @Test fun malformedM3uPayloadUsesSafeFormatFailure() {
        val error = SourceRefreshFailurePolicy.present("M3U_PARSE_CONTENT")
        assertEquals("REFRESH_M3U_FORMAT", error.code)
        assertFalse(error.safeMessage.contains("http://"))
        assertFalse(error.safeMessage.contains("https://"))
    }

    @Test fun unusableXtreamRowsRemainXtreamFormatFailure() {
        assertEquals(
            "REFRESH_XTREAM_FORMAT",
            SourceRefreshFailurePolicy.present("XTREAM_ARRAY_CONTENT").code,
        )
    }
}
