package app.ownplay.mobile.sources.data

import app.ownplay.mobile.sources.domain.SourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StableIdentityTest {
    private val sourceId = SourceId("source-1")

    @Test
    fun `same xtream provider id produces same channel id`() {
        assertEquals(
            StableIdentity.xtreamLiveChannel(sourceId, "42"),
            StableIdentity.xtreamLiveChannel(sourceId, "42"),
        )
    }

    @Test
    fun `different sources cannot collide for same provider id`() {
        assertNotEquals(
            StableIdentity.xtreamLiveChannel(SourceId("source-1"), "42"),
            StableIdentity.xtreamLiveChannel(SourceId("source-2"), "42"),
        )
    }

    @Test
    fun `m3u tvg id wins over changing locator hint`() {
        val first = StableIdentity.m3uLiveChannel(
            sourceId = sourceId,
            tvgId = "news.al",
            stableLocatorHint = "locator-a",
            normalizedName = "News",
            normalizedGroup = "Albania",
        )
        val second = StableIdentity.m3uLiveChannel(
            sourceId = sourceId,
            tvgId = "news.al",
            stableLocatorHint = "locator-b",
            normalizedName = "News HD",
            normalizedGroup = "Albania",
        )

        assertEquals(first, second)
    }
}
