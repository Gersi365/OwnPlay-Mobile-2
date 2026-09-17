package app.ownplay.mobile.feature.live.domain

data class LiveOrganizationBrowseCategory(
    val category: LiveOrganizationCategory,
    val channelIds: List<String>,
    val children: List<LiveOrganizationBrowseCategory> = emptyList(),
)

data class LiveOrganizationBrowseSnapshot(
    val sourceId: String,
    val activeMode: LiveOrganizationMode,
    val categories: List<LiveOrganizationBrowseCategory>,
    val uncategorizedChannelIds: List<String> = emptyList(),
    val allChannelIds: List<String> = emptyList(),
)

object LiveOrganizationBrowsePolicy {
    fun resolve(snapshot: LiveOrganizationSnapshot): LiveOrganizationBrowseSnapshot {
        val mode = snapshot.activeMode
        val categories = snapshot.categories.filter { category -> category.mode == mode }
        val memberships = snapshot.memberships.filter { membership ->
            membership.mode == mode && membership.included && !membership.hidden
        }
        val channelIdsByCategory = memberships
            .groupBy { membership -> membership.categoryId }
            .mapValues { (_, rows) -> rows.map { row -> row.channelId }.distinct() }

        return when (mode) {
            LiveOrganizationMode.PROVIDER -> resolveProvider(
                snapshot = snapshot,
                categories = categories,
                channelIdsByCategory = channelIdsByCategory,
            )
            LiveOrganizationMode.OWNPLAY -> resolveOwnPlay(
                snapshot = snapshot,
                categories = categories,
                channelIdsByCategory = channelIdsByCategory,
            )
        }
    }

    private fun resolveProvider(
        snapshot: LiveOrganizationSnapshot,
        categories: List<LiveOrganizationCategory>,
        channelIdsByCategory: Map<String, List<String>>,
    ): LiveOrganizationBrowseSnapshot {
        val browseCategories = categories
            .filterNot { category -> category.hidden }
            .map { category ->
                LiveOrganizationBrowseCategory(
                    category = category,
                    channelIds = channelIdsByCategory[category.categoryId].orEmpty(),
                )
            }
        val uncategorized = channelIdsByCategory[
            LiveOrganizationScopePolicy.PROVIDER_UNCATEGORIZED_CATEGORY_ID
        ].orEmpty()
        val allChannelIds = buildStableChannelIds(browseCategories, uncategorized)

        return LiveOrganizationBrowseSnapshot(
            sourceId = snapshot.sourceId,
            activeMode = LiveOrganizationMode.PROVIDER,
            categories = browseCategories,
            uncategorizedChannelIds = uncategorized,
            allChannelIds = allChannelIds,
        )
    }

    private fun resolveOwnPlay(
        snapshot: LiveOrganizationSnapshot,
        categories: List<LiveOrganizationCategory>,
        channelIdsByCategory: Map<String, List<String>>,
    ): LiveOrganizationBrowseSnapshot {
        val categoryById = categories.associateBy { category -> category.categoryId }
        val eligible = categories.filter { category ->
            !category.hidden && ancestorsVisible(category, categoryById)
        }
        val eligibleIds = eligible.mapTo(linkedSetOf()) { category -> category.categoryId }
        val childrenByParent = eligible.groupBy { category -> category.parentCategoryId }

        fun build(category: LiveOrganizationCategory): LiveOrganizationBrowseCategory? {
            val children = childrenByParent[category.categoryId]
                .orEmpty()
                .mapNotNull(::build)
            val channelIds = channelIdsByCategory[category.categoryId].orEmpty()
            if (channelIds.isEmpty() && children.isEmpty()) return null
            return LiveOrganizationBrowseCategory(
                category = category,
                channelIds = channelIds,
                children = children,
            )
        }

        val roots = eligible
            .filter { category ->
                val parentId = category.parentCategoryId
                parentId == null || parentId !in eligibleIds
            }
            .mapNotNull(::build)
        val allChannelIds = buildStableChannelIds(roots, emptyList())

        return LiveOrganizationBrowseSnapshot(
            sourceId = snapshot.sourceId,
            activeMode = LiveOrganizationMode.OWNPLAY,
            categories = roots,
            allChannelIds = allChannelIds,
        )
    }

    private fun ancestorsVisible(
        category: LiveOrganizationCategory,
        categoryById: Map<String, LiveOrganizationCategory>,
    ): Boolean {
        var parentId = category.parentCategoryId
        val visited = mutableSetOf(category.categoryId)
        while (parentId != null) {
            if (!visited.add(parentId)) return false
            val parent = categoryById[parentId] ?: return true
            if (parent.hidden) return false
            parentId = parent.parentCategoryId
        }
        return true
    }

    private fun buildStableChannelIds(
        categories: List<LiveOrganizationBrowseCategory>,
        trailingChannelIds: List<String>,
    ): List<String> {
        val ids = linkedSetOf<String>()

        fun collect(category: LiveOrganizationBrowseCategory) {
            category.channelIds.forEach(ids::add)
            category.children.forEach(::collect)
        }

        categories.forEach(::collect)
        trailingChannelIds.forEach(ids::add)
        return ids.toList()
    }
}
