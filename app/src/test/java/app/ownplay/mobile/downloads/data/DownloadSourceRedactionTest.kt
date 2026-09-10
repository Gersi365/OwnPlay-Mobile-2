package app.ownplay.mobile.downloads.data

import app.ownplay.mobile.playback.domain.PlaybackStreamFormat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadSourceRedactionTest {
    @Test
    fun resolvedDownloadSourceStringDoesNotExposeCredentialBearingUri() {
        val resolved = ResolvedDownloadSource(
            uri = "https://example.test/movie/user/secret/42.mp4",
            streamFormat = PlaybackStreamFormat.AUTO,
        )
        val rendered = resolved.toString()

        assertTrue(rendered.contains("<redacted>"))
        assertFalse(rendered.contains("user"))
        assertFalse(rendered.contains("secret"))
        assertFalse(rendered.contains("example.test"))
    }
}
