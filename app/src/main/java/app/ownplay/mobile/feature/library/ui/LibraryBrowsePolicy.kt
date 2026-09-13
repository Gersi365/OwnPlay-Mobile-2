package app.ownplay.mobile.feature.library.ui

import app.ownplay.mobile.feature.library.domain.LibraryCategory
import app.ownplay.mobile.sources.domain.ProviderCategoryVisibility

internal object LibraryBrowsePolicy {
    const val HOME_PREVIEW_LIMIT = 10

    fun <T> homePreview(items: List<T>): List<T> = items.take(HOME_PREVIEW_LIMIT)

    fun visibleCategories(categories: List<LibraryCategory>): List<LibraryCategory> =
        categories.filterNot { category -> ProviderCategoryVisibility.isUtilityLabel(category.name) }

    fun activeCategoryKey(
        categories: List<LibraryCategory>,
        requestedCategoryKey: String?,
    ): String? = requestedCategoryKey
        ?.takeIf { key -> categories.any { category -> category.categoryKey == key } }
        ?: categories.firstOrNull()?.categoryKey
}
