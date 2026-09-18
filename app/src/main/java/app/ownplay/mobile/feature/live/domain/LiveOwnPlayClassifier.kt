package app.ownplay.mobile.feature.live.domain

import java.text.Normalizer
import java.util.Locale

object LiveOwnPlayClassifier {
    const val NEUTRAL_COUNTRY_ID = "country:UNSPECIFIED"
    const val NEUTRAL_COUNTRY_DISPLAY_NAME = "Unspecified"

    fun buildAutomaticOrganization(
        providerCategories: List<ProviderLiveCategory>,
        channels: List<LiveOrganizationChannel>,
    ): AutomaticLiveOrganization {
        val categoryById = providerCategories.associateBy(ProviderLiveCategory::categoryId)
        val orderedChannels = LiveOrganizationOrdering.providerChannels(
            providerCategories = providerCategories,
            channels = channels,
        )
        val countries = linkedMapOf<String, OwnPlayCountryScope>()
        val placementByChannel = linkedMapOf<String, OwnPlayLivePlacement>()

        orderedChannels.forEach { channel ->
            val providerCategory = channel.providerCategoryId?.let(categoryById::get)
            val country = inferCountry(
                providerCategoryName = providerCategory?.displayName,
                channelName = channel.name,
                tvgName = channel.tvgName,
            )
            val scope = country ?: CountryMatch(
                countryId = NEUTRAL_COUNTRY_ID,
                displayName = NEUTRAL_COUNTRY_DISPLAY_NAME,
                isNeutralScope = true,
            )
            if (scope.countryId !in countries) {
                countries[scope.countryId] = OwnPlayCountryScope(
                    countryId = scope.countryId,
                    displayName = scope.displayName,
                    providerOrder = countries.size,
                    isNeutralScope = scope.isNeutralScope,
                )
            }
            placementByChannel[channel.channelId] = OwnPlayLivePlacement(
                countryId = scope.countryId,
                semanticCategory = inferSemanticCategory(
                    providerCategoryName = providerCategory?.displayName,
                    channelName = channel.name,
                    tvgName = channel.tvgName,
                ),
            )
        }

        return AutomaticLiveOrganization(
            countries = countries.values.toList(),
            placementByChannelId = placementByChannel,
        )
    }

    fun inferSemanticCategory(
        providerCategoryName: String?,
        channelName: String,
        tvgName: String?,
    ): OwnPlayLiveSemanticCategory {
        val normalized = normalizeForSearch(
            listOfNotNull(providerCategoryName, channelName, tvgName).joinToString(" "),
        )
        return when {
            normalized.containsAny(SPORTS_HINTS) -> OwnPlayLiveSemanticCategory.SPORTS
            normalized.containsAny(NEWS_HINTS) -> OwnPlayLiveSemanticCategory.NEWS
            normalized.containsAny(MOVIE_HINTS) -> OwnPlayLiveSemanticCategory.MOVIES
            normalized.containsAny(SERIES_HINTS) -> OwnPlayLiveSemanticCategory.SERIES
            normalized.containsAny(KIDS_HINTS) -> OwnPlayLiveSemanticCategory.KIDS
            normalized.containsAny(MUSIC_HINTS) -> OwnPlayLiveSemanticCategory.MUSIC
            normalized.containsAny(DOCUMENTARY_HINTS) -> OwnPlayLiveSemanticCategory.DOCUMENTARIES
            else -> OwnPlayLiveSemanticCategory.GENERAL
        }
    }

    private fun inferCountry(
        providerCategoryName: String?,
        channelName: String,
        tvgName: String?,
    ): CountryMatch? {
        listOfNotNull(providerCategoryName, channelName, tvgName).forEach { raw ->
            findCountry(raw)?.let { return it }
        }
        return null
    }

    private fun findCountry(raw: String): CountryMatch? {
        COUNTRY_DEFINITIONS.forEach { country ->
            if (country.iso2.length == 2 && containsUpperCode(raw, country.iso2)) {
                return country.toMatch()
            }
        }
        val normalized = normalizeForSearch(raw)
        COUNTRY_ALIASES.forEach { alias ->
            if (containsPhrase(normalized, alias.alias)) {
                return alias.country.toMatch()
            }
        }
        return null
    }

    private fun CountryDefinition.toMatch(): CountryMatch = CountryMatch(
        countryId = "country:$iso2",
        displayName = displayName,
        isNeutralScope = false,
    )

    private fun containsUpperCode(raw: String, code: String): Boolean =
        Regex("(?<![A-Za-z])${Regex.escape(code)}(?![A-Za-z])").containsMatchIn(raw)

    private fun containsPhrase(normalizedText: String, normalizedPhrase: String): Boolean =
        " $normalizedText ".contains(" $normalizedPhrase ")

    private fun String.containsAny(hints: Set<String>): Boolean =
        hints.any { hint -> containsPhrase(this, hint) }

