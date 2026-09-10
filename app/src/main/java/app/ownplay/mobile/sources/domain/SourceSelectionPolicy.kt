package app.ownplay.mobile.sources.domain

object SourceSelectionPolicy {
    fun resolve(
        persistedSourceId: String?,
        sources: List<Source>,
    ): Source? {
        val selectable = sources.filter { source -> source.enabled && !source.requiresCredentials }
        if (selectable.isEmpty()) return null

        selectable.firstOrNull { it.sourceId == persistedSourceId }?.let { return it }

        return selectable.sortedWith(
            compareByDescending<Source> { it.updatedAt }
                .thenBy { it.createdAt }
                .thenBy { it.sourceId },
        ).first()
    }
}
