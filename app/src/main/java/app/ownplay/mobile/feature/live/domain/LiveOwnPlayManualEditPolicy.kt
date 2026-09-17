package app.ownplay.mobile.feature.live.domain

enum class LiveOwnPlayMembershipEditMode {
    ADD,
    REPLACE,
    REMOVE,
}

enum class LiveOwnPlayChannelTreatment {
    NORMAL_CHANNEL,
    SECTION_MARKER,
}

data class LiveOwnPlayManualMembershipChange(
    val categoryId: String,
    val channelId: String,
    val included: Boolean,
    val evidenceKeys: Set<String> = emptySet(),
)

data class LiveOwnPlayManualEditPlan(
    val protectedCategoryIds: Set<String>,
    val membershipChanges: List<LiveOwnPlayManualMembershipChange>,
)

data class LiveOwnPlayRefreshConstraints(
    val replaceLockedChannelIds: Set<String> = emptySet(),
    val excludedSubtreeCategoryIdsByChannel: Map<String, Set<String>> = emptyMap(),
)

object LiveOwnPlayManualOverrideEvidence {
    const val NORMAL_CHANNEL = "manual-profile:normal-channel"
    const val REPLACE_MEMBERSHIPS = "manual-profile:replace-memberships"
    const val SECTION_MARKER_PREFIX = "manual-profile:section-marker:"
    const val EXCLUDE_SUBTREE_PREFIX = "manual-profile:exclude-subtree:"

    fun sectionMarker(semanticKey: String): String = SECTION_MARKER_PREFIX + semanticKey

    fun sectionMarkerSemanticKey(evidenceKeys: Set<String>): String? = evidenceKeys
        .firstOrNull { it.startsWith(SECTION_MARKER_PREFIX) }
        ?.removePrefix(SECTION_MARKER_PREFIX)
        ?.takeIf(String::isNotBlank)

    fun excludedSubtree(categoryId: String): String = EXCLUDE_SUBTREE_PREFIX + categoryId

    fun excludedSubtreeCategoryId(evidenceKeys: Set<String>): String? = evidenceKeys
        .firstOrNull { it.startsWith(EXCLUDE_SUBTREE_PREFIX) }
        ?.removePrefix(EXCLUDE_SUBTREE_PREFIX)
        ?.takeIf(String::isNotBlank)
}

object LiveOwnPlayManualEditPolicy {
    fun membershipPlan(
        snapshot: LiveOrganizationSnapshot,
        targetCategoryId: String,
        channelIds: List<String>,
        mode: LiveOwnPlayMembershipEditMode,
        targetEvidenceKeys: Set<String> = emptySet(),
    ): LiveOwnPlayManualEditPlan? {
        val selected = channelIds.filter(String::isNotBlank).distinct()
        if (selected.isEmpty()) return null
        val categories = ownPlayCategories(snapshot)
        val categoryById = categories.associateBy { it.categoryId }
        if (targetCategoryId !in categoryById) return null
        val targetPath = categoryPath(targetCategoryId, categoryById) ?: return null
        val currentByChannel = snapshot.memberships
            .asSequence()
            .filter { it.mode == LiveOrganizationMode.OWNPLAY }
            .groupBy { it.channelId }
        val changes = linkedMapOf<Pair<String, String>, LiveOwnPlayManualMembershipChange>()

        fun put(categoryId: String, channelId: String, included: Boolean, evidence: Set<String> = emptySet()) {
            changes[categoryId to channelId] = LiveOwnPlayManualMembershipChange(
                categoryId = categoryId,
                channelId = channelId,
                included = included,
                evidenceKeys = evidence,
            )
        }

        when (mode) {
            LiveOwnPlayMembershipEditMode.ADD -> selected.forEach { channelId ->
                targetPath.forEach { categoryId ->
                    put(categoryId, channelId, true, if (categoryId == targetCategoryId) targetEvidenceKeys else emptySet())
                }
            }
            LiveOwnPlayMembershipEditMode.REPLACE -> selected.forEach { channelId ->
                currentByChannel[channelId].orEmpty()
                    .map { it.categoryId }
                    .filterNot { it in targetPath }
                    .forEach { categoryId -> put(categoryId, channelId, false) }
                targetPath.forEach { categoryId ->
                    val evidence = if (categoryId == targetCategoryId) {
                        targetEvidenceKeys + LiveOwnPlayManualOverrideEvidence.REPLACE_MEMBERSHIPS
                    } else {
                        emptySet()
                    }
                    put(categoryId, channelId, true, evidence)
                }
            }
            LiveOwnPlayMembershipEditMode.REMOVE -> {
                val subtree = categorySubtree(targetCategoryId, categories)
                selected.forEach { channelId ->
                    subtree.forEach { categoryId ->
                        val evidence = if (categoryId == targetCategoryId) {
                            setOf(LiveOwnPlayManualOverrideEvidence.excludedSubtree(targetCategoryId))
                        } else {
                            emptySet()
                        }
                        put(categoryId, channelId, false, evidence)
                    }
                }
            }
        }

        return LiveOwnPlayManualEditPlan(
            protectedCategoryIds = if (mode == LiveOwnPlayMembershipEditMode.REMOVE) emptySet() else targetPath.toSet(),
            membershipChanges = changes.values.toList(),
        )
    }

