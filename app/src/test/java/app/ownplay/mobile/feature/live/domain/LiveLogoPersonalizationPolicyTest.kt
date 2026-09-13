package app.ownplay.mobile.feature.live.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveLogoPersonalizationPolicyTest {
    @Test
    fun `blank logo restores provider logo`() {
        assertNull(LivePersonalizationPolicy.normalizeLocalLogo(null))
        assertNull(LivePersonalizationPolicy.normalizeLocalLogo("   "))
        assertTrue(LivePersonalizationPolicy.isValidLocalLogoInput("   "))
    }

    @Test
    fun `http and https logo urls are normalized`() {
        assertEquals(
            "https://cdn.example.com/logo.png",
            LivePersonalizationPolicy.normalizeLocalLogo("  https://cdn.example.com/logo.png  "),
        )
        assertEquals(
            "http://192.168.1.20/channel.png",
            LivePersonalizationPolicy.normalizeLocalLogo("http://192.168.1.20/channel.png"),
        )
    }

    @Test
    fun `unsafe or malformed logo urls are rejected`() {
        assertFalse(LivePersonalizationPolicy.isValidLocalLogoInput("ftp://example.com/logo.png"))
        assertFalse(LivePersonalizationPolicy.isValidLocalLogoInput("/relative/logo.png"))
        assertFalse(LivePersonalizationPolicy.isValidLocalLogoInput("https://user:secret@example.com/logo.png"))
        assertFalse(
            LivePersonalizationPolicy.isValidLocalLogoInput(
                "https://example.com/" + "x".repeat(LivePersonalizationPolicy.MAX_LOCAL_LOGO_URL_LENGTH),
            ),
        )
    }
}
