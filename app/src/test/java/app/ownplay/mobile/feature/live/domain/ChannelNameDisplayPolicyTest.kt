package app.ownplay.mobile.feature.live.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelNameDisplayPolicyTest {
    @Test
    fun `keeps raw display name when prefix hiding is disabled`() {
        assertEquals("AL | Top Channel", ChannelNameDisplayPolicy.displayName(" AL | Top Channel ", false))
    }

    @Test
    fun `removes arbitrary provider prefix and separator whitespace`() {
        assertEquals("Rai 1", ChannelNameDisplayPolicy.displayName("ITALY |   Rai 1", true))
        assertEquals("BBC One", ChannelNameDisplayPolicy.displayName("UK HD| BBC One", true))
    }

    @Test
    fun `keeps malformed or empty prefix forms unchanged`() {
        assertEquals("| Channel", ChannelNameDisplayPolicy.displayName("| Channel", true))
        assertEquals("ITALY |", ChannelNameDisplayPolicy.displayName("ITALY |", true))
        assertEquals("No Prefix", ChannelNameDisplayPolicy.displayName("No Prefix", true))
    }
}
