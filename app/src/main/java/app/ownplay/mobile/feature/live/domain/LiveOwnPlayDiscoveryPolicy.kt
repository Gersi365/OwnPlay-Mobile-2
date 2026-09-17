package app.ownplay.mobile.feature.live.domain

import java.text.Normalizer
import java.util.Locale

data class LiveOwnPlayDiscoveryChannel(
    val channelId: String,
    val providerCategoryName: String?,
    val providerCategoryId: String? = null,
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

data class LiveOwnPlayDiscoveryProviderCategory(
    val categoryId: String,
    val name: String,
    val providerOrder: Int,
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

    private data class ProviderCategoryContext(
        val country: CountryDefinition,
        val countryEvidence: Set<String>,
        val semanticEvidence: List<Triple<SemanticDefinition, LiveClassificationConfidence, Set<String>>>,
    )

    private data class ProviderCategoryCountryResolution(
        val country: CountryDefinition,
        val evidence: Set<String>,
    )

    private data class ProviderCategoryDescriptor(
        val id: String,
        val name: String,
    )

    private data class ChannelSemanticSignal(
        val semanticKey: String,
        val score: Int,
        val evidence: Set<String>,
    )

    private data class BrandHint(
        val semanticKey: String,
        val phrases: Set<String>,
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
                "MUSIC", "MUZIKE", "MUZIKORE", "MUZIKA", "MUSICA", "MUSIQUE", "MUSIK", "ΜΟΥΣΙΚΗ", "MUZIK", "MUZICA",
                "MUZYKA", "MUZIEK", "HUDBA", "ZENE", "GLASBA", "МУЗИКА", "МУЗЫКА",
            ),
        ),
        SemanticDefinition(
            "DOCUMENTARY",
            "Documentary",
            aliases = setOf("DOCUMENTARY", "DOKUMENTAR", "DOKUMENTARE"),
        ),
        SemanticDefinition(
            "ENTERTAINMENT",
            "Entertainment",
            aliases = setOf("ENTERTAINMENT", "ARGETIM", "ARGETUESE"),
        ),
        SemanticDefinition(
            "CULTURE",
            "Culture",
            aliases = setOf("CULTURE", "KULTURE", "KULTURORE"),
        ),
    )

    private val semanticByKey = semanticDefinitions.associateBy { it.key }

    private val brandHints = listOf(
        BrandHint("MUSIC", setOf("MTV", "VH1", "TRACE", "VEVO", "MUSIC BOX", "DELUXE MUSIC", "4MUSIC")),
        BrandHint("KIDS", setOf("CARTOON NETWORK", "NICKELODEON", "NICK JR", "DISNEY JUNIOR", "DISNEY CHANNEL", "BABY TV", "BOOMERANG", "MINIMAX", "DUCK TV", "JUNIOR")),
        BrandHint("NEWS", setOf("CNN", "BBC NEWS", "EURONEWS", "BLOOMBERG", "AL JAZEERA", "FRANCE 24", "DW NEWS", "SKY NEWS", "CNBC")),
        BrandHint("DOCUMENTARY", setOf("DISCOVERY", "NATIONAL GEOGRAPHIC", "NAT GEO", "HISTORY", "ANIMAL PLANET", "BBC EARTH", "VIASAT NATURE", "VIASAT HISTORY", "DOCUBOX")),
        BrandHint("SPORT", setOf("EUROSPORT", "ESPN", "DAZN", "BEIN SPORTS", "SKY SPORT", "SUPERSPORT", "ARENA SPORT", "SPORT TV", "ELEVEN SPORTS")),
        BrandHint("FILM", setOf("HBO", "CINEMAX", "FILMBOX", "SKY CINEMA", "AMC", "MOVIE CHANNEL")),
        BrandHint("CULTURE", setOf("ARTE", "MEZZO")),
    )

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
                runCatching { countryLocale.getISO3Country() }
                    .getOrNull()
                    ?.let(::normalizeText)
                    ?.takeIf(String::isNotBlank)
                    ?.let(::add)
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

    private val countryByCode by lazy { countries.associateBy(CountryDefinition::code) }

    fun discover(
        channels: List<LiveOwnPlayDiscoveryChannel>,
        profile: LiveOwnPlayDiscoveryProfile = LiveOwnPlayDiscoveryProfile(),
        providerCategoryCatalog: List<LiveOwnPlayDiscoveryProviderCategory> = emptyList(),
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

        val reviewMarkerChannelIds = reviewMarkers.mapTo(hashSetOf()) { marker -> marker.channelId }
        val markerCandidateChannelIds = automaticMarkers.keys + reviewMarkerChannelIds
        val contentSignalCache = hashMapOf<String, List<ChannelSemanticSignal>>()
        fun contentSignals(country: CountryDefinition, channel: LiveOwnPlayDiscoveryChannel): List<ChannelSemanticSignal> =
            contentSignalCache.getOrPut(channel.channelId) { channelSemanticSignals(country, channel) }
        val providerCategories = mutableListOf<ProviderCategoryDescriptor>()
        val channelsByProviderCategory = linkedMapOf<String, MutableList<LiveOwnPlayDiscoveryChannel>>()
        val seenProviderCategoryIds = hashSetOf<String>()
        providerCategoryCatalog
            .sortedWith(compareBy(LiveOwnPlayDiscoveryProviderCategory::providerOrder, LiveOwnPlayDiscoveryProviderCategory::categoryId))
            .forEach { category ->
                val categoryId = category.categoryId.trim().takeIf(String::isNotEmpty) ?: return@forEach
                if (seenProviderCategoryIds.add(categoryId)) {
                    providerCategories += ProviderCategoryDescriptor(
                        id = categoryId,
                        name = category.name,
                    )
                }
            }
        for (channel in channels) {
            val categoryId = providerCategoryIdentity(channel)
            channelsByProviderCategory.getOrPut(categoryId) { mutableListOf() }.add(channel)
            if (seenProviderCategoryIds.add(categoryId)) {
                providerCategories += ProviderCategoryDescriptor(
                    id = categoryId,
                    name = channel.providerCategoryName.orEmpty(),
                )
            }
        }
        val countryResolutions = resolveProviderCategoryCountries(providerCategories)
        val providerCategoryContexts = linkedMapOf<String, ProviderCategoryContext>()
        for (providerCategory in providerCategories) {
            val resolution = countryResolutions[providerCategory.id] ?: continue
            val country = resolution.country
            val translated = providerCategorySemanticEvidence(country, providerCategory.name)
            val semanticEvidence = if (translated.isNotEmpty()) {
                translated
            } else {
                inferProviderCategorySemanticEvidence(
                    channels = channelsByProviderCategory[providerCategory.id]
                        .orEmpty()
                        .filterNot { channel -> channel.channelId in markerCandidateChannelIds },
                    signalProvider = { channel -> contentSignals(country, channel) },
                )
            }
            providerCategoryContexts[providerCategory.id] = ProviderCategoryContext(
                country = country,
                countryEvidence = resolution.evidence,
                semanticEvidence = semanticEvidence,
            )
        }

        val drafts = mutableListOf<MembershipDraft>()
        var activeProviderCategory: String? = null
        var activeMarker: MarkerDecision? = null

        channels.forEach { channel ->
            val providerCategory = providerCategoryIdentity(channel)
            if (providerCategory != activeProviderCategory) {
                activeProviderCategory = providerCategory
                activeMarker = null
            }

            val automaticMarker = automaticMarkers[channel.channelId]
            if (automaticMarker != null) {
                activeMarker = automaticMarker
                return@forEach
            }

            val categoryContext = providerCategoryContexts[providerCategory] ?: return@forEach
            val country = categoryContext.country
            drafts += MembershipDraft(
                categoryId = countryCategoryId(country.code),
                channelId = channel.channelId,
                confidence = LiveClassificationConfidence.HIGH,
                evidence = categoryContext.countryEvidence,
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

            categoryContext.semanticEvidence.forEach { (semantic, confidence, evidence) ->
                semanticPath(semantic).forEach { pathSemantic ->
                    drafts += MembershipDraft(
                        categoryId = semanticCategoryId(country.code, pathSemantic.key),
                        channelId = channel.channelId,
                        confidence = confidence,
                        evidence = evidence,
                    )
                }
            }

            if (channel.channelId !in reviewMarkerChannelIds) {
                directSemanticEvidence(contentSignals(country, channel)).forEach { (semantic, confidence, evidence) ->
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
        }

        val semanticallyResolvedChannelIds = drafts
            .asSequence()
            .filter { draft -> draft.categoryId.contains(SEMANTIC_CATEGORY_MARKER) }
            .map { draft -> draft.channelId }
            .toSet()
        val fallbackGeneralChannelIds = linkedSetOf<String>()
        channels.forEach { channel ->
            if (
                channel.channelId in automaticMarkers ||
                channel.channelId in reviewMarkerChannelIds ||
                channel.channelId in semanticallyResolvedChannelIds
            ) return@forEach
            val categoryContext = providerCategoryContexts[providerCategoryIdentity(channel)] ?: return@forEach
            drafts += MembershipDraft(
                categoryId = semanticCategoryId(categoryContext.country.code, "GENERAL"),
                channelId = channel.channelId,
                confidence = LiveClassificationConfidence.LOW,
                evidence = setOf("fallback-general:unresolved-semantic"),
            )
            fallbackGeneralChannelIds += channel.channelId
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
            .filter { channel ->
                channel.channelId in fallbackGeneralChannelIds ||
                    channel.channelId !in semanticMembershipChannelIds
            }
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

        val decorativeCount = channel.name.count { character ->
            !character.isLetterOrDigit() && !character.isWhitespace()
        }
        if (decorativeCount < MIN_DECORATIVE_MARKER_CHARACTERS) return null
        val semantic = markerSemantic(channel) ?: return null

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

    private fun markerSemantic(channel: LiveOwnPlayDiscoveryChannel): SemanticDefinition? {
        val country = countryForProviderCategory(channel.providerCategoryName) ?: return null
        val normalized = normalizedIdentity(channel.name)
        if (normalized.isBlank()) return null
        return LiveCategorySemanticTranslator.translate(country.code, normalized)
            .asSequence()
            .mapNotNull { translation -> semanticByKey[translation.semanticKey] }
            .firstOrNull()
    }

    private fun providerCategorySemanticEvidence(
        country: CountryDefinition,
        providerCategoryName: String?,
    ): List<Triple<SemanticDefinition, LiveClassificationConfidence, Set<String>>> {
        val normalized = normalizedIdentity(providerCategoryName.orEmpty())
        if (normalized.isBlank()) return emptyList()
        return LiveCategorySemanticTranslator.translate(country.code, normalized).mapNotNull { translation ->
            val semantic = semanticByKey[translation.semanticKey] ?: return@mapNotNull null
            Triple(
                semantic,
                LiveClassificationConfidence.HIGH,
                setOf(
                    "provider-category:${semantic.key.lowercase(Locale.ROOT)}",
                    "provider-category-translation:${translation.matchedText}",
                    "provider-category-language:${translation.languageCode}",
                    "provider-category-translation-strategy:${translation.strategy}",
                ),
            )
        }
    }

    private fun inferProviderCategorySemanticEvidence(
        channels: List<LiveOwnPlayDiscoveryChannel>,
        signalProvider: (LiveOwnPlayDiscoveryChannel) -> List<ChannelSemanticSignal>,
    ): List<Triple<SemanticDefinition, LiveClassificationConfidence, Set<String>>> {
        if (channels.isEmpty()) return emptyList()
        val bySemantic = linkedMapOf<String, MutableList<ChannelSemanticSignal>>()
        channels.forEach { channel ->
            signalProvider(channel)
                .filterNot { signal -> signal.semanticKey == "GENERAL" }
                .forEach { signal -> bySemantic.getOrPut(signal.semanticKey, ::mutableListOf) += signal }
        }
        if (bySemantic.isEmpty()) return emptyList()

        data class Aggregate(
            val semanticKey: String,
            val support: Int,
            val score: Int,
            val evidence: Set<String>,
        )

        val ranked = bySemantic.map { (semanticKey, signals) ->
            Aggregate(
                semanticKey = semanticKey,
                support = signals.size,
                score = signals.sumOf(ChannelSemanticSignal::score),
                evidence = signals.flatMapTo(linkedSetOf()) { signal -> signal.evidence },
            )
        }.sortedWith(
            compareByDescending<Aggregate> { aggregate -> aggregate.support }
                .thenByDescending { aggregate -> aggregate.score }
                .thenByDescending { aggregate -> semanticDepth(aggregate.semanticKey) },
        )

        val winner = ranked.first()
        val minimumSupport = when (channels.size) {
            1 -> 1
            2 -> 2
            else -> maxOf(2, kotlin.math.ceil(channels.size * CONTENT_INFERENCE_MIN_RATIO).toInt())
        }
        if (winner.support < minimumSupport) return emptyList()
        if (channels.size == 1 && winner.score < STRONG_CONTENT_SIGNAL_SCORE) return emptyList()

        val runnerUp = ranked.getOrNull(1)
        if (runnerUp != null &&
            winner.support == runnerUp.support &&
            winner.score < runnerUp.score + CONTENT_INFERENCE_MIN_SCORE_MARGIN
        ) {
            return emptyList()
        }

        val semantic = semanticByKey[winner.semanticKey] ?: return emptyList()
        val supportRatio = winner.support.toDouble() / channels.size.toDouble()
        val confidence = if (
            supportRatio >= CONTENT_INFERENCE_HIGH_RATIO ||
            winner.score >= winner.support * STRONG_CONTENT_SIGNAL_SCORE
        ) {
            LiveClassificationConfidence.HIGH
        } else {
            LiveClassificationConfidence.MEDIUM
        }
        return listOf(
            Triple(
                semantic,
                confidence,
                winner.evidence + setOf(
                    "provider-category-content-inference:${semantic.key.lowercase(Locale.ROOT)}",
                    "provider-category-content-support:${winner.support}/${channels.size}",
                ),
            ),
        )
    }

    private fun directSemanticEvidence(
        signals: List<ChannelSemanticSignal>,
    ): List<Triple<SemanticDefinition, LiveClassificationConfidence, Set<String>>> {
        return signals.mapNotNull { signal ->
            val semantic = semanticByKey[signal.semanticKey] ?: return@mapNotNull null
            val confidence = if (signal.score >= STRONG_CONTENT_SIGNAL_SCORE) {
                LiveClassificationConfidence.HIGH
            } else {
                LiveClassificationConfidence.MEDIUM
            }
            Triple(semantic, confidence, signal.evidence)
        }
    }

    private fun channelSemanticSignals(
        country: CountryDefinition,
        channel: LiveOwnPlayDiscoveryChannel,
    ): List<ChannelSemanticSignal> {
        val results = linkedMapOf<String, ChannelSemanticSignal>()

        fun add(semanticKey: String, score: Int, evidence: String) {
            if (semanticKey !in semanticByKey) return
            val previous = results[semanticKey]
            if (previous == null || score > previous.score) {
                results[semanticKey] = ChannelSemanticSignal(semanticKey, score, setOf(evidence))
            } else {
                results[semanticKey] = previous.copy(evidence = previous.evidence + evidence)
            }
        }

        val identity = normalizedIdentity(
            listOfNotNull(channel.name, channel.tvgName, channel.tvgId)
                .joinToString(" "),
        )
        LiveCategorySemanticTranslator.translate(country.code, identity)
            .filterNot { translation -> translation.semanticKey == "GENERAL" }
            .forEach { translation ->
                add(
                    translation.semanticKey,
                    STRONG_CONTENT_SIGNAL_SCORE,
                    "channel-translation:${translation.languageCode}:${translation.strategy}:${translation.matchedText}",
                )
            }

        val epg = normalizeText(channel.currentEpgTitle.orEmpty())
        LiveCategorySemanticTranslator.translate(country.code, epg).forEach { translation ->
            add(
                translation.semanticKey,
                EPG_CONTENT_SIGNAL_SCORE,
                "epg-translation:${translation.languageCode}:${translation.matchedText}",
            )
        }

        brandHints.forEach { hint ->
            hint.phrases.firstOrNull { phrase -> containsPhrase(identity, normalizeText(phrase)) }?.let { phrase ->
                add(hint.semanticKey, BRAND_CONTENT_SIGNAL_SCORE, "channel-brand:${normalizeText(phrase)}")
            }
        }

        if (containsPhrase(identity, "DAZN")) {
            add("DAZN", STRONG_CONTENT_SIGNAL_SCORE + 1, "channel-name:dazn")
        }
        if (containsPhrase(identity, "SERIE A")) {
            add("SERIE_A", STRONG_CONTENT_SIGNAL_SCORE + 1, "channel-name:serie-a")
        }
        if (containsPhrase(identity, "SERIE B")) {
            add("SERIE_B", STRONG_CONTENT_SIGNAL_SCORE + 1, "channel-name:serie-b")
        }
        if (containsPhrase(epg, "SERIE A")) {
            add("SERIE_A", STRONG_CONTENT_SIGNAL_SCORE + 1, "epg:serie-a")
        }
        if (containsPhrase(epg, "SERIE B")) {
            add("SERIE_B", STRONG_CONTENT_SIGNAL_SCORE + 1, "epg:serie-b")
        }
        if (containsPhrase(epg, "CHAMPIONS LEAGUE") || containsPhrase(epg, "EUROPA LEAGUE")) {
            add("FOOTBALL", STRONG_CONTENT_SIGNAL_SCORE + 1, "epg:football-competition")
        }
        if (containsPhrase(identity, "CINEMA") || identity.split(' ').any { token -> token.startsWith("CINE") }) {
            add("FILM", BRAND_CONTENT_SIGNAL_SCORE, "channel-name:cinema")
        }

        return results.values.toList()
    }

    private fun semanticDepth(semanticKey: String): Int {
        var depth = 0
        var current = semanticByKey[semanticKey]
        while (current?.parentKey != null) {
            depth += 1
            current = semanticByKey[current.parentKey]
        }
        return depth
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
        val semantic = semanticByKey[semanticKey] ?: return null
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

    private fun resolveProviderCategoryCountries(
        providerCategories: List<ProviderCategoryDescriptor>,
    ): Map<String, ProviderCategoryCountryResolution> {
        if (providerCategories.isEmpty()) return emptyMap()

        val explicit = ArrayList<CountryDefinition?>(providerCategories.size)
        val knownByCode = linkedMapOf<String, CountryDefinition>()
        for (category in providerCategories) {
            val country = countryForProviderCategory(category.name)
            explicit += country
            if (country != null) knownByCode.putIfAbsent(country.code, country)
        }
        if (knownByCode.isEmpty()) return emptyMap()
        val knownCountries = knownByCode.values.toList()
        val result = linkedMapOf<String, ProviderCategoryCountryResolution>()

        for (index in providerCategories.indices) {
            val category = providerCategories[index]
            val explicitCountry = explicit[index]
            if (explicitCountry != null) {
                result[category.id] = ProviderCategoryCountryResolution(
                    country = explicitCountry,
                    evidence = setOf(
                        "provider-country:${explicitCountry.code}",
                        "provider-country-resolution:explicit",
                    ),
                )
                continue
            }

            val normalizedName = normalizedIdentity(category.name)
            var localMatch: CountryDefinition? = null
            var localMatchCount = 0
            if (normalizedName.isNotBlank()) {
                for (country in knownCountries) {
                    val translations = LiveCategorySemanticTranslator.translate(country.code, normalizedName)
                    var hasLocalLanguageMatch = false
                    for (translation in translations) {
                        if (translation.languageCode != "en") {
                            hasLocalLanguageMatch = true
                            break
                        }
                    }
                    if (hasLocalLanguageMatch) {
                        localMatch = country
                        localMatchCount += 1
                    }
                }
            }
            if (localMatchCount == 1 && localMatch != null) {
                result[category.id] = ProviderCategoryCountryResolution(
                    country = localMatch,
                    evidence = setOf(
                        "provider-country:${localMatch.code}",
                        "provider-country-resolution:local-language",
                    ),
                )
                continue
            }

            var previous: CountryDefinition? = null
            var previousIndex = index - 1
            while (previousIndex >= 0) {
                val candidate = explicit[previousIndex]
                if (candidate != null) {
                    previous = candidate
                    break
                }
                previousIndex -= 1
            }
            var next: CountryDefinition? = null
            var nextIndex = index + 1
            while (nextIndex < explicit.size) {
                val candidate = explicit[nextIndex]
                if (candidate != null) {
                    next = candidate
                    break
                }
                nextIndex += 1
            }

            val contextual = when {
                knownCountries.size == 1 -> knownCountries[0]
                previous != null -> previous
                next != null -> next
                else -> null
            }
            if (contextual != null) {
                result[category.id] = ProviderCategoryCountryResolution(
                    country = contextual,
                    evidence = setOf(
                        "provider-country:${contextual.code}",
                        "provider-country-resolution:provider-order-context",
                    ),
                )
            }
        }
        return result
    }

    private fun providerCategoryIdentity(channel: LiveOwnPlayDiscoveryChannel): String =
        channel.providerCategoryId
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: "name:${channel.providerCategoryName.orEmpty()}"

    private fun countryForProviderCategory(providerCategoryName: String?): CountryDefinition? {
        providerCategoryName?.let(::countryCodeFromFlag)?.let(countryByCode::get)?.let { return it }
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

    private fun countryCodeFromFlag(value: String): String? {
        val codePoints = value.codePoints().toArray()
        for (index in 0 until codePoints.lastIndex) {
            val first = codePoints[index]
            val second = codePoints[index + 1]
            if (first in REGIONAL_INDICATOR_A..REGIONAL_INDICATOR_Z &&
                second in REGIONAL_INDICATOR_A..REGIONAL_INDICATOR_Z
            ) {
                val code = buildString(2) {
                    append(('A'.code + first - REGIONAL_INDICATOR_A).toChar())
                    append(('A'.code + second - REGIONAL_INDICATOR_A).toChar())
                }
                if (code in countryByCode) return code
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

    private const val REGIONAL_INDICATOR_A = 0x1F1E6
    private const val REGIONAL_INDICATOR_Z = 0x1F1FF
    private const val MIN_DECORATIVE_MARKER_CHARACTERS = 4
    private const val MAX_COUNTRY_ALIAS_WORDS = 6
    private const val BRAND_CONTENT_SIGNAL_SCORE = 3
    private const val STRONG_CONTENT_SIGNAL_SCORE = 4
    private const val EPG_CONTENT_SIGNAL_SCORE = 2
    private const val CONTENT_INFERENCE_MIN_RATIO = 0.40
    private const val CONTENT_INFERENCE_HIGH_RATIO = 0.60
    private const val CONTENT_INFERENCE_MIN_SCORE_MARGIN = 3
    private const val COUNTRY_CATEGORY_PREFIX = "ownplay:country:"
    private const val SEMANTIC_CATEGORY_MARKER = ":semantic:"
    private const val COUNTRY_SEMANTIC_KEY = "COUNTRY"
}
