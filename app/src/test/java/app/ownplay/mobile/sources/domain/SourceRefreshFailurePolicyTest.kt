package app.ownplay.mobile.sources.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SourceRefreshFailurePolicyTest {
    @Test
    fun `network failure becomes actionable without exposing locators`() {
        val error = SourceRefreshFailurePolicy.present("NETWORK")

        assertEquals("REFRESH_NETWORK", error.code)
        assertFalse(error.safeMessage.contains("username="))
        assertFalse(error.safeMessage.contains("password="))
    }

    @Test
    fun `authentication failure takes priority in composite provider errors`() {
        val error = SourceRefreshFailurePolicy.present("NETWORK,XTREAM_AUTH")

        assertEquals("REFRESH_AUTH", error.code)
    }

    @Test
    fun `missing Xtream endpoint is distinguished from generic failure`() {
        val error = SourceRefreshFailurePolicy.present("HTTP_404")

        assertEquals("REFRESH_HTTP_404", error.code)
    }

    @Test
    fun `provider server errors are grouped safely`() {
        val error = SourceRefreshFailurePolicy.present("HTTP_503")

        assertEquals("REFRESH_PROVIDER_HTTP", error.code)
    }

    @Test
    fun `incompatible provider payload gives Xtream-specific guidance`() {
        val error = SourceRefreshFailurePolicy.present("XTREAM_ARRAY_FORMAT")

        assertEquals("REFRESH_XTREAM_FORMAT", error.code)
    }
}
