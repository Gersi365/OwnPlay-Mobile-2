package app.ownplay.mobile.playback

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackFailureDiagnosticsTest {
    @Test
    fun `network failures expose safe connection guidance`() {
        val message = PlaybackFailureDiagnostics.safeMessage(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        )
        assertTrue(message.contains("Network", ignoreCase = true))
    }

    @Test
    fun `http status failures do not expose request details`() {
        val message = PlaybackFailureDiagnostics.safeMessage(
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        )
        assertTrue(message.contains("source", ignoreCase = true))
        assertTrue(!message.contains("http://", ignoreCase = true))
        assertTrue(!message.contains("https://", ignoreCase = true))
    }

    @Test
    fun `unsupported parsing failures identify stream format`() {
        val message = PlaybackFailureDiagnostics.safeMessage(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        )
        assertTrue(message.contains("format", ignoreCase = true))
    }

    @Test
    fun `decoder failures identify device decode limitation`() {
        val message = PlaybackFailureDiagnostics.safeMessage(
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        )
        assertTrue(message.contains("decode", ignoreCase = true))
    }
}
