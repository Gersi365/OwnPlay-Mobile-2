package app.ownplay.mobile.feature.live.ui

import app.ownplay.mobile.feature.live.domain.LiveCategory

internal object LiveBrowsePolicy {
    private val hiddenCategoryLabels = setOf(
        "all",
        "account information",
        "account info",
    )

    fun visibleCategories(categories: List<LiveCategory>): List<LiveCategory> =
        categories.filterNot { category ->
            category.name.trim().lowercase() in hiddenCategoryLabels
        }

    fun activeCategoryKey(
        categories: List<LiveCategory>,
        requestedCategoryKey: String?,
    ): String? = requestedCategoryKey
        ?.takeIf { key -> categories.any { category -> category.categoryKey == key } }
        ?: categories.firstOrNull()?.categoryKey
}
