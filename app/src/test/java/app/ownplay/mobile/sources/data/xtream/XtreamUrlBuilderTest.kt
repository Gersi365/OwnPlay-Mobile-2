package app.ownplay.mobile.sources.data.xtream

import app.ownplay.mobile.sources.domain.SourceCredential
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamUrlBuilderTest {
    @Test
    fun normalizesPlayerApiSuffix() {
        val normalized = XtreamUrlBuilder.normalizeBaseUrl("https://provider.test/panel/player_api.php")
        assertTrue(normalized == "https://provider.test/panel")
    }

    @Test
    fun redactionRemovesCredentialsFromApiUrl() {
        val credential = SourceCredential.Xtream("gersi@example.com", "secret value")
        val url = XtreamUrlBuilder.apiUrl("https://provider.test", credential, "get_live_streams")
        val redacted = XtreamUrlBuilder.redact(url)

        assertFalse(redacted.contains("gersi", ignoreCase = true))
        assertFalse(redacted.contains("secret", ignoreCase = true))
        assertTrue(redacted.contains("<redacted>"))
    }

    @Test
    fun buildsExtensionSpecificLiveCandidates() {
        val credential = SourceCredential.Xtream("user", "pass")
        val ts = XtreamUrlBuilder.streamUrl("http://provider.test:8080", credential, "live", "42", "ts")
        val hls = XtreamUrlBuilder.streamUrl("http://provider.test:8080", credential, "live", "42", "m3u8")

        assertTrue(ts.endsWith("/42.ts"))
        assertTrue(hls.endsWith("/42.m3u8"))
    }

    @Test
    fun redactionRemovesCredentialsFromStreamPath() {
        val credential = SourceCredential.Xtream("user", "pass")
        val url = XtreamUrlBuilder.streamUrl("https://provider.test", credential, "live", "42", "ts")
        val redacted = XtreamUrlBuilder.redact(url)

        assertFalse(redacted.contains("/user/pass/"))
        assertTrue(redacted.contains("/<redacted>/<redacted>/"))
    }
}
