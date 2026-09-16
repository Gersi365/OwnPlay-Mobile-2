package app.ownplay.mobile.feature.live.domain

import java.text.Normalizer
import java.util.Locale

data class LiveOwnPlayDiscoveryChannel(
    val channelId: String,
    val providerCategoryName: String?,
    val name: String,
    val tvgName: String? = null,
    val tvgId: String? = null,
    val currentEpgTitle: String? = null,
    val hasLogo: Boolean = false,
)

data class LiveOwnPlayDiscoveryProfile(
    val forcedMarkers: Map<String, String> = emptyMap(),
    val forcedNormalChannels: Set<String> = emptySet(),
)

data class LiveOwnPlayDiscoveryCategory(
    val categoryId: String,
    val parentCategoryId: String?,
    val displayName: String,
    val semanticKey: String,
    val confidence: LiveClassificationConfidence,
    val evidenceKeys: Set<String>,
)

data class LiveOwnPlayDiscoveryMembership(
    val categoryId: String,
    val channelId: String,
    val confidence: LiveClassificationConfidence,
    val evidenceKeys: Set<String>,
)

data class LiveOwnPlayMarkerCandidate(
    val channelId: String,
    val semanticKey: String,
    val confidence: LiveClassificationConfidence,
    val evidenceKeys: Set<String>,
)

data class LiveOwnPlayDiscoveryResult(
    val categories: List<LiveOwnPlayDiscoveryCategory>,
    val memberships: List<LiveOwnPlayDiscoveryMembership>,
    val automaticMarkerChannelIds: Set<String>,
    val reviewMarkerCandidates: List<LiveOwnPlayMarkerCandidate>,
    val unclassifiedChannelIds: List<String>,
)

object LiveOwnPlayDiscoveryPolicy {
    private data class SemanticDefinition(
        val key: String,
        val displayName: String,
        val parentKey: String? = null,
        val aliases: Set<String>,
    )

    private data class CountryDefinition(
        val code: String,
        val displayName: String,
        val aliases: Set<String>,
    )

    private data class MarkerDecision(
        val semantic: SemanticDefinition,
        val confidence: LiveClassificationConfidence,
        val evidence: Set<String>,
    )

    private data class MembershipDraft(
        val categoryId: String,
        val channelId: String,
        val confidence: LiveClassificationConfidence,
        val evidence: Set<String>,
    )

    private val technicalTokens = setOf(
        "SD",
        "HD",
        "FHD",
        "UHD",
        "4K",
        "8K",
        "HEVC",
        "H265",
        "H264",
        "HDR",
    )

