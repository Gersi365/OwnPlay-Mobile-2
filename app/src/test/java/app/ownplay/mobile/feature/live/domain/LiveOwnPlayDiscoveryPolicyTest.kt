package app.ownplay.mobile.feature.live.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveOwnPlayDiscoveryPolicyTest {
    @Test
    fun `technical quality tokens do not become semantic identity`() {
        assertEquals(
            "IT DAZN SERIE A 1",
            LiveOwnPlayDiscoveryPolicy.normalizedIdentity("IT | DAZN Serie A 1 FHD HEVC"),
        )
        assertEquals(
            "ITALIA CINEMA",
            LiveOwnPlayDiscoveryPolicy.normalizedIdentity("Italia • Cinema • 4K HDR"),
        )
    }

    @Test
    fun `country first discovery emits only categories backed by real memberships`() {
        val result = LiveOwnPlayDiscoveryPolicy.discover(
            listOf(
                channel("rai-1", "Italy FHD", "Rai 1 FHD"),
                channel("dazn-1", "Italy HEVC", "DAZN Serie A 1 FHD"),
                channel("cinema-1", "Italy HD", "Sky Cinema Uno HD"),
            ),
        )

        assertEquals(
            setOf(
                "ownplay:country:IT",
                "ownplay:country:IT:semantic:SPORT",
                "ownplay:country:IT:semantic:DAZN",
                "ownplay:country:IT:semantic:SERIE_A",
                "ownplay:country:IT:semantic:FILM",
            ),
            result.categories.map { it.categoryId }.toSet(),
        )
        assertFalse(result.categories.any { it.semanticKey == "SERIE_B" })
        assertEquals(
            "ownplay:country:IT",
            result.categories.single { it.semanticKey == "SPORT" }.parentCategoryId,
        )
        assertEquals(
            "ownplay:country:IT:semantic:SPORT",
            result.categories.single { it.semanticKey == "DAZN" }.parentCategoryId,
        )
        assertEquals(
            "ownplay:country:IT:semantic:SPORT",
            result.categories.single { it.semanticKey == "SERIE_A" }.parentCategoryId,
        )
        assertEquals(
            setOf(
                "ownplay:country:IT",
                "ownplay:country:IT:semantic:SPORT",
                "ownplay:country:IT:semantic:DAZN",
                "ownplay:country:IT:semantic:SERIE_A",
            ),
            result.memberships
                .filter { it.channelId == "dazn-1" }
                .map { it.categoryId }
                .toSet(),
        )
        assertEquals(listOf("rai-1"), result.unclassifiedChannelIds)
    }

    @Test
    fun `high confidence decorative rows become section markers and are not channels`() {
        val result = LiveOwnPlayDiscoveryPolicy.discover(
            listOf(
                channel("sport-marker", "Italia FHD", "----- SPORT -----"),
                channel("sport-channel", "Italia FHD", "Rai Sport Uno"),
                channel("film-marker", "Italia FHD", "***** FILM *****"),
                channel("film-channel", "Italia FHD", "Canale Cinema Due"),
            ),
        )

        assertEquals(setOf("sport-marker", "film-marker"), result.automaticMarkerChannelIds)
        assertTrue(result.reviewMarkerCandidates.isEmpty())
        assertFalse(result.memberships.any { it.channelId == "sport-marker" })
        assertFalse(result.memberships.any { it.channelId == "film-marker" })
        assertTrue(
            result.memberships.any {
                it.channelId == "sport-channel" &&
                    it.categoryId == "ownplay:country:IT:semantic:SPORT"
            },
        )
        assertTrue(
            result.memberships.any {
                it.channelId == "film-channel" &&
                    it.categoryId == "ownplay:country:IT:semantic:FILM"
            },
        )
    }

    @Test
    fun `medium confidence marker stays review only and is not auto hidden`() {
        val result = LiveOwnPlayDiscoveryPolicy.discover(
            listOf(
                channel(
                    id = "news-candidate",
                    providerCategory = "Italy HD",
                    name = "---- NEWS ----",
                    tvgId = "news-candidate.example",
                    hasLogo = true,
                ),
            ),
        )

        assertTrue(result.automaticMarkerChannelIds.isEmpty())
        assertEquals(1, result.reviewMarkerCandidates.size)
        assertEquals("news-candidate", result.reviewMarkerCandidates.single().channelId)
        assertEquals("NEWS", result.reviewMarkerCandidates.single().semanticKey)
        assertEquals(LiveClassificationConfidence.MEDIUM, result.reviewMarkerCandidates.single().confidence)
        assertTrue(result.memberships.any { it.channelId == "news-candidate" })
        assertEquals(listOf("news-candidate"), result.unclassifiedChannelIds)
    }

    @Test
    fun `country must be known before semantic categories are emitted`() {
        val result = LiveOwnPlayDiscoveryPolicy.discover(
            listOf(
                channel("mixed-dazn", "Premium FHD", "DAZN Serie A 1 FHD"),
                channel("mixed-cinema", null, "Cinema Uno HD"),
            ),
        )

        assertTrue(result.categories.isEmpty())
        assertTrue(result.memberships.isEmpty())
        assertEquals(listOf("mixed-dazn", "mixed-cinema"), result.unclassifiedChannelIds)
    }

    @Test
    fun `marker scope resets when provider category changes`() {
        val result = LiveOwnPlayDiscoveryPolicy.discover(
            listOf(
                channel("sport-marker", "Italy FHD", "----- SPORT -----"),
                channel("sport-1", "Italy FHD", "Channel One"),
                channel("general-1", "Italy HEVC", "Channel Two"),
            ),
        )

        assertTrue(
            result.memberships.any {
                it.channelId == "sport-1" &&
                    it.categoryId == "ownplay:country:IT:semantic:SPORT"
            },
        )
        assertFalse(
            result.memberships.any {
                it.channelId == "general-1" &&
                    it.categoryId == "ownplay:country:IT:semantic:SPORT"
            },
        )
        assertEquals(listOf("general-1"), result.unclassifiedChannelIds)
    }

    @Test
    fun `source profile can confirm or reject marker interpretation deterministically`() {
        val forcedMarkerResult = LiveOwnPlayDiscoveryPolicy.discover(
            channels = listOf(
                channel("profile-marker", "Italy HD", "My Sports Section"),
                channel("after-marker", "Italy HD", "Channel One"),
            ),
            profile = LiveOwnPlayDiscoveryProfile(
                forcedMarkers = mapOf("profile-marker" to "SPORT"),
            ),
        )

        assertEquals(setOf("profile-marker"), forcedMarkerResult.automaticMarkerChannelIds)
        assertTrue(
            forcedMarkerResult.memberships.any {
                it.channelId == "after-marker" &&
                    it.categoryId == "ownplay:country:IT:semantic:SPORT"
            },
        )

        val forcedNormalResult = LiveOwnPlayDiscoveryPolicy.discover(
            channels = listOf(
                channel("normal-row", "Italy HD", "----- SPORT -----"),
                channel("after-normal", "Italy HD", "Channel Two"),
            ),
            profile = LiveOwnPlayDiscoveryProfile(
                forcedNormalChannels = setOf("normal-row"),
            ),
        )

        assertTrue(forcedNormalResult.automaticMarkerChannelIds.isEmpty())
        assertFalse(
            forcedNormalResult.memberships.any {
                it.channelId == "after-normal" &&
                    it.categoryId == "ownplay:country:IT:semantic:SPORT"
            },
        )
        assertEquals(listOf("normal-row", "after-normal"), forcedNormalResult.unclassifiedChannelIds)
    }

    @Test
    fun `empty input stays empty`() {
        val result = LiveOwnPlayDiscoveryPolicy.discover(emptyList())

        assertTrue(result.categories.isEmpty())
        assertTrue(result.memberships.isEmpty())
        assertTrue(result.automaticMarkerChannelIds.isEmpty())
        assertTrue(result.reviewMarkerCandidates.isEmpty())
        assertTrue(result.unclassifiedChannelIds.isEmpty())
    }

    private fun channel(
        id: String,
        providerCategory: String?,
        name: String,
        tvgId: String? = null,
        hasLogo: Boolean = false,
    ) = LiveOwnPlayDiscoveryChannel(
        channelId = id,
        providerCategoryName = providerCategory,
        name = name,
        tvgId = tvgId,
        hasLogo = hasLogo,
    )
}
