package app.ownplay.mobile.feature.live.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveOrganizationScopePolicyTest {
    @Test
    fun `provider uncategorized membership receives a stable scope id`() {
        assertEquals(
            LiveOrganizationScopePolicy.PROVIDER_UNCATEGORIZED_CATEGORY_ID,
            LiveOrganizationScopePolicy.providerCategoryId(null),
        )
        assertEquals(
            LiveOrganizationScopePolicy.PROVIDER_UNCATEGORIZED_CATEGORY_ID,
            LiveOrganizationScopePolicy.providerCategoryId("   "),
        )
        assertEquals("provider-category", LiveOrganizationScopePolicy.providerCategoryId(" provider-category "))
    }

    @Test
    fun `provider and OwnPlay personalization keys remain independent`() {
        val provider = LiveChannelMembershipPersonalizationKey(
            sourceId = "source-1",
            mode = LiveOrganizationMode.PROVIDER,
            categoryId = "italy-fhd",
            channelId = "dazn-1",
        )
        val ownPlay = provider.copy(
            mode = LiveOrganizationMode.OWNPLAY,
            categoryId = "italy-sport",
        )

        assertNotEquals(provider, ownPlay)
    }

    @Test
    fun `category reorder is valid only among siblings in one organization scope`() {
        val scope = LiveCategoryScope(
            sourceId = "source-1",
            mode = LiveOrganizationMode.OWNPLAY,
            parentCategoryId = "italy",
        )
        val sport = category(scope, "sport", "Sport")
        val film = category(scope, "film", "Film")
        val nested = category(scope.copy(parentCategoryId = "sport"), "serie-a", "Serie A")

        assertTrue(LiveOrganizationScopePolicy.canReorderCategories(scope, listOf(sport, film)))
        assertFalse(LiveOrganizationScopePolicy.canReorderCategories(scope, listOf(sport, nested)))
        assertFalse(LiveOrganizationScopePolicy.canReorderCategories(scope, listOf(sport, sport)))
    }

    @Test
    fun `channel reorder is scoped to one category membership`() {
        val sportScope = LiveChannelMembershipScope("source-1", LiveOrganizationMode.OWNPLAY, "sport")
        val dazn = membership(sportScope, "dazn-1")
        val eurosport = membership(sportScope, "eurosport-1")
        val filmMembership = membership(sportScope.copy(categoryId = "film"), "movie-channel")

        assertTrue(LiveOrganizationScopePolicy.canReorderChannels(sportScope, listOf(dazn, eurosport)))
        assertFalse(LiveOrganizationScopePolicy.canReorderChannels(sportScope, listOf(dazn, filmMembership)))
        assertFalse(
            LiveOrganizationScopePolicy.canReorderChannels(
                sportScope,
                listOf(dazn.copy(included = false), eurosport),
            ),
        )
    }

    @Test
    fun `organization origin cannot silently cross provider and OwnPlay modes`() {
        assertTrue(
            LiveOrganizationScopePolicy.originMatchesMode(
                LiveOrganizationMode.PROVIDER,
                LiveOrganizationOrigin.PROVIDER,
            ),
        )
        assertFalse(
            LiveOrganizationScopePolicy.originMatchesMode(
                LiveOrganizationMode.PROVIDER,
                LiveOrganizationOrigin.AUTO,
            ),
        )
        assertTrue(
            LiveOrganizationScopePolicy.originMatchesMode(
                LiveOrganizationMode.OWNPLAY,
                LiveOrganizationOrigin.AUTO,
            ),
        )
        assertTrue(
            LiveOrganizationScopePolicy.originMatchesMode(
                LiveOrganizationMode.OWNPLAY,
                LiveOrganizationOrigin.MANUAL,
            ),
        )
    }

    private fun category(
        scope: LiveCategoryScope,
        categoryId: String,
        name: String,
    ) = LiveOrganizationCategory(
        sourceId = scope.sourceId,
        mode = scope.mode,
        categoryId = categoryId,
        parentCategoryId = scope.parentCategoryId,
        displayName = name,
        origin = LiveOrganizationOrigin.AUTO,
    )

    private fun membership(
        scope: LiveChannelMembershipScope,
        channelId: String,
    ) = LiveChannelMembership(
        sourceId = scope.sourceId,
        mode = scope.mode,
        categoryId = scope.categoryId,
        channelId = channelId,
        origin = LiveOrganizationOrigin.AUTO,
    )
}