    private val semanticDefinitions = listOf(
        SemanticDefinition(
            "GENERAL",
            "General",
            aliases = setOf(
                "GENERAL", "GENERALE", "GENERALISTE", "TE PERGJITHSHME", "PERGJITHSHME", "ALLGEMEIN",
                "GERAL", "ΓΕΝΙΚΑ", "GENEL", "OGOLNE", "ALGEMEEN", "OBECNE", "VSEOBECNE", "ALTALANOS",
                "OPĆENITO", "OPSTI", "SPLOSNO", "ОБЩИ", "ОБЩИЕ", "ЗАГАЛЬНІ",
            ),
        ),
        SemanticDefinition(
            "FILM",
            "Film",
            aliases = setOf(
                "FILM", "FILMA", "FILMS", "MOVIES", "MOVIE", "CINEMA", "CINE", "PELICULA", "PELICULAS",
                "FILME", "KINO", "FILMES", "ΤΑΙΝΙΕΣ", "ΣΙΝΕΜΑ", "FILMLER", "SINEMA", "FILMY", "FILMEK",
                "MOZI", "FILMOVI", "FILMI", "ФИЛМИ", "ФИЛЬМЫ", "ФІЛЬМИ",
            ),
        ),
        SemanticDefinition(
            "SPORT",
            "Sport",
            aliases = setOf("SPORT", "SPORTS", "SPORTE", "ΑΘΛΗΤΙΚΑ", "SPOR", "СПОРТ"),
        ),
        SemanticDefinition("DAZN", "DAZN", parentKey = "SPORT", aliases = setOf("DAZN")),
        SemanticDefinition(
            "FOOTBALL",
            "Football",
            parentKey = "SPORT",
            aliases = setOf(
                "FOOTBALL", "FUTBOLL", "CALCIO", "FUSSBALL", "FUTBOL", "FUTEBOL", "ΠΟΔΟΣΦΑΙΡΟ", "FOTBAL",
                "PILKA NOZNA", "VOETBAL", "LABDARUGAS", "NOGOMET", "FUDBAL", "ФУТБОЛ",
            ),
        ),
        SemanticDefinition("SERIE_A", "Serie A", parentKey = "SPORT", aliases = setOf("SERIE A")),
        SemanticDefinition("SERIE_B", "Serie B", parentKey = "SPORT", aliases = setOf("SERIE B")),
        SemanticDefinition(
            "NEWS",
            "News",
            aliases = setOf(
                "NEWS", "LAJME", "NOTIZIE", "NACHRICHTEN", "NOUVELLES", "INFORMATIONS", "ACTUALITES",
                "NOTICIAS", "NOTICIAS 24H", "HABER", "HABERLER", "STIRI", "WIADOMOSCI", "NIEUWS", "ZPRAVY",
                "SPRAVY", "HIREK", "VIJESTI", "VESTI", "NOVICE", "ΕΙΔΗΣΕΙΣ", "НОВИНИ", "НОВОСТИ",
            ),
        ),
        SemanticDefinition(
            "KIDS",
            "Kids",
            aliases = setOf(
                "KIDS", "FEMIJE", "BAMBINI", "KINDER", "ENFANTS", "NINOS", "INFANTIL", "CRIANCAS", "ΠΑΙΔΙΚΑ",
                "COCUK", "COPII", "DZIECI", "KINDEREN", "DETI", "GYEREKEK", "DJECA", "DECA", "OTROCI",
                "ДЕЦА", "ДЕТИ", "ДІТИ",
            ),
        ),
        SemanticDefinition(
            "MUSIC",
            "Music",
            aliases = setOf(
                "MUSIC", "MUZIKE", "MUZIKA", "MUSICA", "MUSIQUE", "MUSIK", "ΜΟΥΣΙΚΗ", "MUZIK", "MUZICA",
                "MUZYKA", "MUZIEK", "HUDBA", "ZENE", "GLASBA", "МУЗИКА", "МУЗЫКА",
            ),
        ),
    )

    private val semanticByKey = semanticDefinitions.associateBy { it.key }

    private val explicitCountryAliases = mapOf(
        "SHQIP" to "AL",
        "SHQIPERI" to "AL",
        "ITALIA" to "IT",
        "DEUTSCHLAND" to "DE",
        "ESPANA" to "ES",
        "FRANCIA" to "FR",
        "PORTUGAL" to "PT",
    )

    private val countries: List<CountryDefinition> by lazy {
        val availableLocales = Locale.getAvailableLocales()
        Locale.getISOCountries().map { code ->
            val countryLocale = Locale.Builder().setRegion(code).build()
            val displayName = countryLocale.getDisplayCountry(Locale.ENGLISH)
            val aliases = buildSet {
                add(code)
                normalizeText(displayName).takeIf(String::isNotBlank)?.let(::add)
                availableLocales.forEach { locale ->
                    normalizeText(countryLocale.getDisplayCountry(locale))
                        .takeIf(String::isNotBlank)
                        ?.let(::add)
                }
                explicitCountryAliases
                    .filterValues { it == code }
                    .keys
                    .forEach(::add)
            }
            CountryDefinition(
                code = code,
                displayName = displayName,
                aliases = aliases,
            )
        }
    }

    private val countryByAlias by lazy {
        countries
            .asSequence()
            .flatMap { country -> country.aliases.asSequence().map { alias -> alias to country } }
            .groupBy({ (alias, _) -> alias }, { (_, country) -> country })
            .mapNotNull { (alias, matches) ->
                matches.distinctBy(CountryDefinition::code).singleOrNull()?.let { country -> alias to country }
            }
            .toMap()
    }

