package app.ownplay.mobile.feature.library.ui

import app.ownplay.mobile.feature.library.domain.LibraryCategory
import app.ownplay.mobile.sources.domain.ProviderCategoryVisibility

internal object LibraryBrowsePolicy {
    fun visibleCategories(categories: List<LibraryCategory>): List<LibraryCategory> =
        categories.filterNot { category -> ProviderCategoryVisibility.isUtilityLabel(category.name) }

    fun activeCategoryKey(
        categories: List<LibraryCategory>,
        requestedCategoryKey: String?,
    ): String? = requestedCategoryKey
        ?.takeIf { key -> categories.any { category -> category.categoryKey == key } }
        ?: categories.firstOrNull()?.categoryKey
}
