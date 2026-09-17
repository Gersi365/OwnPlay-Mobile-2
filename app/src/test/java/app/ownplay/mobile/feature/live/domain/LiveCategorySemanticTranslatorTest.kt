package app.ownplay.mobile.feature.live.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveCategorySemanticTranslatorTest {
    @Test
    fun `translation is scoped to detected country languages plus English`() {
        assertTrue(LiveCategorySemanticTranslator.translate("AL", "Kanale Lajme").has("NEWS"))
        assertFalse(LiveCategorySemanticTranslator.translate("IT", "Canali Lajme").has("NEWS"))
        assertTrue(LiveCategorySemanticTranslator.translate("IT", "Canali Notizie").has("NEWS"))
        assertTrue(LiveCategorySemanticTranslator.translate("IT", "News").has("NEWS"))
    }

    @Test
    fun `non decomposing latin letters are folded before semantic matching`() {
        assertTrue(LiveCategorySemanticTranslator.translate("PL", "Piłka Nożna").has("FOOTBALL"))
        assertTrue(LiveCategorySemanticTranslator.translate("DK", "Børn").has("KIDS"))
    }

    @Test
    fun `minor provider spelling variants use conservative fuzzy matching`() {
        assertTrue(LiveCategorySemanticTranslator.translate("AL", "Kanale Muzikorr").has("MUSIC"))
        assertTrue(LiveCategorySemanticTranslator.translate("IT", "Canali Bambina").has("KIDS"))
        assertFalse(LiveCategorySemanticTranslator.translate("AL", "Premium Random").has("MUSIC"))
    }

    @Test
    fun `country language selection exposes only available packs`() {
        val albania = LiveCategorySemanticTranslator.languageCodesForCountry("AL")
        assertTrue("en" in albania)
        assertTrue("sq" in albania)
        assertFalse("it" in albania)

        val switzerland = LiveCategorySemanticTranslator.languageCodesForCountry("CH")
        assertTrue("en" in switzerland)
        assertTrue(switzerland.any { it == "de" || it == "fr" || it == "it" })
    }

    private fun List<LiveCategorySemanticTranslation>.has(semanticKey: String): Boolean =
        any { translation -> translation.semanticKey == semanticKey }
}
