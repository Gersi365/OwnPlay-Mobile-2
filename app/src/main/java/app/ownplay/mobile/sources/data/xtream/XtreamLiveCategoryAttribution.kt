package app.ownplay.mobile.sources.data.xtream

object XtreamLiveCategoryAttribution {
    fun normalizeProviderCategoryId(raw: String?): String? =
        raw?.trim()?.takeIf(String::isNotEmpty)

    fun resolveProviderCategoryId(
        primary: String?,
        fallbacks: Collection<String?>,
    ): String? {
        normalizeProviderCategoryId(primary)?.let { return it }
        return fallbacks
            .mapNotNull(::normalizeProviderCategoryId)
            .distinct()
            .singleOrNull()
    }

    fun needsRecovery(
        knownCategoryIds: Collection<String>,
        streamCategoryIds: Collection<String?>,
    ): Boolean {
        val known = knownCategoryIds
            .mapNotNull(::normalizeProviderCategoryId)
            .toSet()
        if (known.isEmpty() || streamCategoryIds.isEmpty()) return false
        return streamCategoryIds.any { raw ->
            normalizeProviderCategoryId(raw)?.let(known::contains) != true
        }
    }
}
