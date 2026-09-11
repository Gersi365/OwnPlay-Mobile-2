package app.ownplay.mobile.sources.data.xtream

object XtreamLiveCategoryAttribution {
    fun normalizeProviderCategoryId(raw: String?): String? =
        raw?.trim()?.takeIf(String::isNotEmpty)

    fun needsRecovery(
        knownCategoryIds: Collection<String>,
        streamCategoryIds: Collection<String?>,
    ): Boolean {
        val known = knownCategoryIds
            .mapNotNull(::normalizeProviderCategoryId)
            .toSet()
        if (known.isEmpty() || streamCategoryIds.isEmpty()) return false
        return streamCategoryIds.none { raw ->
            normalizeProviderCategoryId(raw)?.let(known::contains) == true
        }
    }
}
