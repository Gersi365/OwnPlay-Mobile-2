package app.ownplay.mobile.feature.live.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LivePersonalizationPolicyTest {
    @Test
    fun `local channel names are trimmed and blank restores provider name`() {
        assertEquals("News HD", LivePersonalizationPolicy.normalizeLocalName("  News HD  "))
        assertNull(LivePersonalizationPolicy.normalizeLocalName("   "))
        assertNull(LivePersonalizationPolicy.normalizeLocalName(null))
    }

    @Test
    fun `local channel names are bounded`() {
        val value = "x".repeat(LivePersonalizationPolicy.MAX_LOCAL_NAME_LENGTH + 20)
        assertEquals(
            LivePersonalizationPolicy.MAX_LOCAL_NAME_LENGTH,
            LivePersonalizationPolicy.normalizeLocalName(value)?.length,
        )
    }
}
