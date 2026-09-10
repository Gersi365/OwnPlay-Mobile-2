package app.ownplay.mobile.playback.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioFormatLabelPolicyTest {
    @Test
    fun `describes common IPTV audio formats without provider data`() {
        assertEquals("MPEG Layer II", AudioFormatLabelPolicy.describe("audio/mpeg-L2", null))
        assertEquals("AC-3 (ac-3)", AudioFormatLabelPolicy.describe("audio/ac3", "ac-3"))
        assertEquals("E-AC-3", AudioFormatLabelPolicy.describe("audio/eac3", null))
        assertEquals("audio/example", AudioFormatLabelPolicy.describe("audio/example", null))
        assertEquals("unknown format", AudioFormatLabelPolicy.describe(null, null))
    }
}
