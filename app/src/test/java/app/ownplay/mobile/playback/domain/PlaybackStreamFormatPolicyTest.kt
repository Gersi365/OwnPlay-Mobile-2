package app.ownplay.mobile.playback.domain

import org.junit.Assert.assertEquals
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
}