    fun discover(
        channels: List<LiveOwnPlayDiscoveryChannel>,
        profile: LiveOwnPlayDiscoveryProfile = LiveOwnPlayDiscoveryProfile(),
    ): LiveOwnPlayDiscoveryResult {
        if (channels.isEmpty()) {
            return LiveOwnPlayDiscoveryResult(
                categories = emptyList(),
                memberships = emptyList(),
                automaticMarkerChannelIds = emptySet(),
                reviewMarkerCandidates = emptyList(),
                unclassifiedChannelIds = emptyList(),
            )
        }

        val automaticMarkers = linkedMapOf<String, MarkerDecision>()
        val reviewMarkers = mutableListOf<LiveOwnPlayMarkerCandidate>()
        channels.forEach { channel ->
            val decision = markerDecision(channel, profile)
            when (decision?.confidence) {
                LiveClassificationConfidence.HIGH -> automaticMarkers[channel.channelId] = decision
                LiveClassificationConfidence.MEDIUM -> reviewMarkers += LiveOwnPlayMarkerCandidate(
                    channelId = channel.channelId,
                    semanticKey = decision.semantic.key,
                    confidence = decision.confidence,
                    evidenceKeys = decision.evidence,
                )
                else -> Unit
            }
        }

        val drafts = mutableListOf<MembershipDraft>()
        var activeProviderCategory: String? = null
        var activeMarker: MarkerDecision? = null

        channels.forEach { channel ->
            val providerCategory = channel.providerCategoryName.orEmpty()
            if (providerCategory != activeProviderCategory) {
                activeProviderCategory = providerCategory
                activeMarker = null
            }

            val automaticMarker = automaticMarkers[channel.channelId]
            if (automaticMarker != null) {
                activeMarker = automaticMarker
                return@forEach
            }

            val country = countryForProviderCategory(channel.providerCategoryName) ?: return@forEach
            drafts += MembershipDraft(
                categoryId = countryCategoryId(country.code),
                channelId = channel.channelId,
                confidence = LiveClassificationConfidence.HIGH,
                evidence = setOf("provider-country:${country.code}"),
            )

            activeMarker?.let { marker ->
                semanticPath(marker.semantic).forEach { semantic ->
                    drafts += MembershipDraft(
                        categoryId = semanticCategoryId(country.code, semantic.key),
                        channelId = channel.channelId,
                        confidence = marker.confidence,
                        evidence = marker.evidence + "section-marker",
                    )
                }
            }

            val providerSemanticEvidence = providerCategorySemanticEvidence(channel.providerCategoryName)
            providerSemanticEvidence.forEach { (semantic, confidence, evidence) ->
                semanticPath(semantic).forEach { pathSemantic ->
                    drafts += MembershipDraft(
                        categoryId = semanticCategoryId(country.code, pathSemantic.key),
                        channelId = channel.channelId,
                        confidence = confidence,
                        evidence = evidence,
                    )
                }
            }
            if (providerSemanticEvidence.isEmpty()) {
                localProviderCategorySemantic(country, channel.providerCategoryName)?.let { semantic ->
                    drafts += MembershipDraft(
                        categoryId = semanticCategoryId(country.code, semantic.key),
                        channelId = channel.channelId,
                        confidence = LiveClassificationConfidence.HIGH,
                        evidence = setOf("provider-category-local:${semantic.key.removePrefix(LOCAL_SEMANTIC_PREFIX)}"),
                    )
                }
            }

            directSemanticEvidence(channel).forEach { (semantic, confidence, evidence) ->
                semanticPath(semantic).forEach { pathSemantic ->
                    drafts += MembershipDraft(
                        categoryId = semanticCategoryId(country.code, pathSemantic.key),
                        channelId = channel.channelId,
                        confidence = confidence,
                        evidence = evidence,
                    )
                }
            }
        }

        val memberships = mergeMemberships(drafts)
        val categories = memberships
            .groupBy { it.categoryId }
            .mapNotNull { (categoryId, memberRows) -> categoryDefinition(categoryId, memberRows) }
            .sortedWith(compareBy({ it.parentCategoryId.orEmpty() }, { it.displayName.lowercase(Locale.ROOT) }))

        val semanticMembershipChannelIds = memberships
            .asSequence()
            .filter { it.categoryId.contains(SEMANTIC_CATEGORY_MARKER) }
            .map { it.channelId }
            .toSet()
        val unclassified = channels
            .asSequence()
            .filterNot { it.channelId in automaticMarkers }
            .filterNot { it.channelId in semanticMembershipChannelIds }
            .map { it.channelId }
            .toList()

        return LiveOwnPlayDiscoveryResult(
            categories = categories,
            memberships = memberships,
            automaticMarkerChannelIds = automaticMarkers.keys,
            reviewMarkerCandidates = reviewMarkers,
            unclassifiedChannelIds = unclassified,
        )
    }

