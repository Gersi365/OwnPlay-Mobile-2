package app.ownplay.mobile.playback.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStreamFormatPolicyTest {
    @Test
    fun m3u8PathIsHlsEvenWithQueryParameters() {
        assertEquals(
            PlaybackStreamFormat.HLS,
            PlaybackStreamFormatPolicy.infer("https://media.example/live/channel.M3U8?token=abc"),
        )
    }

    @Test
    fun explicitHlsQueryHintSupportsOpaqueEndpoint() {
        assertEquals(
            PlaybackStreamFormat.HLS,
            PlaybackStreamFormatPolicy.infer("https://media.example/play?id=12&format=hls"),
        )
        assertEquals(
            PlaybackStreamFormat.HLS,
            PlaybackStreamFormatPolicy.infer("https://media.example/play?id=12&playlist=channel.m3u8"),
        )
    }

    @Test
    fun unrelatedQueryTextDoesNotGuessHls() {
        assertEquals(
            PlaybackStreamFormat.AUTO,
            PlaybackStreamFormatPolicy.infer("https://media.example/play?id=m3u8-channel&token=hls"),
        )
    }

    @Test
    fun ordinaryProgressiveOrTransportUrisRemainAuto() {
        assertEquals(
            PlaybackStreamFormat.AUTO,
            PlaybackStreamFormatPolicy.infer("https://media.example/movie/video.mp4"),
        )
        assertEquals(
            PlaybackStreamFormat.AUTO,
            PlaybackStreamFormatPolicy.infer("https://media.example/live/12.ts"),
        )
    }

    @Test
    fun extensionlessHttpEndpointsAreEligibleForOneShotRecovery() {
        assertTrue(PlaybackStreamFormatPolicy.isOpaqueNetworkUri("https://media.example/play?id=12"))
        assertTrue(PlaybackStreamFormatPolicy.isOpaqueNetworkUri("http://media.example/live/12345"))
    }

    @Test
    fun knownExtensionsAndLocalUrisAreNotOpaqueRecoveryCandidates() {
        assertFalse(PlaybackStreamFormatPolicy.isOpaqueNetworkUri("https://media.example/live/12.ts"))
        assertFalse(PlaybackStreamFormatPolicy.isOpaqueNetworkUri("https://media.example/movie/video.mp4"))
        assertFalse(PlaybackStreamFormatPolicy.isOpaqueNetworkUri("content://media/external/video/12"))
        assertFalse(PlaybackStreamFormatPolicy.isOpaqueNetworkUri("file:///storage/emulated/0/movie"))
    }
}
