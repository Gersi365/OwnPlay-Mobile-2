package app.ownplay.mobile.sources.domain

object SourceSelectionPolicy {
    fun resolve(
        persistedSourceId: String?,
        sources: List<Source>,
    ): Source? {
        val enabled = sources.filter(Source::enabled)
        if (enabled.isEmpty()) return null

        enabled.firstOrNull { it.sourceId == persistedSourceId }?.let { return it }

        return enabled.sortedWith(
            compareByDescending<Source> { it.updatedAt }
                .thenBy { it.createdAt }
                .thenBy { it.sourceId },
        ).first()
    }
}