    fun normalizedIdentity(value: String): String = normalizeText(value)
        .split(' ')
        .filterNot { token -> token in technicalTokens }
        .joinToString(" ")
        .trim()

    private fun markerDecision(
        channel: LiveOwnPlayDiscoveryChannel,
        profile: LiveOwnPlayDiscoveryProfile,
    ): MarkerDecision? {
        if (channel.channelId in profile.forcedNormalChannels) return null

        profile.forcedMarkers[channel.channelId]?.let { semanticKey ->
            val semantic = semanticByKey[semanticKey] ?: return null
            return MarkerDecision(
                semantic = semantic,
                confidence = LiveClassificationConfidence.HIGH,
                evidence = setOf("source-profile-marker", "semantic:${semantic.key}"),
            )
        }

        val semantic = markerSemantic(channel.name) ?: return null
        val decorativeCount = channel.name.count { character ->
            !character.isLetterOrDigit() && !character.isWhitespace()
        }
        if (decorativeCount < MIN_DECORATIVE_MARKER_CHARACTERS) return null

        var score = 2
        val evidence = linkedSetOf<String>()
        evidence += "decorative-row"
        evidence += "semantic:${semantic.key}"
        if (channel.currentEpgTitle.isNullOrBlank() && channel.tvgId.isNullOrBlank()) {
            score += 1
            evidence += "no-epg-identity"
        }
        if (!channel.hasLogo) {
            score += 1
            evidence += "no-logo"
        }
        if (normalizedIdentity(channel.name).split(' ').size <= 4) {
            score += 1
            evidence += "short-label"
        }

        val confidence = when {
            score >= 4 -> LiveClassificationConfidence.HIGH
            score == 3 -> LiveClassificationConfidence.MEDIUM
            else -> LiveClassificationConfidence.LOW
        }
        return MarkerDecision(semantic, confidence, evidence)
    }

    private fun markerSemantic(rawName: String): SemanticDefinition? {
        val normalized = normalizedIdentity(rawName)
        if (normalized.isBlank()) return null
        return semanticDefinitions.firstOrNull { definition ->
            definition.aliases.any { alias ->
                val normalizedAlias = normalizeText(alias)
                normalized == normalizedAlias || normalized.endsWith(" $normalizedAlias")
            }
        }
    }

    private fun providerCategorySemanticEvidence(
        providerCategoryName: String?,
    ): List<Triple<SemanticDefinition, LiveClassificationConfidence, Set<String>>> {
        val normalized = normalizedIdentity(providerCategoryName.orEmpty())
        if (normalized.isBlank()) return emptyList()
        return semanticDefinitions.mapNotNull { semantic ->
            val matchedAlias = semantic.aliases
                .asSequence()
                .map(::normalizeText)
                .filter(String::isNotBlank)
                .sortedByDescending(String::length)
                .firstOrNull { alias -> containsPhrase(normalized, alias) }
                ?: return@mapNotNull null
            Triple(
                semantic,
                LiveClassificationConfidence.HIGH,
                setOf("provider-category:${semantic.key.lowercase(Locale.ROOT)}", "provider-category-alias:$matchedAlias"),
            )
        }
    }

    private fun localProviderCategorySemantic(
        country: CountryDefinition,
        providerCategoryName: String?,
    ): SemanticDefinition? {
        val normalized = normalizedIdentity(providerCategoryName.orEmpty())
        if (normalized.isBlank()) return null
        val countryAlias = country.aliases
            .asSequence()
            .map(::normalizeText)
            .filter(String::isNotBlank)
            .distinct()
            .sortedByDescending(String::length)
            .firstOrNull { alias -> containsPhrase(normalized, alias) }
            ?: return null
        val residual = " $normalized "
            .replace(" $countryAlias ", " ")
            .trim()
            .replace(Regex("\\s+"), " ")
        if (residual.isBlank()) return null
        return SemanticDefinition(
            key = LOCAL_SEMANTIC_PREFIX + residual.replace(' ', '_'),
            displayName = localSemanticDisplayName(residual),
            aliases = setOf(residual),
        )
    }

