package app.ownplay.mobile.feature.live.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveOwnPlayManualEditPolicyTest {
    @Test
    fun addIncludesTargetPathAndProtectsItsCategories() {
        val plan = LiveOwnPlayManualEditPolicy.membershipPlan(
            snapshot = snapshot(),
            targetCategoryId = FOOTBALL,
            channelIds = listOf("channel-a", "channel-a"),
            mode = LiveOwnPlayMembershipEditMode.ADD,
        )!!

        assertEquals(setOf(COUNTRY, SPORT, FOOTBALL), plan.protectedCategoryIds)
        assertEquals(
            listOf(COUNTRY, SPORT, FOOTBALL),
            plan.membershipChanges.map { it.categoryId },
        )
        assertTrue(plan.membershipChanges.all { it.included })
    }

    @Test
    fun replaceExcludesOtherMembershipsAndKeepsTargetPath() {
        val source = snapshot(
            memberships = listOf(
                membership(COUNTRY, "channel-a"),
                membership(FILM, "channel-a"),
                membership(SPORT, "channel-a"),
            ),
        )

        val plan = LiveOwnPlayManualEditPolicy.membershipPlan(
            snapshot = source,
            targetCategoryId = FOOTBALL,
            channelIds = listOf("channel-a"),
            mode = LiveOwnPlayMembershipEditMode.REPLACE,
        )!!
        val byCategory = plan.membershipChanges.associateBy { it.categoryId }

        assertEquals(false, byCategory.getValue(FILM).included)
        assertEquals(true, byCategory.getValue(COUNTRY).included)
        assertEquals(true, byCategory.getValue(SPORT).included)
        assertEquals(true, byCategory.getValue(FOOTBALL).included)
        assertEquals(
            setOf(LiveOwnPlayManualOverrideEvidence.REPLACE_MEMBERSHIPS),
            byCategory.getValue(FOOTBALL).evidenceKeys,
        )

        val persisted = snapshot(
            memberships = plan.membershipChanges.map { change ->
                membership(
                    categoryId = change.categoryId,
                    channelId = change.channelId,
                    included = change.included,
                    evidence = change.evidenceKeys,
                    origin = LiveOrganizationOrigin.MANUAL,
                )
            },
        )
        val constraints = LiveOwnPlayManualEditPolicy.refreshConstraints(persisted)
        assertEquals(setOf("channel-a"), constraints.replaceLockedChannelIds)
        assertFalseAutomaticMembership(
            channelId = "channel-a",
            categoryId = FILM,
            constraints = constraints,
        )
    }

    @Test
    fun removeExcludesTargetSubtreeSoAutoRefreshCannotReAddChildren() {
        val plan = LiveOwnPlayManualEditPolicy.membershipPlan(
            snapshot = snapshot(),
            targetCategoryId = SPORT,
            channelIds = listOf("channel-a"),
            mode = LiveOwnPlayMembershipEditMode.REMOVE,
        )!!

        assertEquals(emptySet<String>(), plan.protectedCategoryIds)
        assertEquals(
            setOf(SPORT, FOOTBALL),
            plan.membershipChanges.map { it.categoryId }.toSet(),
        )
        assertTrue(plan.membershipChanges.none { it.included })
        assertEquals(
            setOf(LiveOwnPlayManualOverrideEvidence.excludedSubtree(SPORT)),
            plan.membershipChanges.single { it.categoryId == SPORT }.evidenceKeys,
        )

        val persisted = snapshot(
            memberships = plan.membershipChanges.map { change ->
                membership(
                    categoryId = change.categoryId,
                    channelId = change.channelId,
                    included = change.included,
                    evidence = change.evidenceKeys,
                    origin = LiveOrganizationOrigin.MANUAL,
                )
            },
        )
        val constraints = LiveOwnPlayManualEditPolicy.refreshConstraints(persisted)
        assertFalseAutomaticMembership("channel-a", SPORT, constraints)
        assertFalseAutomaticMembership("channel-a", FOOTBALL, constraints)
        assertTrue(
            LiveOwnPlayManualEditPolicy.allowsAutomaticMembership(
                channelId = "channel-a",
                categoryId = FILM,
                discoveredCategories = discoveredCategories(),
                constraints = constraints,
            ),
        )
    }

    @Test
    fun markerTreatmentRejectsCountryAndPersistsSemanticProfileEvidence() {
        assertNull(
            LiveOwnPlayManualEditPolicy.treatmentPlan(
                snapshot = snapshot(),
                targetCategoryId = COUNTRY,
                channelIds = listOf("marker-row"),
                treatment = LiveOwnPlayChannelTreatment.SECTION_MARKER,
            ),
        )

        val plan = LiveOwnPlayManualEditPolicy.treatmentPlan(
            snapshot = snapshot(
                memberships = listOf(
                    membership(COUNTRY, "marker-row"),
                    membership(FILM, "marker-row"),
                ),
            ),
            targetCategoryId = FILM,
            channelIds = listOf("marker-row"),
            treatment = LiveOwnPlayChannelTreatment.SECTION_MARKER,
        )!!
        val marker = plan.membershipChanges.single { it.categoryId == FILM }
        assertEquals(false, marker.included)
        assertEquals(setOf("manual-profile:section-marker:FILM"), marker.evidenceKeys)

        val persisted = snapshot(
            memberships = plan.membershipChanges.map { change ->
                membership(
                    categoryId = change.categoryId,
                    channelId = change.channelId,
                    included = change.included,
                    evidence = change.evidenceKeys,
                    origin = LiveOrganizationOrigin.MANUAL,
                )
            },
        )
        assertEquals(
            mapOf("marker-row" to "FILM"),
            LiveOwnPlayManualEditPolicy.discoveryProfile(persisted).forcedMarkers,
        )
    }

    @Test
    fun normalTreatmentIsImmediateMembershipAndFutureClassifierOverride() {
        val plan = LiveOwnPlayManualEditPolicy.treatmentPlan(
            snapshot = snapshot(),
            targetCategoryId = FILM,
            channelIds = listOf("title-row"),
            treatment = LiveOwnPlayChannelTreatment.NORMAL_CHANNEL,
        )!!
        val target = plan.membershipChanges.single { it.categoryId == FILM }
        assertTrue(target.included)
        assertEquals(setOf(LiveOwnPlayManualOverrideEvidence.NORMAL_CHANNEL), target.evidenceKeys)

        val profile = LiveOwnPlayManualEditPolicy.discoveryProfile(
            snapshot(
                memberships = listOf(
                    membership(
                        FILM,
                        "title-row",
                        evidence = target.evidenceKeys,
                        origin = LiveOrganizationOrigin.MANUAL,
                    ),
                ),
            ),
        )
        assertEquals(setOf("title-row"), profile.forcedNormalChannels)
    }

    private fun snapshot(
        memberships: List<LiveChannelMembership> = emptyList(),
    ) = LiveOrganizationSnapshot(
        sourceId = SOURCE,
        activeMode = LiveOrganizationMode.OWNPLAY,
        categories = listOf(
            category(COUNTRY, "Italy", semanticKey = "COUNTRY"),
            category(FILM, "Film", parent = COUNTRY, semanticKey = "FILM"),
            category(SPORT, "Sport", parent = COUNTRY, semanticKey = "SPORT"),
            category(FOOTBALL, "Football", parent = SPORT, semanticKey = "FOOTBALL"),
        ),
        memberships = memberships,
    )

    private fun category(
        id: String,
        name: String,
        parent: String? = null,
        semanticKey: String,
    ) = LiveOrganizationCategory(
        sourceId = SOURCE,
        mode = LiveOrganizationMode.OWNPLAY,
        categoryId = id,
        parentCategoryId = parent,
        displayName = name,
        semanticKey = semanticKey,
        origin = LiveOrganizationOrigin.AUTO,
    )

    private fun membership(
        categoryId: String,
        channelId: String,
        included: Boolean = true,
        evidence: Set<String> = emptySet(),
        origin: LiveOrganizationOrigin = LiveOrganizationOrigin.AUTO,
    ) = LiveChannelMembership(
        sourceId = SOURCE,
        mode = LiveOrganizationMode.OWNPLAY,
        categoryId = categoryId,
        channelId = channelId,
        included = included,
        origin = origin,
        evidenceKeys = evidence,
    )

    private fun assertFalseAutomaticMembership(
        channelId: String,
        categoryId: String,
        constraints: LiveOwnPlayRefreshConstraints,
    ) {
        assertEquals(
            false,
            LiveOwnPlayManualEditPolicy.allowsAutomaticMembership(
                channelId = channelId,
                categoryId = categoryId,
                discoveredCategories = discoveredCategories(),
                constraints = constraints,
            ),
        )
    }

    private fun discoveredCategories() = listOf(
        LiveOwnPlayDiscoveryCategory(
            categoryId = COUNTRY,
            parentCategoryId = null,
            displayName = "Italy",
            semanticKey = "COUNTRY",
            confidence = LiveClassificationConfidence.HIGH,
            evidenceKeys = emptySet(),
        ),
        LiveOwnPlayDiscoveryCategory(
            categoryId = FILM,
            parentCategoryId = COUNTRY,
            displayName = "Film",
            semanticKey = "FILM",
            confidence = LiveClassificationConfidence.HIGH,
            evidenceKeys = emptySet(),
        ),
        LiveOwnPlayDiscoveryCategory(
            categoryId = SPORT,
            parentCategoryId = COUNTRY,
            displayName = "Sport",
            semanticKey = "SPORT",
            confidence = LiveClassificationConfidence.HIGH,
            evidenceKeys = emptySet(),
        ),
        LiveOwnPlayDiscoveryCategory(
            categoryId = FOOTBALL,
            parentCategoryId = SPORT,
            displayName = "Football",
            semanticKey = "FOOTBALL",
            confidence = LiveClassificationConfidence.HIGH,
            evidenceKeys = emptySet(),
        ),
    )

    private companion object {
        const val SOURCE = "source-a"
        const val COUNTRY = "ownplay:country:IT"
        const val FILM = "ownplay:country:IT:semantic:FILM"
        const val SPORT = "ownplay:country:IT:semantic:SPORT"
        const val FOOTBALL = "ownplay:country:IT:semantic:FOOTBALL"
    }
}