    fun treatmentPlan(
        snapshot: LiveOrganizationSnapshot,
        targetCategoryId: String,
        channelIds: List<String>,
        treatment: LiveOwnPlayChannelTreatment,
    ): LiveOwnPlayManualEditPlan? {
        return when (treatment) {
            LiveOwnPlayChannelTreatment.NORMAL_CHANNEL -> normalChannelPlan(
                snapshot = snapshot,
                targetCategoryId = targetCategoryId,
                channelIds = channelIds,
            )
            LiveOwnPlayChannelTreatment.SECTION_MARKER -> markerPlan(snapshot, targetCategoryId, channelIds)
        }
    }

    fun discoveryProfile(snapshot: LiveOrganizationSnapshot): LiveOwnPlayDiscoveryProfile {
        val manual = snapshot.memberships.filter {
            it.mode == LiveOrganizationMode.OWNPLAY && it.origin == LiveOrganizationOrigin.MANUAL
        }
        val forcedNormal = manual
            .filter { LiveOwnPlayManualOverrideEvidence.NORMAL_CHANNEL in it.evidenceKeys }
            .mapTo(linkedSetOf()) { it.channelId }
        val forcedMarkers = linkedMapOf<String, String>()
        manual.forEach { membership ->
            LiveOwnPlayManualOverrideEvidence.sectionMarkerSemanticKey(membership.evidenceKeys)
                ?.let { semanticKey -> forcedMarkers[membership.channelId] = semanticKey }
        }
        return LiveOwnPlayDiscoveryProfile(
            forcedMarkers = forcedMarkers,
            forcedNormalChannels = forcedNormal,
        )
    }

    fun refreshConstraints(snapshot: LiveOrganizationSnapshot): LiveOwnPlayRefreshConstraints {
        val manual = snapshot.memberships.filter {
            it.mode == LiveOrganizationMode.OWNPLAY && it.origin == LiveOrganizationOrigin.MANUAL
        }
        val replaceLocked = manual
            .filter { LiveOwnPlayManualOverrideEvidence.REPLACE_MEMBERSHIPS in it.evidenceKeys }
            .mapTo(linkedSetOf()) { it.channelId }
        val excluded = linkedMapOf<String, MutableSet<String>>()
        manual.forEach { membership ->
            LiveOwnPlayManualOverrideEvidence.excludedSubtreeCategoryId(membership.evidenceKeys)
                ?.let { categoryId -> excluded.getOrPut(membership.channelId, ::linkedSetOf).add(categoryId) }
        }
        return LiveOwnPlayRefreshConstraints(
            replaceLockedChannelIds = replaceLocked,
            excludedSubtreeCategoryIdsByChannel = excluded.mapValues { it.value.toSet() },
        )
    }

    fun allowsAutomaticMembership(
        channelId: String,
        categoryId: String,
        discoveredCategories: List<LiveOwnPlayDiscoveryCategory>,
        constraints: LiveOwnPlayRefreshConstraints,
    ): Boolean {
        if (channelId in constraints.replaceLockedChannelIds) return false
        val excludedRoots = constraints.excludedSubtreeCategoryIdsByChannel[channelId].orEmpty()
        if (excludedRoots.isEmpty()) return true
        val categoryById = discoveredCategories.associateBy { it.categoryId }
        var currentId: String? = categoryId
        val visited = mutableSetOf<String>()
        while (currentId != null && visited.add(currentId)) {
            if (currentId in excludedRoots) return false
            currentId = categoryById[currentId]?.parentCategoryId
        }
        return true
    }