    private fun localSemanticDisplayName(normalized: String): String = normalized
        .lowercase(Locale.ROOT)
        .split(' ')
        .filter(String::isNotBlank)
        .joinToString(" ") { token ->
            token.replaceFirstChar { character -> character.titlecase(Locale.ROOT) }
        }

    private fun directSemanticEvidence(
        channel: LiveOwnPlayDiscoveryChannel,
    ): List<Triple<SemanticDefinition, LiveClassificationConfidence, Set<String>>> {
        val identity = normalizedIdentity(
            listOfNotNull(channel.name, channel.tvgName)
                .joinToString(" "),
        )
        val epg = normalizeText(channel.currentEpgTitle.orEmpty())
        val results = linkedMapOf<String, Triple<SemanticDefinition, LiveClassificationConfidence, Set<String>>>()

        fun add(key: String, confidence: LiveClassificationConfidence, evidence: String) {
            val semantic = semanticByKey.getValue(key)
            val previous = results[key]
            if (previous == null || confidence.rank > previous.second.rank) {
                results[key] = Triple(semantic, confidence, setOf(evidence))
            } else if (confidence == previous.second) {
                results[key] = Triple(semantic, confidence, previous.third + evidence)
            }
        }

        if (containsPhrase(identity, "DAZN")) {
            add("DAZN", LiveClassificationConfidence.HIGH, "channel-name:dazn")
        }
        if (containsPhrase(identity, "EUROSPORT") || containsPhrase(identity, "SKY SPORT")) {
            add("SPORT", LiveClassificationConfidence.HIGH, "channel-name:sport-brand")
        }
        if (containsPhrase(identity, "SERIE A")) {
            add("SERIE_A", LiveClassificationConfidence.HIGH, "channel-name:serie-a")
        }
        if (containsPhrase(identity, "SERIE B")) {
            add("SERIE_B", LiveClassificationConfidence.HIGH, "channel-name:serie-b")
        }
        if (containsPhrase(epg, "SERIE A")) {
            add("SERIE_A", LiveClassificationConfidence.HIGH, "epg:serie-a")
        }
        if (containsPhrase(epg, "SERIE B")) {
            add("SERIE_B", LiveClassificationConfidence.HIGH, "epg:serie-b")
        }
        if (containsPhrase(epg, "CHAMPIONS LEAGUE") || containsPhrase(epg, "EUROPA LEAGUE")) {
            add("FOOTBALL", LiveClassificationConfidence.HIGH, "epg:football-competition")
        }
        if (containsPhrase(identity, "CINEMA") || identity.split(' ').any { it.startsWith("CINE") }) {
            add("FILM", LiveClassificationConfidence.MEDIUM, "channel-name:cinema")
        }

        return results.values.toList()
    }

    private fun semanticPath(semantic: SemanticDefinition): List<SemanticDefinition> {
        val parent = semantic.parentKey?.let(semanticByKey::get)
        return if (parent == null) listOf(semantic) else listOf(parent, semantic)
    }

    private fun mergeMemberships(drafts: List<MembershipDraft>): List<LiveOwnPlayDiscoveryMembership> = drafts
        .groupBy { it.categoryId to it.channelId }
        .map { (_, rows) ->
            val strongest = rows.maxBy { it.confidence.rank }
            LiveOwnPlayDiscoveryMembership(
                categoryId = strongest.categoryId,
                channelId = strongest.channelId,
                confidence = strongest.confidence,
                evidenceKeys = rows.flatMapTo(linkedSetOf()) { it.evidence },
            )
        }
        .sortedWith(compareBy({ it.categoryId }, { it.channelId }))

