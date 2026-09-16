package app.ownplay.mobile.feature.live.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveOrganizationPresentationPolicyTest {
    @Test
    fun browseTabsExposeHierarchyWithoutDuplicatingMembershipIdentity() {
        val snapshot = ownPlaySnapshot(
            categories = listOf(
                category("country", "Italy"),
                category("sport", "Sport", parent = "country"),
                category("football", "Football", parent = "sport"),
            ),
            memberships = listOf(
                membership("country", "rai-1"),
                membership("country", "dazn-1"),
                membership("sport", "dazn-1"),
                membership("football", "dazn-1"),
            ),
        )

        val tabs = LiveOrganizationPresentationPolicy.browseTabs(snapshot)

        assertEquals(listOf("country", "sport", "football"), tabs.map { it.categoryId })
        assertEquals("Italy", tabs[0].label)
        assertEquals("Italy › Sport", tabs[1].label)
        assertEquals("Italy › Sport › Football", tabs[2].label)
        assertEquals(listOf("dazn-1"), tabs[2].channelIds)
    }


    @Test
    fun managementCategoriesKeepHiddenAndEmptyRowsEditable() {
        val snapshot = ownPlaySnapshot(
            categories = listOf(
                category("country", "Italy"),
                category("film", "Film", parent = "country", hidden = true),
            ),
            memberships = listOf(membership("country", "rai-1")),
        )

        val rows = LiveOrganizationPresentationPolicy.managementCategories(snapshot)

        assertEquals(listOf("country", "film"), rows.map { it.category.categoryId })
        assertEquals(listOf(0, 1), rows.map { it.depth })
        assertEquals(0, rows.single { it.category.categoryId == "film" }.includedChannelCount)
        assertTrue(rows.single { it.category.categoryId == "film" }.category.hidden)
    }

    @Test
    fun reviewSummarizesHierarchyConfidenceCountsAndEvidence() {
        val snapshot = ownPlaySnapshot(
            categories = listOf(
                category("country", "Italy"),
                category("film", "Film", parent = "country"),
            ),
            memberships = listOf(
                membership(
                    categoryId = "country",
                    channelId = "rai-1",
                    confidence = LiveClassificationConfidence.HIGH,
                    evidence = setOf("provider-country:IT"),
                ),
                membership(
                    categoryId = "country",
                    channelId = "cine-34",
                    confidence = LiveClassificationConfidence.HIGH,
                    evidence = setOf("provider-country:IT"),
                ),
                membership(
                    categoryId = "film",
                    channelId = "cine-34",
                    confidence = LiveClassificationConfidence.MEDIUM,
                    evidence = setOf("decorative-row", "semantic:FILM"),
                ),
            ),
        )

        val review = LiveOrganizationPresentationPolicy.review(snapshot)

        assertTrue(review.available)
        assertEquals(2, review.categoryCount)
        assertEquals(2, review.classifiedChannelCount)
        assertEquals(1, review.mediumConfidenceChannelCount)
        val root = review.roots.single()
        assertEquals(2, root.totalChannelCount)
        assertEquals(LiveClassificationConfidence.HIGH, root.confidence)
        val film = root.children.single()
        assertEquals(1, film.totalChannelCount)
        assertEquals(LiveClassificationConfidence.MEDIUM, film.confidence)
        assertEquals(setOf("decorative-row", "semantic:FILM"), film.evidenceKeys)
    }

    @Test
    fun reviewIsUnavailableWithoutOwnPlayMemberships() {
        val review = LiveOrganizationPresentationPolicy.review(
            LiveOrganizationSnapshot(sourceId = SOURCE_ID),
        )

        assertFalse(review.available)
        assertEquals(0, review.categoryCount)
        assertEquals(0, review.classifiedChannelCount)
    }

    @Test
    fun evidenceCodecDecodesCoordinatorJson() {
        val expected = linkedSetOf("provider-country:IT", "semantic:FILM", "quoted\"value", "path\\value")
        val encoded = LiveOrganizationEvidencePolicy.encodeJsonArray(expected)
        val decoded = LiveOrganizationEvidencePolicy.decodeJsonArray(encoded)

        assertEquals(expected, decoded)
        assertTrue(LiveOrganizationEvidencePolicy.decodeJsonArray("not-json").isEmpty())
    }

    private fun ownPlaySnapshot(
        categories: List<LiveOrganizationCategory>,
        memberships: List<LiveChannelMembership>,
    ) = LiveOrganizationSnapshot(
        sourceId = SOURCE_ID,
        activeMode = LiveOrganizationMode.OWNPLAY,
        categories = categories,
        memberships = memberships,
    )

    private fun category(
        id: String,
        name: String,
        parent: String? = null,
        hidden: Boolean = false,
    ) = LiveOrganizationCategory(
        sourceId = SOURCE_ID,
        mode = LiveOrganizationMode.OWNPLAY,
        categoryId = id,
        parentCategoryId = parent,
        displayName = name,
        origin = LiveOrganizationOrigin.AUTO,
        hidden = hidden,
    )

    private fun membership(
        categoryId: String,
        channelId: String,
        confidence: LiveClassificationConfidence = LiveClassificationConfidence.HIGH,
        evidence: Set<String> = emptySet(),
    ) = LiveChannelMembership(
        sourceId = SOURCE_ID,
        mode = LiveOrganizationMode.OWNPLAY,
        categoryId = categoryId,
        channelId = channelId,
        origin = LiveOrganizationOrigin.AUTO,
        confidence = confidence,
        evidenceKeys = evidence,
    )

    private companion object {
        const val SOURCE_ID = "source-a"
    }
}
