package app.ownplay.mobile.sources.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SourceLocatorPolicyTest {
    @Test
    fun `m3u stored locator keeps only non-secret origin`() {
        val stored = SourceLocatorPolicy.redactRemoteLocator(
            "https://user:password@provider.test:8443/private/list.m3u?token=secret#fragment",
        )

        assertEquals("https://provider.test:8443", stored)
        assertFalse(stored.contains("user"))
        assertFalse(stored.contains("password"))
        assertFalse(stored.contains("private"))
        assertFalse(stored.contains("secret"))
    }

    @Test
    fun `m3u remote validation preserves encrypted playback locator`() {
        val remote = "https://provider.test/private/list.m3u?token=secret"

        assertEquals(remote, SourceLocatorPolicy.validateM3uRemote(remote))
    }
}