    private fun categoryDefinition(
        categoryId: String,
        members: List<LiveOwnPlayDiscoveryMembership>,
    ): LiveOwnPlayDiscoveryCategory? {
        if (members.isEmpty() || !categoryId.startsWith(COUNTRY_CATEGORY_PREFIX)) return null
        val tail = categoryId.removePrefix(COUNTRY_CATEGORY_PREFIX)
        val semanticMarkerIndex = tail.indexOf(SEMANTIC_CATEGORY_MARKER)
        if (semanticMarkerIndex < 0) {
            val country = countries.firstOrNull { it.code == tail } ?: return null
            return LiveOwnPlayDiscoveryCategory(
                categoryId = categoryId,
                parentCategoryId = null,
                displayName = country.displayName,
                semanticKey = COUNTRY_SEMANTIC_KEY,
                confidence = members.maxBy { it.confidence.rank }.confidence,
                evidenceKeys = members.flatMapTo(linkedSetOf()) { it.evidenceKeys },
            )
        }

        val countryCode = tail.substring(0, semanticMarkerIndex)
        val semanticKey = tail.substring(semanticMarkerIndex + SEMANTIC_CATEGORY_MARKER.length)
        val country = countries.firstOrNull { it.code == countryCode } ?: return null
        val semantic = semanticByKey[semanticKey]
            ?: semanticKey
                .takeIf { it.startsWith(LOCAL_SEMANTIC_PREFIX) }
                ?.removePrefix(LOCAL_SEMANTIC_PREFIX)
                ?.replace('_', ' ')
                ?.takeIf(String::isNotBlank)
                ?.let { localKey ->
                    SemanticDefinition(
                        key = semanticKey,
                        displayName = localSemanticDisplayName(localKey),
                        aliases = setOf(localKey),
                    )
                }
            ?: return null
        val parentCategoryId = semantic.parentKey
            ?.let { parentKey -> semanticCategoryId(country.code, parentKey) }
            ?: countryCategoryId(country.code)
        return LiveOwnPlayDiscoveryCategory(
            categoryId = categoryId,
            parentCategoryId = parentCategoryId,
            displayName = semantic.displayName,
            semanticKey = semantic.key,
            confidence = members.maxBy { it.confidence.rank }.confidence,
            evidenceKeys = members.flatMapTo(linkedSetOf()) { it.evidenceKeys },
        )
    }

    private fun countryForProviderCategory(providerCategoryName: String?): CountryDefinition? {
        val normalized = normalizedIdentity(providerCategoryName.orEmpty())
        if (normalized.isBlank()) return null
        val tokens = normalized.split(' ').filter(String::isNotBlank)
        val maxWords = minOf(tokens.size, MAX_COUNTRY_ALIAS_WORDS)
        for (wordCount in maxWords downTo 1) {
            for (start in 0..tokens.size - wordCount) {
                val alias = tokens.subList(start, start + wordCount).joinToString(" ")
                countryByAlias[alias]?.let { return it }
            }
        }
        return null
    }

    private fun countryCategoryId(countryCode: String): String = "$COUNTRY_CATEGORY_PREFIX$countryCode"

    private fun semanticCategoryId(countryCode: String, semanticKey: String): String =
        "$COUNTRY_CATEGORY_PREFIX$countryCode$SEMANTIC_CATEGORY_MARKER$semanticKey"

    private fun containsPhrase(value: String, phrase: String): Boolean {
        if (value.isBlank() || phrase.isBlank()) return false
        return " $value ".contains(" $phrase ")
    }

    private fun normalizeText(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{M}+"), "")
            .uppercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    private val LiveClassificationConfidence.rank: Int
        get() = when (this) {
            LiveClassificationConfidence.LOW -> 0
            LiveClassificationConfidence.MEDIUM -> 1
            LiveClassificationConfidence.HIGH -> 2
        }

    private const val MIN_DECORATIVE_MARKER_CHARACTERS = 4
    private const val MAX_COUNTRY_ALIAS_WORDS = 6
    private const val COUNTRY_CATEGORY_PREFIX = "ownplay:country:"
    private const val SEMANTIC_CATEGORY_MARKER = ":semantic:"
    private const val LOCAL_SEMANTIC_PREFIX = "LOCAL_"
    private const val COUNTRY_SEMANTIC_KEY = "COUNTRY"
}
