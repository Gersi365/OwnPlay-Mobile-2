package app.ownplay.mobile.feature.live.domain

import app.ownplay.mobile.sources.data.m3u.M3uParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SanitizedLiveFixtureTest {
    @Test
    fun sanitizedFixtureCoversMulticountrySemanticAndNeutralFallback() {
        val content = checkNotNull(javaClass.classLoader?.getResource(
            "fixtures/sanitized/live_multicountry.m3u",
        )).readText()
        val parsed = M3uParser.parse(content)

        assertEquals(6, parsed.entries.size)
        assertEquals(0, parsed.skippedEntries)
        assertFalse(content.contains("token=", ignoreCase = true))
        assertFalse(content.contains("password", ignoreCase = true))

        val categoryIds = linkedMapOf<String, String>()
        parsed.entries.forEach { entry ->
            val group = checkNotNull(entry.groupTitle)
            categoryIds.putIfAbsent(group, "category-${categoryIds.size}")
        }
        val categories = categoryIds.entries.mapIndexed { order, (name, id) ->
            ProviderLiveCategory(id, name, order)
        }
        val channels = parsed.entries.mapIndexed { order, entry ->
            LiveOrganizationChannel(
                channelId = checkNotNull(entry.tvgId),
                name = entry.name,
                tvgName = entry.tvgName,
                providerCategoryId = categoryIds.getValue(checkNotNull(entry.groupTitle)),
                providerOrder = order,
            )
        }

        val organization = LiveOwnPlayClassifier.buildAutomaticOrganization(categories, channels)
        assertEquals(
            listOf("Albania", "Italy", "Germany", "Sweden", "Unspecified"),
            organization.countries.map { it.displayName },
        )
        assertEquals(OwnPlayLiveSemanticCategory.NEWS, organization.category("al-news-1"))
        assertEquals(OwnPlayLiveSemanticCategory.MOVIES, organization.category("it-movie-1"))
        assertEquals(OwnPlayLiveSemanticCategory.SPORTS, organization.category("de-sport-1"))
        assertEquals(OwnPlayLiveSemanticCategory.KIDS, organization.category("se-kids-1"))
        assertEquals(OwnPlayLiveSemanticCategory.GENERAL, organization.category("unknown-1"))
        assertEquals(OwnPlayLiveSemanticCategory.GENERAL, organization.category("unknown-2"))
    }

    private fun AutomaticLiveOrganization.category(channelId: String): OwnPlayLiveSemanticCategory =
        placementByChannelId.getValue(channelId).semanticCategory
}
