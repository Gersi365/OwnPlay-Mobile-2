package app.ownplay.mobile.feature.live.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveChannelVariantGroupingPolicyTest {
    @Test
    fun `quality variants with the same base name stay adjacent`() {
        val channels = listOf(
            channel("one-fhd", "Channel 1 FHD"),
            channel("two-hd", "Channel 2 HD"),
            channel("one-hd", "Channel 1 HD"),
            channel("three", "Channel 3"),
            channel("two-fhd", "Channel 2 FHD HEVC"),
        )

        val grouped = LiveChannelVariantGroupingPolicy.group(channels, preserveManualOrder = false)

        assertEquals(
            listOf("one-fhd", "one-hd", "two-hd", "two-fhd", "three"),
            grouped.map { it.channelId },
        )
    }

    @Test
    fun `manual order remains authoritative`() {
        val channels = listOf(
            channel("two-hd", "Channel 2 HD"),
            channel("one-fhd", "Channel 1 FHD"),
            channel("one-hd", "Channel 1 HD"),
        )

        val grouped = LiveChannelVariantGroupingPolicy.group(channels, preserveManualOrder = true)

        assertEquals(channels.map { it.channelId }, grouped.map { it.channelId })
    }

    @Test
    fun `technical suffixes are removed from grouping identity`() {
        assertEquals("KANALI 1", LiveChannelVariantGroupingPolicy.baseIdentity("Kanali 1 FHD HEVC"))
        assertEquals("KANALI 10", LiveChannelVariantGroupingPolicy.baseIdentity("Kanali 10 4K HDR"))
    }

    private fun channel(id: String, name: String) = LiveChannel(
        channelId = id,
        sourceId = "source",
        categoryKey = "category",
        name = name,
        logoUrl = null,
        sortOrder = 0,
    )
}