    private fun normalChannelPlan(
        snapshot: LiveOrganizationSnapshot,
        targetCategoryId: String,
        channelIds: List<String>,
    ): LiveOwnPlayManualEditPlan? {
        val base = membershipPlan(
            snapshot = snapshot,
            targetCategoryId = targetCategoryId,
            channelIds = channelIds,
            mode = LiveOwnPlayMembershipEditMode.ADD,
            targetEvidenceKeys = setOf(LiveOwnPlayManualOverrideEvidence.NORMAL_CHANNEL),
        ) ?: return null
        val selected = channelIds.filter(String::isNotBlank).toSet()
        val changes = linkedMapOf<Pair<String, String>, LiveOwnPlayManualMembershipChange>()
        snapshot.memberships
            .filter { membership ->
                membership.mode == LiveOrganizationMode.OWNPLAY &&
                    membership.origin == LiveOrganizationOrigin.MANUAL &&
                    membership.channelId in selected
            }
            .forEach { membership ->
                changes[membership.categoryId to membership.channelId] = LiveOwnPlayManualMembershipChange(
                    categoryId = membership.categoryId,
                    channelId = membership.channelId,
                    included = membership.included,
                )
            }
        base.membershipChanges.forEach { change -> changes[change.categoryId to change.channelId] = change }
        return base.copy(membershipChanges = changes.values.toList())
    }

    private fun markerPlan(
        snapshot: LiveOrganizationSnapshot,
        targetCategoryId: String,
        channelIds: List<String>,
    ): LiveOwnPlayManualEditPlan? {
        val selected = channelIds.filter(String::isNotBlank).distinct()
        if (selected.isEmpty()) return null
        val categories = ownPlayCategories(snapshot)
        val categoryById = categories.associateBy { it.categoryId }
        val target = categoryById[targetCategoryId] ?: return null
        val semanticKey = target.semanticKey?.takeUnless { it == "COUNTRY" } ?: return null
        val targetPath = categoryPath(targetCategoryId, categoryById) ?: return null
        val currentByChannel = snapshot.memberships
            .asSequence()
            .filter { it.mode == LiveOrganizationMode.OWNPLAY }
            .groupBy { it.channelId }
        val changes = linkedMapOf<Pair<String, String>, LiveOwnPlayManualMembershipChange>()
        selected.forEach { channelId ->
            currentByChannel[channelId].orEmpty().forEach { membership ->
                changes[membership.categoryId to channelId] = LiveOwnPlayManualMembershipChange(
                    categoryId = membership.categoryId,
                    channelId = channelId,
                    included = false,
                )
            }
            changes[targetCategoryId to channelId] = LiveOwnPlayManualMembershipChange(
                categoryId = targetCategoryId,
                channelId = channelId,
                included = false,
                evidenceKeys = setOf(LiveOwnPlayManualOverrideEvidence.sectionMarker(semanticKey)),
            )
        }
        return LiveOwnPlayManualEditPlan(
            protectedCategoryIds = targetPath.toSet(),
            membershipChanges = changes.values.toList(),
        )
    }

    private fun ownPlayCategories(snapshot: LiveOrganizationSnapshot): List<LiveOrganizationCategory> =
        snapshot.categories.filter { it.mode == LiveOrganizationMode.OWNPLAY }

    private fun categoryPath(
        categoryId: String,
        categoryById: Map<String, LiveOrganizationCategory>,
    ): List<String>? {
        val reversed = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        var currentId: String? = categoryId
        while (currentId != null) {
            if (!visited.add(currentId)) return null
            val category = categoryById[currentId] ?: return null
            reversed += currentId
            currentId = category.parentCategoryId
        }
        return reversed.asReversed()
    }

    private fun categorySubtree(
        categoryId: String,
        categories: List<LiveOrganizationCategory>,
    ): Set<String> {
        val childrenByParent = categories.groupBy { it.parentCategoryId }
        val result = linkedSetOf<String>()
        fun collect(id: String) {
            if (!result.add(id)) return
            childrenByParent[id].orEmpty().forEach { collect(it.categoryId) }
        }
        collect(categoryId)
        return result
    }
}
