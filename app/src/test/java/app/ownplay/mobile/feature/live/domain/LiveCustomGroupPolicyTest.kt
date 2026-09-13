package app.ownplay.mobile.feature.live.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveCustomGroupPolicyTest {
    @Test
    fun `custom group names are trimmed and blank is rejected`() {
        assertEquals("Sports", LiveCustomGroupPolicy.normalizeName("  Sports  "))
        assertNull(LiveCustomGroupPolicy.normalizeName("   "))
        assertNull(LiveCustomGroupPolicy.normalizeName(null))
    }

    @Test
    fun `custom group names are bounded`() {
        val value = "x".repeat(LiveCustomGroupPolicy.MAX_NAME_LENGTH + 20)
        assertEquals(
            LiveCustomGroupPolicy.MAX_NAME_LENGTH,
            LiveCustomGroupPolicy.normalizeName(value)?.length,
        )
    }
}
