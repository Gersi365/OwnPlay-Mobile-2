package app.ownplay.mobile.sources.data

/** Existing IDs are references held by personalization, groups, progress and backups. */
internal data class PersistedM3uIdentity(
    val channelId: String,
    val tvgId: String?,
    val streamLocator: String,
    val available: Boolean,
)

internal object M3uIdentityReconciliation {
    fun reconcile(
        sourceId: String,
        incoming: List<ProviderLiveChannelRecord>,
        previous: List<PersistedM3uIdentity>,
    ): List<ProviderLiveChannelRecord> {
        val priorByLocator = previous.groupBy { it.streamLocator }
        val priorByTvg = previous.filter { !it.tvgId.isNullOrBlank() }.groupBy { it.tvgId!!.trim() }
        val incomingTvgCounts = incoming.mapNotNull { it.tvgId?.trim()?.takeIf(String::isNotEmpty) }
            .groupingBy { it }.eachCount()
        val reservedIds = previous.mapTo(hashSetOf()) { it.channelId }
        val assignedIds = hashSetOf<String>()
        val assignments = mutableMapOf<String, String>()

        // First reserve exact locator matches for the whole batch. A new duplicate tvg-id
        // must not take the existing channel's identity merely because it arrives first.
        incoming.forEach { row ->
            val prior = priorByLocator[row.streamLocator].orEmpty()
                .sortedWith(compareByDescending<PersistedM3uIdentity> { it.available }.thenBy { it.channelId })
                .firstOrNull { it.channelId !in assignedIds }
            if (prior != null) {
                assignments[row.streamLocator] = prior.channelId
                assignedIds += prior.channelId
            }
        }
        return incoming.map { row ->
            val exact = assignments[row.streamLocator]
            val tvg = row.tvgId?.trim()?.takeIf(String::isNotEmpty)
            val uniquePrior = tvg?.takeIf { incomingTvgCounts[it] == 1 }
                ?.let { priorByTvg[it]?.singleOrNull() }
                ?.takeIf { it.channelId !in assignedIds }
            val id = exact ?: uniquePrior?.channelId ?: row.channelId
                .takeIf { it !in reservedIds && it !in assignedIds }
                ?: StableIdentity.m3uChannelId(
                    sourceId, row.tvgId, false, row.streamLocator, row.name, row.categoryKey,
                )
            assignedIds += id
            row.copy(channelId = id)
        }
    }
}
