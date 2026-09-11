package app.ownplay.mobile.sources.data.xtream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamLiveCategoryAttributionTest {
    @Test
    fun normalizesProviderCategoryIds() {
        assertEquals("42", XtreamLiveCategoryAttribution.normalizeProviderCategoryId(" 42 "))
        assertEquals(null, XtreamLiveCategoryAttribution.normalizeProviderCategoryId("   "))
    }

    @Test
    fun resolvesSingleFallbackProviderCategoryId() {
        assertEquals(
            "20",
            XtreamLiveCategoryAttribution.resolveProviderCategoryId(
                primary = null,
                fallbacks = listOf(" 20 ", "20"),
            ),
        )
        assertNull(
            XtreamLiveCategoryAttribution.resolveProviderCategoryId(
                primary = null,
                fallbacks = listOf("10", "20"),
            ),
        )
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
    fun requestsRecoveryWhenGlobalMappingIsPartial() {
        assertTrue(
            XtreamLiveCategoryAttribution.needsRecovery(
                knownCategoryIds = listOf("10", "20"),
                streamCategoryIds = listOf("10", null, "999", " 20 "),
            ),
        )
    }

    @Test
    fun skipsRecoveryOnlyWhenEveryGlobalStreamMapsToKnownCategory() {
        assertFalse(
            XtreamLiveCategoryAttribution.needsRecovery(
                knownCategoryIds = listOf("10", "20"),
                streamCategoryIds = listOf("10", " 20 ", "10"),
            ),
        )
    }
}