    private fun normalizeForSearch(value: String): String = Normalizer
        .normalize(value, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
        .replace(NON_ALPHANUMERIC, " ")
        .trim()
        .replace(MULTIPLE_SPACES, " ")

    private data class CountryDefinition(
        val iso2: String,
        val displayName: String,
        val aliases: Set<String> = emptySet(),
    )

    private data class CountryAlias(
        val alias: String,
        val country: CountryDefinition,
    )

    private data class CountryMatch(
        val countryId: String,
        val displayName: String,
        val isNeutralScope: Boolean,
    )

    private val COUNTRY_DEFINITIONS: List<CountryDefinition> by lazy {
        val standard = Locale.getISOCountries().map { code ->
            val locale = Locale.Builder().setRegion(code).build()
            val displayName = locale.getDisplayCountry(Locale.ENGLISH).ifBlank { code }
            val iso3 = runCatching { locale.getISO3Country() }.getOrNull().orEmpty()
            CountryDefinition(
                iso2 = code,
                displayName = displayName,
                aliases = setOf(displayName, iso3).filter(String::isNotBlank).toSet(),
            )
        }.toMutableList()
        standard.removeAll { it.iso2 == "GB" }
        standard += CountryDefinition(
            iso2 = "GB",
            displayName = "United Kingdom",
            aliases = setOf("United Kingdom", "Britain", "British", "UK", "Great Britain"),
        )
        standard += CountryDefinition(
            iso2 = "XK",
            displayName = "Kosovo",
            aliases = setOf("Kosovo", "Kosova", "Kosove", "Kosovë"),
        )
        standard.map { country ->
            country.copy(aliases = country.aliases + LOCALIZED_ALIASES[country.iso2].orEmpty())
        }
    }

    private val COUNTRY_ALIASES: List<CountryAlias> by lazy {
        COUNTRY_DEFINITIONS
            .flatMap { country ->
                country.aliases.mapNotNull { rawAlias ->
                    normalizeForSearch(rawAlias)
                        .takeIf { it.length >= 3 }
                        ?.let { normalized -> CountryAlias(normalized, country) }
                }
            }
            .distinctBy { it.alias to it.country.iso2 }
            .sortedByDescending { it.alias.length }
    }

    private val LOCALIZED_ALIASES = mapOf(
        "AL" to setOf("Shqiperi", "Shqipëri", "Shqiperia", "Shqipëria", "Albanian"),
        "DE" to setOf("Deutschland", "Deutsch"),
        "IT" to setOf("Italia", "Italiano"),
        "ES" to setOf("España", "Espana"),
        "FR" to setOf("Français", "Francais"),
        "TR" to setOf("Türkiye", "Turkiye"),
        "GR" to setOf("Hellas", "Ellada"),
        "CH" to setOf("Schweiz", "Suisse", "Svizzera"),
        "AT" to setOf("Österreich", "Osterreich"),
        "NL" to setOf("Holland"),
        "SE" to setOf("Sverige", "Svenska"),
        "US" to setOf("USA", "United States of America", "American"),
    )

    private val SPORTS_HINTS = setOf(
        "sport", "sports", "football", "futboll", "soccer", "dazn", "calcio",
        "serie a", "serie b", "basket", "basketball", "tennis",
    )
    private val NEWS_HINTS = setOf(
        "news", "lajme", "nachrichten", "notizie", "noticias", "haber", "actualites",
    )
    private val MOVIE_HINTS = setOf(
        "movie", "movies", "film", "films", "cinema", "kino",
    )
    private val SERIES_HINTS = setOf(
        "series", "serien", "serie tv", "serial", "seriale", "telenovela",
    )
    private val KIDS_HINTS = setOf(
        "kids", "kid", "femije", "bambini", "barn", "cartoon", "junior",
    )
    private val MUSIC_HINTS = setOf(
        "music", "muzike", "musica", "musik", "mtv music",
    )
    private val DOCUMENTARY_HINTS = setOf(
        "documentary", "documentaries", "documentario", "dokumentar", "dokumentare", "dokumentari",
    )

    private val COMBINING_MARKS = Regex("\\p{M}+")
    private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
    private val MULTIPLE_SPACES = Regex("\\s+")
}

object LiveOrganizationOrdering {
    fun providerCategories(categories: List<ProviderLiveCategory>): List<ProviderLiveCategory> =
        categories.withIndex()
            .sortedWith(compareBy<IndexedValue<ProviderLiveCategory>> { it.value.providerOrder }.thenBy { it.index })
            .map(IndexedValue<ProviderLiveCategory>::value)

    fun providerChannels(
        providerCategories: List<ProviderLiveCategory>,
        channels: List<LiveOrganizationChannel>,
    ): List<LiveOrganizationChannel> {
        val categoryRank = providerCategories(providerCategories)
            .mapIndexed { index, category -> category.categoryId to index }
            .toMap()
        return channels.withIndex()
            .sortedWith(
                compareBy<IndexedValue<LiveOrganizationChannel>> {
                    it.value.providerCategoryId?.let(categoryRank::get) ?: Int.MAX_VALUE
                }.thenBy { it.value.providerOrder }
                    .thenBy { it.index },
            )
            .map(IndexedValue<LiveOrganizationChannel>::value)
    }
}

object LiveManualPlacementPolicy {
    fun effectivePlacement(
        automatic: OwnPlayLivePlacement,
        manualOverride: OwnPlayLivePlacement?,
    ): OwnPlayLivePlacement = manualOverride ?: automatic

    fun resetToAutomatic(automatic: OwnPlayLivePlacement): OwnPlayLivePlacement = automatic
}
