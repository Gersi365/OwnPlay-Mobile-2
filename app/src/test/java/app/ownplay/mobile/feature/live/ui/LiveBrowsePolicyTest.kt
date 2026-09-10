package app.ownplay.mobile.feature.live.ui

import app.ownplay.mobile.feature.live.domain.LiveCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveBrowsePolicyTest {
    @Test
    fun `provider utility categories are hidden and first real category becomes active`() {
        val categories = listOf(
            LiveCategory("all", "All", 0),
            LiveCategory("account", "Account Information", 1),
            LiveCategory("news", "News", 2),
            LiveCategory("sports", "Sports", 3),
        )
        val visible = LiveBrowsePolicy.visibleCategories(categories)
        assertEquals(listOf("news", "sports"), visible.map { it.categoryKey })
        assertEquals("news", LiveBrowsePolicy.activeCategoryKey(visible, null))
        assertEquals("sports", LiveBrowsePolicy.activeCategoryKey(visible, "sports"))
        assertEquals("news", LiveBrowsePolicy.activeCategoryKey(visible, "missing"))
    }
}
