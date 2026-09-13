package app.ownplay.mobile.feature.library.ui

import app.ownplay.mobile.feature.library.domain.LibraryCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryBrowsePolicyTest {
    @Test
    fun `library uses provider categories without synthetic all`() {
        val categories = listOf(
            LibraryCategory("all", "All Movies", 0),
            LibraryCategory("action", "Action", 1),
            LibraryCategory("drama", "Drama", 2),
        )
        val visible = LibraryBrowsePolicy.visibleCategories(categories)
        assertEquals(listOf("action", "drama"), visible.map { it.categoryKey })
        assertEquals("action", LibraryBrowsePolicy.activeCategoryKey(visible, null))
    }

    @Test
    fun `library home preview caps category rows at ten`() {
        val items = (1..14).toList()
        assertEquals((1..10).toList(), LibraryBrowsePolicy.homePreview(items))
        assertEquals(10, LibraryBrowsePolicy.HOME_PREVIEW_LIMIT)
    }
}
