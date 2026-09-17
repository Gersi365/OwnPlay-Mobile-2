package app.ownplay.mobile.feature.playback.data

import app.ownplay.mobile.sources.domain.SourceId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePlaybackSourceResolverTest {
    @Test
    fun directM3uDiagnosticsRedactSecretBearingLocator() {
        val source = ResolvedLivePlaybackSource.M3uDirect(
            sourceId = SourceId("source"),
            channelId = "channel",
            streamLocator = "https://provider.example/live.m3u8?token=secret-token",
        )

        val rendered = source.toString()
        assertTrue(rendered.contains("<redacted>"))
        assertFalse(rendered.contains("provider.example"))
        assertFalse(rendered.contains("secret-token"))
    }

    @Test
    fun xtreamDiagnosticsRedactConnectionAndCredentials() {
        val source = ResolvedLivePlaybackSource.XtreamLive(
            sourceId = SourceId("source"),
            channelId = "channel",
            baseUrl = "https://provider.example",
            username = "private-user",
            password = "private-password",
            streamId = "42",
        )

        val rendered = source.toString()
        assertTrue(rendered.contains("<redacted>"))
        assertFalse(rendered.contains("provider.example"))
        assertFalse(rendered.contains("private-user"))
        assertFalse(rendered.contains("private-password"))
    }
}
