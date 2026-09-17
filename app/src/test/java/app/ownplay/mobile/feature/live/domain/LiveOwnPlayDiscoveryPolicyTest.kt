package app.ownplay.mobile.feature.live.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveOwnPlayDiscoveryPolicyTest {
    @Test
    fun `technical quality tokens do not become semantic identity`() {
        assertEquals(
            "IT DAZN SERIE A 1",
            LiveOwnPlayDiscoveryPolicy.normalizedIdentity("IT | DAZN Serie A 1 FHD HEVC"),
        )
        assertEquals(
            "ITALIA CINEMA",
            LiveOwnPlayDiscoveryPolicy.normalizedIdentity("Italia • Cinema • 4K HDR"),
        )
    }

    @Test
    fun `country first discovery emits only categories backed by real memberships`() {
        val result = LiveOwnPlayDiscoveryPolicy.discover(
            listOf(
                channel("rai-1", "Italy FHD", "Rai 1 FHD"),
                channel("dazn-1", "Italy HEVC", "DAZN Serie A 1 FHD"),
                channel("cinema-1", "Italy HD", "Sky Cinema Uno HD"),
            ),
        )

        assertEquals(
            setOf(
                "ownplay:country:IT",
                "ownplay:country:IT:semantic:GENERAL",
                "ownplay:country:IT:semantic:SPORT",
                "ownplay:country:IT:semantic:DAZN",
                "ownplay:country:IT:semantic:SERIE_A",
                "ownplay:country:IT:semantic:FILM",
            ),
            result.categories.map { it.categoryId }.toSet(),
        )
        assertFalse(result.categories.any { it.semanticKey == "SERIE_B" })
        assertEquals(
            "ownplay:country:IT",
            result.categories.single { it.semanticKey == "SPORT" }.parentCategoryId,
        )
        assertEquals(
            "ownplay:country:IT:semantic:SPORT",
            result.categories.single { it.semanticKey == "DAZN" }.parentCategoryId,
        )
        assertEquals(
            "ownplay:country:IT:semantic:SPORT",
            result.categories.single { it.semanticKey == "SERIE_A" }.parentCategoryId,
        )
        assertEquals(
            setOf(
                "ownplay:country:IT",
                "ownplay:country:IT:semantic:SPORT",
                "ownplay:country:IT:semantic:DAZN",
                "ownplay:country:IT:semantic:SERIE_A",
            ),
            result.memberships
                .filter { it.channelId == "dazn-1" }
                .map { it.categoryId }
                .toSet(),
        )
        assertEquals(listOf("rai-1"), result.unclassifiedChannelIds)
    }

    @Test
    fun `localized provider categories classify countries and semantics`() {
        val result = LiveOwnPlayDiscoveryPolicy.discover(
            listOf(
                channel("al-music", "Shqipëri Muzikë", "Top Channel One"),
                channel("al-kids", "Shqip Fëmijë", "Junior One"),
                channel("al-news", "Shqipëri Lajme", "News One"),
                channel("al-general", "Shqipëri Të Përgjithshme", "General One"),
                channel("it-kids", "Italia Bambini", "Junior Due"),
                channel("de-news", "Deutschland Nachrichten", "Nachrichten Eins"),
                channel("fr-music", "France Musique", "Musique Un"),
                channel("es-film", "España Películas", "Canal Uno"),
                channel("gr-music", "Ελλάδα Μουσική", "Κανάλι Ένα"),
                channel("bg-news", "България Новини", "Канал Едно"),
            ),
        )

        fun has(countryCode: String, semanticKey: String) = result.categories.any { category ->
            category.categoryId == "ownplay:country:$countryCode:semantic:$semanticKey"
        }

        assertTrue(has("AL", "MUSIC"))
        assertTrue(has("AL", "KIDS"))
        assertTrue(has("AL", "NEWS"))
        assertTrue(has("AL", "GENERAL"))
        assertTrue(has("IT", "KIDS"))
        assertTrue(has("DE", "NEWS"))
        assertTrue(has("FR", "MUSIC"))
        assertTrue(has("ES", "FILM"))
        assertTrue(has("GR", "MUSIC"))
        assertTrue(has("BG", "NEWS"))
    }

    @Test
    fun `albanian provider vocabulary is translated from iso3 and grammatical variants`() {
        val result = LiveOwnPlayDiscoveryPolicy.discover(
            listOf(
                channel("al-music", "ALB | Kanale Muzikore", "Top Muzik One"),
                channel("al-kids", "ALB | Kanale për Fëmijët", "Junior Shqip"),
                channel("al-news", "ALB | Kanale Informative", "Info Shqip"),
                channel("al-current", "ALB | Aktualitete", "Aktualitet Shqip"),
                channel("al-doc", "ALB | Dokumentarë", "Dok Shqip"),
                channel("al-ent", "ALB | Argëtuese", "Argetim Shqip"),
                channel("al-culture", "ALB | Kulturë", "Kultura Shqip"),
                channel("al-general", "ALB | Gjenerale", "General Shqip"),
                channel("al-sport", "ALB | Sportive", "Sport Shqip"),
            ),
        )

        fun has(semanticKey: String) = result.categories.any { category ->
            category.categoryId == "ownplay:country:AL:semantic:$semanticKey"
        }

        assertTrue(has("MUSIC"))
        assertTrue(has("KIDS"))
        assertTrue(has("NEWS"))
        assertTrue(has("DOCUMENTARY"))
        assertTrue(has("ENTERTAINMENT"))
        assertTrue(has("CULTURE"))
        assertTrue(has("GENERAL"))
        assertTrue(has("SPORT"))
        assertEquals(9, result.memberships.count { it.categoryId == "ownplay:country:AL" })
        assertTrue(result.unclassifiedChannelIds.isEmpty())
    }

    @Test
    fun `flag country marker activates the matching local language pack`() {
        val result = LiveOwnPlayDiscoveryPolicy.discover(
            listOf(channel("flag-music", "🇦🇱 | Kanale Muzikore", "Top Shqip")),
        )

        val music = result.categories.single { category ->
            category.categoryId == "ownplay:country:AL:semantic:MUSIC"
        }
        assertEquals("Music", music.displayName)
        assertTrue(result.unclassifiedChannelIds.isEmpty())
    }

    @Test
    fun `localized decorative marker uses the detected country language`() {
        val result = LiveOwnPlayDiscoveryPolicy.discover(
            listOf(
                channel("music-marker", "Shqipëri", "---- MUZIKË ----"),
                channel("music-channel", "Shqipëri", "Top Channel"),
            ),
        )

        assertEquals(setOf("music-marker"), result.automaticMarkerChannelIds)
        assertTrue(
            result.memberships.any { membership ->
                membership.channelId == "music-channel" &&
                    membership.categoryId == "ownplay:country:AL:semantic:MUSIC"
            },
        )
    }

    @Test
    fun `unknown provider category uses canonical general fallback without inventing local taxonomy`() {
        val result = LiveOwnPlayDiscoveryPolicy.discover(
            listOf(channel("se-unknown", "Sverige Premium X", "Kanal Ett")),
        )

        assertEquals(
            setOf("ownplay:country:SE", "ownplay:country:SE:semantic:GENERAL"),
            result.categories.map { it.categoryId }.toSet(),
        )
        assertEquals(listOf("se-unknown"), result.unclassifiedChannelIds)
        assertTrue(result.categories.single { it.semanticKey == "GENERAL" }.displayName == "General")
        assertFalse(result.categories.any { it.displayName == "Premium X" })
    }

    @Test
    fun `country language translation keeps canonical OwnPlay taxonomy in English`() {
        val result = LiveOwnPlayDiscoveryPolicy.discover(
            listOf(
                channel("al-kids", "Shqipëri Fëmijë", "Junior Shqip"),
                channel("it-kids", "Italia Bambini", "Junior Italia"),
                channel("se-kids", "Sverige Barn", "Barn Ett"),
            ),
        )

        listOf("AL", "IT", "SE").forEach { countryCode ->
            val category = result.categories.single { item ->
                item.categoryId == "ownplay:country:$countryCode:semantic:KIDS"
            }
            assertEquals("Kids", category.displayName)
            assertEquals("KIDS", category.semanticKey)
            assertEquals("ownplay:country:$countryCode", category.parentCategoryId)
        }
        assertTrue(result.unclassifiedChannelIds.isEmpty())
    }

    @Test
    fun `provider semantic translation is scoped to detected country languages plus English`() {
        val result = LiveOwnPlayDiscoveryPolicy.discover(
            listOf(
                channel("it-albanian-word", "Italia Lajme", "Canale Uno"),
                channel("it-english", "Italia News", "Canale Due"),
            ),
        )

        assertFalse(
            result.memberships.any { membership ->
                membership.channelId == "it-albanian-word" &&
                    membership.categoryId == "ownplay:country:IT:semantic:NEWS"
            },
        )
        assertTrue(
            result.memberships.any { membership ->
                membership.channelId == "it-english" &&
                    membership.categoryId == "ownplay:country:IT:semantic:NEWS"
            },
        )
        assertEquals(listOf("it-albanian-word"), result.unclassifiedChannelIds)
    }


    @Test
    fun `same canonical categories are discovered from different country languages`() {
        val result = LiveOwnPlayDiscoveryPolicy.discover(
            listOf(
                channel("al-music", "ALB Muzikore", "A"),
                channel("it-music", "Italia Musica", "B"),
                channel("de-music", "Deutschland Musik", "C"),
                channel("fr-music", "France Musique", "D"),
                channel("es-music", "España Música", "E"),
            ),
        )

        listOf("AL", "IT", "DE", "FR", "ES").forEach { countryCode ->
            val music = result.categories.single { category ->
                category.categoryId == "ownplay:country:$countryCode:semantic:MUSIC"
            }
            assertEquals("Music", music.displayName)
            assertEquals("MUSIC", music.semanticKey)
        }
    }

    @Test
    fun `localized child categories inherit source country context without repeating country name`() {
        val result = LiveOwnPlayDiscoveryPolicy.discover(
            listOf(
                channel("al-root", "Albania", "Top Channel", providerCategoryId = "al-root-category"),
                channel("al-music", "Muzikë", "My Music", providerCategoryId = "al-music-category"),
                channel("al-kids", "Fëmijë", "Junior Shqip", providerCategoryId = "al-kids-category"),
                channel("it-root", "Italia", "Rai 1", providerCategoryId = "it-root-category"),
                channel("it-music", "Musica", "Radio Italia TV", providerCategoryId = "it-music-category"),
                channel("it-kids", "Bambini", "Junior Italia", providerCategoryId = "it-kids-category"),
            ),
        )

        fun has(countryCode: String, semanticKey: String) = result.categories.any { category ->
            category.categoryId == "ownplay:country:$countryCode:semantic:$semanticKey"
        }

        assertTrue(has("AL", "MUSIC"))
        assertTrue(has("AL", "KIDS"))
        assertTrue(has("IT", "MUSIC"))
        assertTrue(has("IT", "KIDS"))
        assertTrue(
            result.memberships
                .first { membership -> membership.channelId == "al-music" && membership.categoryId.endsWith(":semantic:MUSIC") }
                .evidenceKeys
                .any { evidence -> evidence == "provider-category-language:sq" },
        )
    }

    @Test
    fun `provider category ids keep repeated opaque display names isolated across country blocks`() {
        val result = LiveOwnPlayDiscoveryPolicy.discover(
            listOf(
                channel("al-root", "Albania", "Top Channel", providerCategoryId = "al-root-category"),
                channel("al-premium-1", "Premium", "MTV Live", providerCategoryId = "al-premium"),
                channel("al-premium-2", "Premium", "VH1 Classic", providerCategoryId = "al-premium"),
                channel("it-root", "Italia", "Rai 1", providerCategoryId = "it-root-category"),
                channel("it-premium-1", "Premium", "Cartoon Network", providerCategoryId = "it-premium"),
                channel("it-premium-2", "Premium", "Nick Jr", providerCategoryId = "it-premium"),
            ),
        )

        val alMusic = result.memberships
            .filter { membership -> membership.categoryId == "ownplay:country:AL:semantic:MUSIC" }
            .mapTo(linkedSetOf()) { membership -> membership.channelId }
        val itKids = result.memberships
            .filter { membership -> membership.categoryId == "ownplay:country:IT:semantic:KIDS" }
            .mapTo(linkedSetOf()) { membership -> membership.channelId }

        assertEquals(setOf("al-premium-1", "al-premium-2"), alMusic)
        assertEquals(setOf("it-premium-1", "it-premium-2"), itKids)
        assertFalse(result.memberships.any { membership ->
            membership.channelId.startsWith("al-premium") && membership.categoryId.startsWith("ownplay:country:IT")
        })
        assertFalse(result.memberships.any { membership ->
            membership.channelId.startsWith("it-premium") && membership.categoryId.startsWith("ownplay:country:AL")
        })
    }

    @Test
    fun `opaque albanian provider labels are inferred from category channel content`() {
        val result = LiveOwnPlayDiscoveryPolicy.discover(
            listOf(
                channel("music-1", "ALB | PREMIUM A", "MTV Live"),
                channel("music-2", "ALB | PREMIUM A", "Trace Urban"),
                channel("music-3", "ALB | PREMIUM A", "VH1 Classic"),
                channel("kids-1", "ALB | PREMIUM B", "Cartoon Network"),
                channel("kids-2", "ALB | PREMIUM B", "Nick Jr"),
                channel("kids-3", "ALB | PREMIUM B", "Disney Junior"),
                channel("news-1", "ALB | PREMIUM C", "CNN International"),
                channel("news-2", "ALB | PREMIUM C", "Euronews Albania"),
                channel("news-3", "ALB | PREMIUM C", "Bloomberg Europe"),
                channel("doc-1", "ALB | PREMIUM D", "Discovery Channel"),
                channel("doc-2", "ALB | PREMIUM D", "National Geographic"),
                channel("doc-3", "ALB | PREMIUM D", "History Channel"),
            ),
        )

        fun members(semanticKey: String): Set<String> {
            val categoryId = "ownplay:country:AL:semantic:$semanticKey"
            return result.memberships
                .filter { membership -> membership.categoryId == categoryId }
                .mapTo(linkedSetOf()) { membership -> membership.channelId }
        }

        assertEquals(setOf("music-1", "music-2", "music-3"), members("MUSIC"))
        assertEquals(setOf("kids-1", "kids-2", "kids-3"), members("KIDS"))
        assertEquals(setOf("news-1", "news-2", "news-3"), members("NEWS"))
        assertEquals(setOf("doc-1", "doc-2", "doc-3"), members("DOCUMENTARY"))
        assertTrue(result.unclassifiedChannelIds.isEmpty())
    }

    @Test
    fun `content inference is country agnostic and preserves canonical english taxonomy`() {
        val result = LiveOwnPlayDiscoveryPolicy.discover(
            listOf(
                channel("it-kids-1", "ITA | PACCHETTO X", "Cartoon Network"),
                channel("it-kids-2", "ITA | PACCHETTO X", "Nick Jr"),
                channel("de-doc-1", "DEU | PAKET X", "Discovery Channel"),
                channel("de-doc-2", "DEU | PAKET X", "Nat Geo Wild"),
            ),
        )

        val italyKids = result.categories.single { it.categoryId == "ownplay:country:IT:semantic:KIDS" }
        val germanyDocumentary = result.categories.single { it.categoryId == "ownplay:country:DE:semantic:DOCUMENTARY" }
        assertEquals("Kids", italyKids.displayName)
        assertEquals("Documentary", germanyDocumentary.displayName)
    }

    @Test
    fun `documentary channel brand is not polluted by localized general fuzzy matching`() {
        val result = LiveOwnPlayDiscoveryPolicy.discover(
            listOf(channel("natgeo", "ITA | PACCHETTO X", "National Geographic")),
        )

        assertTrue(
            result.memberships.any { membership ->
                membership.channelId == "natgeo" && membership.categoryId.endsWith(":semantic:DOCUMENTARY")
            },
        )
        assertFalse(
            result.memberships.any { membership ->
                membership.channelId == "natgeo" && membership.categoryId.endsWith(":semantic:GENERAL")
            },
        )
    }

    @Test
    fun `mixed opaque category does not propagate a weak semantic winner to every channel`() {
        val result = LiveOwnPlayDiscoveryPolicy.discover(
            listOf(
                channel("mix-music", "ALB | MIX X", "MTV Live"),
                channel("mix-news", "ALB | MIX X", "CNN International"),
                channel("mix-kids", "ALB | MIX X", "Cartoon Network"),
                channel("mix-generic", "ALB | MIX X", "Channel One"),
            ),
        )

        assertTrue(result.memberships.any { it.channelId == "mix-music" && it.categoryId.endsWith(":semantic:MUSIC") })
        assertTrue(result.memberships.any { it.channelId == "mix-news" && it.categoryId.endsWith(":semantic:NEWS") })
        assertTrue(result.memberships.any { it.channelId == "mix-kids" && it.categoryId.endsWith(":semantic:KIDS") })
        assertTrue(result.memberships.any { it.channelId == "mix-generic" && it.categoryId.endsWith(":semantic:GENERAL") })
        assertFalse(
            result.memberships.any {
                it.channelId == "mix-generic" &&
                    (it.categoryId.endsWith(":semantic:MUSIC") ||
                        it.categoryId.endsWith(":semantic:NEWS") ||
                        it.categoryId.endsWith(":semantic:KIDS"))
            },
        )
    }

    @Test
    fun `high confidence decorative rows become section markers and are not channels`() {
        val result = LiveOwnPlayDiscoveryPolicy.discover(
            listOf(
                channel("sport-marker", "Italia FHD", "----- SPORT -----"),
                channel("sport-channel", "Italia FHD", "Rai Sport Uno"),
                channel("film-marker", "Italia FHD", "***** FILM *****"),
                channel("film-channel", "Italia FHD", "Canale Cinema Due"),
            ),
        )

        assertEquals(setOf("sport-marker", "film-marker"), result.automaticMarkerChannelIds)
        assertTrue(result.reviewMarkerCandidates.isEmpty())
        assertFalse(result.memberships.any { it.channelId == "sport-marker" })
        assertFalse(result.memberships.any { it.channelId == "film-marker" })
        assertTrue(
            result.memberships.any {
                it.channelId == "sport-channel" &&
                    it.categoryId == "ownplay:country:IT:semantic:SPORT"
            },
        )
        assertTrue(
            result.memberships.any {
                it.channelId == "film-channel" &&
                    it.categoryId == "ownplay:country:IT:semantic:FILM"
            },
        )
    }

    @Test
    fun `medium confidence marker stays review only and is not auto hidden`() {
        val result = LiveOwnPlayDiscoveryPolicy.discover(
            listOf(
                channel(
                    id = "news-candidate",
                    providerCategory = "Italy HD",
                    name = "---- NEWS ----",
                    tvgId = "news-candidate.example",
                    hasLogo = true,
                ),
            ),
        )

        assertTrue(result.automaticMarkerChannelIds.isEmpty())
        assertEquals(1, result.reviewMarkerCandidates.size)
        assertEquals("news-candidate", result.reviewMarkerCandidates.single().channelId)
        assertEquals("NEWS", result.reviewMarkerCandidates.single().semanticKey)
        assertEquals(LiveClassificationConfidence.MEDIUM, result.reviewMarkerCandidates.single().confidence)
        assertTrue(result.memberships.any { it.channelId == "news-candidate" })
        assertEquals(listOf("news-candidate"), result.unclassifiedChannelIds)
    }

    @Test
    fun `country must be known before semantic categories are emitted`() {
        val result = LiveOwnPlayDiscoveryPolicy.discover(
            listOf(
                channel("mixed-dazn", "Premium FHD", "DAZN Serie A 1 FHD"),
                channel("mixed-cinema", null, "Cinema Uno HD"),
            ),
        )

        assertTrue(result.categories.isEmpty())
        assertTrue(result.memberships.isEmpty())
        assertEquals(listOf("mixed-dazn", "mixed-cinema"), result.unclassifiedChannelIds)
    }

    @Test
    fun `marker scope resets when provider category changes`() {
        val result = LiveOwnPlayDiscoveryPolicy.discover(
            listOf(
                channel("sport-marker", "Italy FHD", "----- SPORT -----"),
                channel("sport-1", "Italy FHD", "Channel One"),
                channel("general-1", "Italy HEVC", "Channel Two"),
            ),
        )

        assertTrue(
            result.memberships.any {
                it.channelId == "sport-1" &&
                    it.categoryId == "ownplay:country:IT:semantic:SPORT"
            },
        )
        assertFalse(
            result.memberships.any {
                it.channelId == "general-1" &&
                    it.categoryId == "ownplay:country:IT:semantic:SPORT"
            },
        )
        assertEquals(listOf("general-1"), result.unclassifiedChannelIds)
    }

    @Test
    fun `source profile can confirm or reject marker interpretation deterministically`() {
        val forcedMarkerResult = LiveOwnPlayDiscoveryPolicy.discover(
            channels = listOf(
                channel("profile-marker", "Italy HD", "My Sports Section"),
                channel("after-marker", "Italy HD", "Channel One"),
            ),
            profile = LiveOwnPlayDiscoveryProfile(
                forcedMarkers = mapOf("profile-marker" to "SPORT"),
            ),
        )

        assertEquals(setOf("profile-marker"), forcedMarkerResult.automaticMarkerChannelIds)
        assertTrue(
            forcedMarkerResult.memberships.any {
                it.channelId == "after-marker" &&
                    it.categoryId == "ownplay:country:IT:semantic:SPORT"
            },
        )

        val forcedNormalResult = LiveOwnPlayDiscoveryPolicy.discover(
            channels = listOf(
                channel("normal-row", "Italy HD", "----- SPORT -----"),
                channel("after-normal", "Italy HD", "Channel Two"),
            ),
            profile = LiveOwnPlayDiscoveryProfile(
                forcedNormalChannels = setOf("normal-row"),
            ),
        )

        assertTrue(forcedNormalResult.automaticMarkerChannelIds.isEmpty())
        assertFalse(
            forcedNormalResult.memberships.any {
                it.channelId == "after-normal" &&
                    it.categoryId == "ownplay:country:IT:semantic:SPORT"
            },
        )
        assertEquals(listOf("after-normal"), forcedNormalResult.unclassifiedChannelIds)
    }

    @Test
    fun `empty country marker categories provide ordered context for localized child categories`() {
        val result = LiveOwnPlayDiscoveryPolicy.discover(
            channels = listOf(
                channel("al-music", "Muzikë", "Top Music", providerCategoryId = "al-music"),
                channel("al-sport", "Sport", "SuperSport 1", providerCategoryId = "al-sport"),
                channel("it-music", "Musica", "Radio Italia TV", providerCategoryId = "it-music"),
                channel("it-sport", "Sport", "Sky Sport Uno", providerCategoryId = "it-sport"),
            ),
            providerCategoryCatalog = listOf(
                LiveOwnPlayDiscoveryProviderCategory("country-al", "Albania", 0),
                LiveOwnPlayDiscoveryProviderCategory("al-music", "Muzikë", 1),
                LiveOwnPlayDiscoveryProviderCategory("al-sport", "Sport", 2),
                LiveOwnPlayDiscoveryProviderCategory("country-it", "Italia", 3),
                LiveOwnPlayDiscoveryProviderCategory("it-music", "Musica", 4),
                LiveOwnPlayDiscoveryProviderCategory("it-sport", "Sport", 5),
            ),
        )

        fun has(channelId: String, countryCode: String, semanticKey: String) =
            result.memberships.any { membership ->
                membership.channelId == channelId &&
                    membership.categoryId == "ownplay:country:$countryCode:semantic:$semanticKey"
            }

        assertTrue(has("al-music", "AL", "MUSIC"))
        assertTrue(has("al-sport", "AL", "SPORT"))
        assertTrue(has("it-music", "IT", "MUSIC"))
        assertTrue(has("it-sport", "IT", "SPORT"))
        assertFalse(has("al-sport", "IT", "SPORT"))
        assertFalse(has("it-sport", "AL", "SPORT"))
    }

    @Test
    fun `stable provider category ids do not become country block ordering when provider order ties`() {
        val result = LiveOwnPlayDiscoveryPolicy.discover(
            channels = listOf(
                channel("al-premium", "Premium", "MTV Live", providerCategoryId = "100"),
                channel("it-premium", "Premium", "Cartoon Network", providerCategoryId = "300"),
            ),
            providerCategoryCatalog = listOf(
                LiveOwnPlayDiscoveryProviderCategory("200", "Albania", 0),
                LiveOwnPlayDiscoveryProviderCategory("100", "Premium", 0),
                LiveOwnPlayDiscoveryProviderCategory("400", "Italia", 0),
                LiveOwnPlayDiscoveryProviderCategory("300", "Premium", 0),
            ),
        )

        assertTrue(
            result.memberships.any { membership ->
                membership.channelId == "al-premium" &&
                    membership.categoryId == "ownplay:country:AL:semantic:MUSIC"
            },
        )
        assertTrue(
            result.memberships.any { membership ->
                membership.channelId == "it-premium" &&
                    membership.categoryId == "ownplay:country:IT:semantic:KIDS"
            },
        )
        assertFalse(
            result.memberships.any { membership ->
                membership.channelId == "it-premium" && membership.categoryId.startsWith("ownplay:country:AL")
            },
        )
    }

    @Test
    fun `large country catalog stays deterministic without speculative categories`() {
        val channels = (1..5_000).map { index ->
            channel(
                id = "channel-$index",
                providerCategory = "Italy HD",
                name = "Channel $index",
            )
        }

        val result = LiveOwnPlayDiscoveryPolicy.discover(channels)

        assertEquals(
            setOf("ownplay:country:IT", "ownplay:country:IT:semantic:GENERAL"),
            result.categories.map { it.categoryId }.toSet(),
        )
        assertEquals(10_000, result.memberships.size)
        assertEquals(10_000, result.memberships.map { it.categoryId to it.channelId }.distinct().size)
        assertTrue(result.automaticMarkerChannelIds.isEmpty())
        assertEquals(5_000, result.unclassifiedChannelIds.size)
    }

    @Test
    fun `empty input stays empty`() {
        val result = LiveOwnPlayDiscoveryPolicy.discover(emptyList())

        assertTrue(result.categories.isEmpty())
        assertTrue(result.memberships.isEmpty())
        assertTrue(result.automaticMarkerChannelIds.isEmpty())
        assertTrue(result.reviewMarkerCandidates.isEmpty())
        assertTrue(result.unclassifiedChannelIds.isEmpty())
    }

    private fun channel(
        id: String,
        providerCategory: String?,
        name: String,
        tvgId: String? = null,
        hasLogo: Boolean = false,
        providerCategoryId: String? = providerCategory,
    ) = LiveOwnPlayDiscoveryChannel(
        channelId = id,
        providerCategoryName = providerCategory,
        providerCategoryId = providerCategoryId,
        name = name,
        tvgId = tvgId,
        hasLogo = hasLogo,
    )
}
