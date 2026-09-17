package app.ownplay.mobile.feature.playback.data

import app.ownplay.mobile.feature.playback.domain.LivePlaybackSource
import app.ownplay.mobile.sources.data.xtream.XtreamLiveStreamIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePlaybackMediaPreparerTest {
    private val preparer = DefaultLivePlaybackMediaPreparer()

    @Test
    fun directRemoteMediaKeepsSecretLocatorPrivateAndDetectsHls() {
        val media = preparer.prepare(
            LivePlaybackSource.Direct(
                "https://provider.example/live/channel.m3u8?token=private-token",
            ),
        )

        requireNotNull(media)
        assertEquals("application/x-mpegURL", media.mimeType)
        assertTrue(media.uri.contains("private-token"))
        assertFalse(media.toString().contains("private-token"))
        assertFalse(media.toString().contains("provider.example"))
    }

    @Test
    fun xtreamUsesOnlyAuthoritativeExtensionFromOpaqueIdentity() {
        val media = preparer.prepare(
            LivePlaybackSource.Xtream(
                baseUrl = "https://provider.example",
                username = "user name",
                password = "private password",
                streamId = "42",
                opaqueStreamIdentity = XtreamLiveStreamIdentity.encode("42", "ts"),
            ),
        )

        requireNotNull(media)
        assertEquals(
            "https://provider.example/live/user%20name/private%20password/42.ts",
            media.uri,
        )
        assertNull(media.mimeType)
        assertFalse(media.toString().contains("private password"))
    }

    @Test
    fun xtreamWithoutAuthoritativeExtensionDoesNotGuessAPlaybackPath() {
        val media = preparer.prepare(
            LivePlaybackSource.Xtream(
                baseUrl = "https://provider.example",
                username = "user",
                password = "password",
                streamId = "42",
                opaqueStreamIdentity = "xtream://live/42",
            ),
        )

        assertNull(media)
    }

    @Test
    fun unsupportedDirectSchemeIsRejectedBeforeMedia3() {
        assertNull(
            preparer.prepare(
                LivePlaybackSource.Direct("file:///private/provider.ts"),
            ),
        )
    }
}
