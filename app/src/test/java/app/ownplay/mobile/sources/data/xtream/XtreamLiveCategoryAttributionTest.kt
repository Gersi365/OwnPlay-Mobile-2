package app.ownplay.mobile.sources.data.xtream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamLiveCategoryAttributionTest {
    @Test
    fun normalizesProviderCategoryIds() {
        assertEquals("42", XtreamLiveCategoryAttribution.normalizeProviderCategoryId(" 42 "))
        assertEquals(null, XtreamLiveCategoryAttribution.normalizeProviderCategoryId("   "))
    }

    @Test
    fun requestsRecoveryWhenGlobalStreamsHaveNoUsableCategoryMapping() {
        assertTrue(
            XtreamLiveCategoryAttribution.needsRecovery(
                knownCategoryIds = listOf("10", "20"),
                streamCategoryIds = listOf(null, "", "999"),
            ),
        )
    }

    @Test
    fun skipsRecoveryWhenAnyGlobalStreamResolvesToProviderCategory() {
        assertFalse(
            XtreamLiveCategoryAttribution.needsRecovery(
                knownCategoryIds = listOf("10", "20"),
                streamCategoryIds = listOf(null, " 20 ", "999"),
            ),
        )
    }
}
