package app.ownplay.mobile.sources.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCategoryVisibilityTest {
    @Test
    fun `utility labels tolerate provider punctuation and suffixes`() {
        assertTrue(ProviderCategoryVisibility.isUtilityLabel("All"))
        assertTrue(ProviderCategoryVisibility.isUtilityLabel("ALL CHANNELS"))
        assertTrue(ProviderCategoryVisibility.isUtilityLabel("• Account Information •"))
        assertTrue(ProviderCategoryVisibility.isUtilityLabel("ACCOUNT_INFO [expires soon]"))
        assertFalse(ProviderCategoryVisibility.isUtilityLabel("All Sports"))
        assertFalse(ProviderCategoryVisibility.isUtilityLabel("News"))
    }
}
