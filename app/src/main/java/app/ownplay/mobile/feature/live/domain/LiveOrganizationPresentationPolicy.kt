package app.ownplay.mobile.feature.live.domain

data class LiveOrganizationBrowseTab(
    val categoryId: String,
    val label: String,
    val depth: Int,
    val channelIds: List<String>,
)

data class LiveOrganizationReviewCategory(
    val categoryId: String,
    val displayName: String,
    val depth: Int,
    val directChannelCount: Int,
    val totalChannelCount: Int,
    val confidence: LiveClassificationConfidence?,
    val evidenceKeys: Set<String>,
    val children: List<LiveOrganizationReviewCategory>,
)

data class LiveOrganizationReviewSnapshot(
    val sourceId: String,
    val available: Boolean,
    val categoryCount: Int,
    val classifiedChannelCount: Int,
    val mediumConfidenceChannelCount: Int,
    val roots: List<LiveOrganizationReviewCategory>,
)

data class LiveOrganizationManagementCategory(
    val category: LiveOrganizationCategory,
    val depth: Int,
    val includedChannelCount: Int,
)

object LiveOrganizationEvidencePolicy {
    private val jsonString = Regex("\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"")

    fun encodeJsonArray(values: Set<String>): String? = values
        .filter(String::isNotBlank)
        .distinct()
        .sorted()
        .takeIf(List<String>::isNotEmpty)
        ?.joinToString(prefix = "[", postfix = "]") { value ->
            "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        }

    fun decodeJsonArray(value: String?): Set<String> {
        val input = value?.trim().orEmpty()
        if (input.length < 2 || input.first() != '[' || input.last() != ']') return emptySet()
        return jsonString.findAll(input)
            .map { match -> unescape(match.groupValues[1]) }
            .filter(String::isNotBlank)
            .toCollection(linkedSetOf())
    }

    private fun unescape(value: String): String {
        val output = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val character = value[index]
            if (character != '\\' || index + 1 >= value.length) {
                output.append(character)
                index += 1
                continue
            }
            when (val escaped = value[index + 1]) {
                '\\', '\"', '/' -> output.append(escaped)
                'b' -> output.append('\b')
                'f' -> output.append('\u000c')
                'n' -> output.append('\n')
                'r' -> output.append('\r')
                't' -> output.append('\t')
                'u' -> {
                    val end = index + 6
                    val code = value.substring(index + 2, end.coerceAtMost(value.length))
                    if (end <= value.length && code.length == 4) {
                        code.toIntOrNull(16)?.let { output.append(it.toChar()) }
                        index = end
                        continue
                    }
                    output.append('u')
                }
                else -> output.append(escaped)
            }
            index += 2
        }
        return output.toString()
    }
}

object LiveOrganizationPresentationPolicy {
    fun managementCategories(snapshot: LiveOrganizationSnapshot): List<LiveOrganizationManagementCategory> {
        val categories = snapshot.categories.filter { it.mode == LiveOrganizationMode.OWNPLAY }
        val includedCounts = snapshot.memberships
            .asSequence()
            .filter { it.mode == LiveOrganizationMode.OWNPLAY && it.included }
            .groupingBy { it.categoryId }
            .eachCount()
        val childrenByParent = categories.groupBy { it.parentCategoryId }
        val result = mutableListOf<LiveOrganizationManagementCategory>()
        val visited = mutableSetOf<String>()

        fun append(category: LiveOrganizationCategory, depth: Int) {
            if (!visited.add(category.categoryId)) return
            result += LiveOrganizationManagementCategory(
                category = category,
                depth = depth,
                includedChannelCount = includedCounts[category.categoryId] ?: 0,
            )
            childrenByParent[category.categoryId].orEmpty().forEach { child -> append(child, depth + 1) }
        }

        childrenByParent[null].orEmpty().forEach { root -> append(root, 0) }
        categories.filterNot { it.categoryId in visited }.forEach { orphan -> append(orphan, 0) }
        return result
    }

