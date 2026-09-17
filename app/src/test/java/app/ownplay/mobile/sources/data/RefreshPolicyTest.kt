package app.ownplay.mobile.sources.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshPolicyTest {
    @Test
    fun partialRefreshAdvancesGenerationOnlyForSuccessfulSections() {
        val payload = payload(
            live = RemoteSection.success(emptyList()),
            movies = RemoteSection.failed("HTTP_500"),
        )

        val plan = RefreshPolicy.plan(previousGeneration = 7, payload = payload)

        assertEquals(8, plan.generation)
        assertEquals("PARTIAL", plan.state)
        assertTrue(CatalogSection.LIVE_CHANNELS in plan.successfulSections)
        assertFalse(CatalogSection.MOVIES in plan.successfulSections)
    }

    @Test
    fun totalFailureDoesNotAdvanceGeneration() {
        val failed = RemoteSection.failed<List<ProviderLiveChannelRecord>>("NETWORK")
        val payload = ProviderRefreshPayload(
            liveCategories = RemoteSection.failed("NETWORK"),
            liveChannels = failed,
            vodCategories = RemoteSection.failed("NETWORK"),
            movies = RemoteSection.failed("NETWORK"),
            seriesCategories = RemoteSection.failed("NETWORK"),
            series = RemoteSection.failed("NETWORK"),
        )

        val plan = RefreshPolicy.plan(4, payload)

        assertEquals(4, plan.generation)
        assertEquals("FAILED", plan.state)
        assertTrue(plan.successfulSections.isEmpty())
    }

    @Test
    fun incompleteInventoryIsUpsertedWithoutRetiringUnseenItems() {
        val payload = payload(
            live = RemoteSection.partial(emptyList(), "M3U_PARTIAL"),
            movies = RemoteSection.success(emptyList()),
        )
        val plan = RefreshPolicy.plan(7, payload)
        assertEquals(8, plan.generation)
        assertEquals("PARTIAL", plan.state)
        assertTrue(CatalogSection.LIVE_CHANNELS in plan.successfulSections)
        assertFalse(CatalogSection.LIVE_CHANNELS in plan.authoritativeSections)
        // Explicitly complete empty provider inventories can still retire removed items.
        assertTrue(CatalogSection.MOVIES in plan.authoritativeSections)
        assertEquals("M3U_PARTIAL", plan.errorCode)
    }

    @Test
    fun failedAndSkippedSectionsNeverAuthorizeRetirement() {
        val plan = RefreshPolicy.plan(3, payload(
            live = RemoteSection.failed("NETWORK"),
            movies = RemoteSection.skipped(),
        ))
        assertTrue(plan.authoritativeSections.isEmpty())
        assertTrue(plan.successfulSections.isEmpty())
        assertEquals(3, plan.generation)
    }

    private fun payload(
        live: RemoteSection<List<ProviderLiveChannelRecord>>,
        movies: RemoteSection<List<ProviderMovieRecord>>,
    ) = ProviderRefreshPayload(
        liveCategories = RemoteSection.skipped(),
        liveChannels = live,
        vodCategories = RemoteSection.skipped(),
        movies = movies,
        seriesCategories = RemoteSection.skipped(),
        series = RemoteSection.skipped(),
    )
}
