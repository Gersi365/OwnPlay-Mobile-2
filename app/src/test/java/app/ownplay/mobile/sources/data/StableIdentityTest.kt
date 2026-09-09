package app.ownplay.mobile.sources.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StableIdentityTest {
    @Test
    fun uniqueM3uTvgIdSurvivesRenameAndReorder() {
        val first = StableIdentity.m3uChannelId(
            sourceId = "source-a",
            tvgId = "sports-1",
            tvgIdIsUnique = true,
            normalizedStreamLocator = "https://stream.test/old",
            fallbackName = "Sports 1",
            fallbackGroup = "Sports",
        )
        val renamed = StableIdentity.m3uChannelId(
            sourceId = "source-a",
            tvgId = "sports-1",
            tvgIdIsUnique = true,
            normalizedStreamLocator = "https://stream.test/new",
            fallbackName = "Sports One HD",
            fallbackGroup = "Premium",
        )

        assertEquals(first, renamed)
    }

    @Test
    fun duplicateTvgIdFallsBackToStreamLocator() {
        val first = StableIdentity.m3uChannelId(
            "source-a", "duplicate", false, "https://stream.test/a", "A", "Group",
        )
        val second = StableIdentity.m3uChannelId(
            "source-a", "duplicate", false, "https://stream.test/b", "B", "Group",
        )
        assertNotEquals(first, second)
    }

    @Test
    fun xtreamIdentityIsSourceScoped() {
        val first = StableIdentity.xtreamContentId("source-a", "live", "42")
        val second = StableIdentity.xtreamContentId("source-b", "live", "42")
        assertNotEquals(first, second)
    }
}
