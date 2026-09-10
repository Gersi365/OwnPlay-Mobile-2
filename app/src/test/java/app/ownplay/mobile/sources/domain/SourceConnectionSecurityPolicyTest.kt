package app.ownplay.mobile.sources.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceConnectionSecurityPolicyTest {
    @Test
    fun `http provider is identified as cleartext`() {
        assertTrue(SourceConnectionSecurityPolicy.isCleartext("http://provider.test:8080"))
        assertNotNull(SourceConnectionSecurityPolicy.warning("http://provider.test:8080"))
    }

    @Test
    fun `https provider does not receive cleartext warning`() {
        assertFalse(SourceConnectionSecurityPolicy.isCleartext("https://provider.test"))
        assertNull(SourceConnectionSecurityPolicy.warning("https://provider.test"))
    }
}