    fun browseTabs(snapshot: LiveOrganizationSnapshot): List<LiveOrganizationBrowseTab> {
        val browse = LiveOrganizationBrowsePolicy.resolve(snapshot)
        val result = mutableListOf<LiveOrganizationBrowseTab>()

        fun append(
            category: LiveOrganizationBrowseCategory,
            path: List<String>,
            depth: Int,
        ) {
            val nextPath = path + category.category.displayName
            result += LiveOrganizationBrowseTab(
                categoryId = category.category.categoryId,
                label = nextPath.joinToString(" › "),
                depth = depth,
                channelIds = category.channelIds,
            )
            category.children.forEach { child -> append(child, nextPath, depth + 1) }
        }

        browse.categories.forEach { category -> append(category, emptyList(), 0) }
        return result
    }

    fun review(snapshot: LiveOrganizationSnapshot): LiveOrganizationReviewSnapshot {
        val ownPlaySnapshot = snapshot.copy(activeMode = LiveOrganizationMode.OWNPLAY)
        val browse = LiveOrganizationBrowsePolicy.resolve(ownPlaySnapshot)
        val membershipsByCategory = snapshot.memberships
            .asSequence()
            .filter { membership ->
                membership.mode == LiveOrganizationMode.OWNPLAY &&
                    membership.included &&
                    !membership.hidden
            }
            .groupBy { membership -> membership.categoryId }

        data class BuiltCategory(
            val value: LiveOrganizationReviewCategory,
            val channelIds: Set<String>,
        )

        fun build(category: LiveOrganizationBrowseCategory, depth: Int): BuiltCategory {
            val directRows = membershipsByCategory[category.category.categoryId].orEmpty()
            val children = category.children.map { child -> build(child, depth + 1) }
            val channelIds = linkedSetOf<String>()
            directRows.forEach { row -> channelIds += row.channelId }
            children.forEach { child -> channelIds += child.channelIds }
            val directConfidence = directRows
                .mapNotNull { row -> row.confidence }
                .minByOrNull(::confidenceRank)
            val evidence = directRows
                .flatMap { row -> row.evidenceKeys }
                .toCollection(sortedSetOf())
            return BuiltCategory(
                value = LiveOrganizationReviewCategory(
                    categoryId = category.category.categoryId,
                    displayName = category.category.displayName,
                    depth = depth,
                    directChannelCount = directRows.map { row -> row.channelId }.distinct().size,
                    totalChannelCount = channelIds.size,
                    confidence = directConfidence,
                    evidenceKeys = evidence,
                    children = children.map { child -> child.value },
                ),
                channelIds = channelIds,
            )
        }

        val roots = browse.categories.map { category -> build(category, 0).value }
        val categoryCount = roots.sumOf(::categoryTreeSize)
        val mediumConfidenceChannelCount = snapshot.memberships
            .asSequence()
            .filter { membership ->
                membership.mode == LiveOrganizationMode.OWNPLAY &&
                    membership.included &&
                    !membership.hidden &&
                    membership.confidence == LiveClassificationConfidence.MEDIUM
            }
            .map { membership -> membership.channelId }
            .distinct()
            .count()

        return LiveOrganizationReviewSnapshot(
            sourceId = snapshot.sourceId,
            available = roots.isNotEmpty() && browse.allChannelIds.isNotEmpty(),
            categoryCount = categoryCount,
            classifiedChannelCount = browse.allChannelIds.size,
            mediumConfidenceChannelCount = mediumConfidenceChannelCount,
            roots = roots,
        )
    }

    private fun categoryTreeSize(category: LiveOrganizationReviewCategory): Int =
        1 + category.children.sumOf(::categoryTreeSize)

    private fun confidenceRank(confidence: LiveClassificationConfidence): Int = when (confidence) {
        LiveClassificationConfidence.LOW -> 0
        LiveClassificationConfidence.MEDIUM -> 1
        LiveClassificationConfidence.HIGH -> 2
    }
}
