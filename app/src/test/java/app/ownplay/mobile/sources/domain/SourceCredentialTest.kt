package app.ownplay.mobile.sources.domain

import org.junit.Assert.assertFalse
import org.junit.Test

class SourceCredentialTest {
    @Test
    fun secretToStringNeverContainsCredentialValues() {
        val credential = SourceCredential.Xtream("private-user", "private-password")
        val text = credential.toString()
        assertFalse(text.contains("private-user"))
        assertFalse(text.contains("private-password"))
    }

    @Test
    fun remoteLocatorToStringIsRedacted() {
        val credential = SourceCredential.M3uRemoteLocator("https://example.test/list?token=very-secret")
        assertFalse(credential.toString().contains("very-secret"))
    }
}
