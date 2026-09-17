package app.ownplay.mobile.sources.data

import app.ownplay.mobile.sources.data.m3u.M3uClient
import app.ownplay.mobile.sources.data.m3u.M3uParser
import app.ownplay.mobile.sources.data.m3u.M3uResult
import app.ownplay.mobile.sources.data.xtream.XtreamClient
import app.ownplay.mobile.sources.data.xtream.XtreamLiveCategoryAttribution
import app.ownplay.mobile.sources.data.xtream.XtreamLiveStream
import app.ownplay.mobile.sources.data.xtream.XtreamResult
import app.ownplay.mobile.sources.domain.Source
import app.ownplay.mobile.sources.domain.SourceCredential
import app.ownplay.mobile.sources.domain.ProviderCategoryVisibility
import app.ownplay.mobile.sources.domain.SourceType
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class SourceCatalogLoader(
    private val xtreamClient: XtreamClient,
    private val m3uClient: M3uClient,
    private val m3uParser: M3uParser,
) {
    suspend fun load(
        source: Source,
        credential: SourceCredential,
    ): ProviderRefreshPayload = withContext(Dispatchers.Default) {
        when (source.type) {
            SourceType.XTREAM -> loadXtream(source, credential)
            SourceType.M3U -> loadM3u(source, credential)
        }
    }

    private suspend fun loadXtream(
        source: Source,
        credential: SourceCredential,
    ): ProviderRefreshPayload {
        val xtreamCredential = credential as? SourceCredential.Xtream
            ?: return failedPayload("CREDENTIAL_TYPE")

        return coroutineScope {
            // These six catalog sections are independent. Launch them together and let the
            // shared OkHttp dispatcher enforce the existing per-host request ceiling.
            val liveCategoriesDeferred = async {
                xtreamClient.liveCategories(source.baseLocator, xtreamCredential)
            }
            val globalLiveStreamsDeferred = async {
                xtreamClient.liveStreams(source.baseLocator, xtreamCredential)
            }
            val vodCategoriesDeferred = async {
                xtreamClient.vodCategories(source.baseLocator, xtreamCredential)
            }
            val vodStreamsDeferred = async {
                xtreamClient.vodStreams(source.baseLocator, xtreamCredential)
            }
            val seriesCategoriesDeferred = async {
                xtreamClient.seriesCategories(source.baseLocator, xtreamCredential)
            }
            val seriesDeferred = async {
                xtreamClient.series(source.baseLocator, xtreamCredential)
            }

            val liveCategoriesResult = liveCategoriesDeferred.await()
            val globalLiveStreamsResult = globalLiveStreamsDeferred.await()
            val vodCategoriesResult = vodCategoriesDeferred.await()
            val vodStreamsResult = vodStreamsDeferred.await()
            val seriesCategoriesResult = seriesCategoriesDeferred.await()
            val seriesResult = seriesDeferred.await()

            val liveStreamsResult = recoverLiveCategoryAttribution(
                source = source,
                credential = xtreamCredential,
                categoriesResult = liveCategoriesResult,
                streamsResult = globalLiveStreamsResult,
            )
            val liveCategoryMap = categoryIdMap(source.sourceId, "LIVE", liveCategoriesResult)
            val vodCategoryMap = categoryIdMap(source.sourceId, "MOVIE", vodCategoriesResult)
            val seriesCategoryMap = categoryIdMap(source.sourceId, "SERIES", seriesCategoriesResult)

            ProviderRefreshPayload(
                liveCategories = mapCategories(source.sourceId, "LIVE", liveCategoriesResult),
                liveChannels = mapXtreamResult(liveStreamsResult) { streams ->
                    streams.map { stream ->
                        ProviderLiveChannelRecord(
                            channelId = StableIdentity.xtreamContentId(source.sourceId, "live", stream.streamId),
                            providerKey = stream.streamId,
                            providerStreamId = stream.streamId,
                            categoryKey = contentCategoryKey(
                                source.sourceId, "LIVE", stream.categoryId, liveCategoriesResult, liveCategoryMap,
                            ),
                            name = stream.name,
                            tvgId = stream.epgChannelId,
                            tvgName = stream.name,
                            logoUrl = stream.streamIcon,
                            streamLocator = "xtream://live/${stream.streamId}",
                            providerOrder = stream.providerOrder,
                        )
                    }
                },
                vodCategories = mapCategories(source.sourceId, "MOVIE", vodCategoriesResult),
                movies = mapXtreamResult(vodStreamsResult) { movies ->
                    movies.map { movie ->
                        ProviderMovieRecord(
                            movieId = StableIdentity.xtreamContentId(source.sourceId, "movie", movie.streamId),
                            providerStreamId = movie.streamId,
                            categoryKey = contentCategoryKey(
                                source.sourceId, "MOVIE", movie.categoryId, vodCategoriesResult, vodCategoryMap,
                            ),
                            name = movie.name,
                            posterUrl = movie.posterUrl,
                            backdropUrl = null,
                            extension = movie.extension,
                            rating = movie.rating,
                            providerOrder = movie.providerOrder,
                        )
                    }
                },
                seriesCategories = mapCategories(source.sourceId, "SERIES", seriesCategoriesResult),
                series = mapXtreamResult(seriesResult) { series ->
                    series.map { item ->
                        ProviderSeriesRecord(
                            seriesId = StableIdentity.xtreamContentId(source.sourceId, "series", item.seriesId),
                            providerSeriesId = item.seriesId,
                            categoryKey = contentCategoryKey(
                                source.sourceId, "SERIES", item.categoryId, seriesCategoriesResult, seriesCategoryMap,
                            ),
                            name = item.name,
                            posterUrl = item.posterUrl,
                            backdropUrl = item.backdropUrl,
                            description = item.description,
                            rating = item.rating,
                            providerOrder = item.providerOrder,
                        )
                    }
                },
            )
        }
    }

    private suspend fun loadM3u(
        source: Source,
        credential: SourceCredential,
    ): ProviderRefreshPayload {
        val locator = (credential as? SourceCredential.M3uRemoteLocator)?.locator
            ?: return failedPayload("CREDENTIAL_TYPE")
        return when (val fetch = m3uClient.fetch(locator)) {
            is M3uResult.Failure -> failedPayload(fetch.code)
            is M3uResult.Success -> {
                val parsed = m3uParser.parse(fetch.value)
                val resolvedEntries = parsed.entries.mapNotNull { entry ->
                    resolveLocator(locator, entry.streamLocator)?.let { resolved -> entry to resolved }
                }
                val hasPlaylistHeader = fetch.value.lineSequence().any { line ->
                    val trimmed = line.removePrefix("\uFEFF").trim()
                    trimmed.equals("#EXTM3U", ignoreCase = true) ||
                        trimmed.startsWith("#EXTM3U ", ignoreCase = true) ||
                        trimmed.startsWith("#EXTM3U\t", ignoreCase = true)
                }
                val hasStructuralLoss = parsed.diagnostics.any {
                    it.code == "MISSING_STREAM_LOCATOR" || it.code == "ORPHAN_STREAM_LOCATOR" ||
                        it.code == "MALFORMED_EXTINF"
                } || resolvedEntries.size < parsed.entries.size
                if (resolvedEntries.isEmpty() && (!hasPlaylistHeader || hasStructuralLoss)) {
                    return failedPayload("M3U_PLAYLIST_FORMAT")
                }
                val visibleEntries = ProviderPayloadValidation.distinctM3uByLocator(
                    resolvedEntries.filterNot { (entry, _) ->
                        ProviderCategoryVisibility.isUtilityLabel(entry.groupTitle.orEmpty())
                    },
                )
                // Repeated rows for the same channel must not demote a unique tvg-id to a URL identity.
                val tvgCounts = visibleEntries
                    .distinctBy { (entry, resolved) -> entry.tvgId?.trim() to resolved }
                    .mapNotNull { (entry, _) -> entry.tvgId?.trim()?.takeIf(String::isNotEmpty) }
                    .groupingBy { it }
                    .eachCount()

                val groupNames = linkedSetOf<String>()
                visibleEntries.forEach { (entry, _) ->
                    groupNames += entry.groupTitle?.trim()?.takeIf(String::isNotEmpty) ?: "Other"
                }
                val categories = groupNames.mapIndexed { index, groupName ->
                    ProviderCategoryRecord(
                        categoryId = StableIdentity.categoryId(source.sourceId, "LIVE", groupName),
                        providerKey = groupName,
                        name = groupName,
                        providerOrder = index,
                    )
                }
                val categoryByName = categories.associateBy({ it.providerKey }, { it.categoryId })
                val channels = visibleEntries.mapIndexed { index, (entry, resolvedLocator) ->
                    val group = entry.groupTitle?.trim()?.takeIf(String::isNotEmpty) ?: "Other"
                    val uniqueTvgId = entry.tvgId?.trim()?.let { tvgCounts[it] == 1 } == true
                    ProviderLiveChannelRecord(
                        channelId = StableIdentity.m3uChannelId(
                            sourceId = source.sourceId,
                            tvgId = entry.tvgId,
                            tvgIdIsUnique = uniqueTvgId,
                            normalizedStreamLocator = resolvedLocator,
                            fallbackName = entry.displayName,
                            fallbackGroup = group,
                        ),
                        providerKey = entry.tvgId?.takeIf { uniqueTvgId } ?: resolvedLocator,
                        providerStreamId = null,
                        categoryKey = categoryByName[group],
                        name = entry.displayName,
                        tvgId = entry.tvgId,
                        tvgName = entry.tvgName,
                        logoUrl = entry.logoUrl,
                        streamLocator = resolvedLocator,
                        providerOrder = index,
                    )
                }.distinctBy(ProviderLiveChannelRecord::channelId)

                ProviderRefreshPayload(
                    liveCategories = if (hasStructuralLoss) RemoteSection.partial(categories, "M3U_PARTIAL_PLAYLIST")
                        else RemoteSection.success(categories),
                    liveChannels = if (hasStructuralLoss) RemoteSection.partial(channels, "M3U_PARTIAL_PLAYLIST")
                        else RemoteSection.success(channels),
                    vodCategories = RemoteSection.skipped(),
                    movies = RemoteSection.skipped(),
                    seriesCategories = RemoteSection.skipped(),
                    series = RemoteSection.skipped(),
                )
            }
        }
    }

    private suspend fun recoverLiveCategoryAttribution(
        source: Source,
        credential: SourceCredential.Xtream,
        categoriesResult: XtreamResult<List<app.ownplay.mobile.sources.data.xtream.XtreamCategory>>,
        streamsResult: XtreamResult<List<XtreamLiveStream>>,
    ): XtreamResult<List<XtreamLiveStream>> {
        val categoriesSuccess = categoriesResult as? XtreamResult.Success ?: return streamsResult
        if (categoriesSuccess.warningCode != null) return streamsResult
        val categoryRows = categoriesSuccess.value
            .filterNot { category -> ProviderCategoryVisibility.isUtilityLabel(category.name) }
        val streamsSuccess = streamsResult as? XtreamResult.Success ?: return streamsResult
        val globalRows = streamsSuccess.value
        val categoryIds = categoryRows
            .mapNotNull { category ->
                XtreamLiveCategoryAttribution.normalizeProviderCategoryId(category.providerKey)
            }
            .distinct()
        val normalizedGlobalRows = globalRows.map { stream ->
            stream.copy(
                categoryId = XtreamLiveCategoryAttribution.normalizeProviderCategoryId(stream.categoryId),
            )
        }

        if (!XtreamLiveCategoryAttribution.needsRecovery(
                knownCategoryIds = categoryIds,
                streamCategoryIds = normalizedGlobalRows.map(XtreamLiveStream::categoryId),
            )
        ) {
            return XtreamResult.Success(normalizedGlobalRows, streamsSuccess.warningCode)
        }

        val categoryResults = coroutineScope {
            val requestGate = Semaphore(XTREAM_CATEGORY_RECOVERY_CONCURRENCY)
            categoryIds.map { categoryId ->
                async {
                    requestGate.withPermit {
                        categoryId to xtreamClient.liveStreams(
                            baseUrl = source.baseLocator,
                            credential = credential,
                            categoryId = categoryId,
                        )
                    }
                }
            }.awaitAll()
        }

        // Missing category responses cannot prove that a stream belongs to exactly one category.
        if (categoryResults.any { (_, result) ->
                result !is XtreamResult.Success || result.warningCode != null
            }
        ) return XtreamResult.Success(normalizedGlobalRows, "XTREAM_CATEGORY_RECOVERY_PARTIAL")

        // Some Xtream servers ignore category_id and return the complete catalog for every request.
        // Only attribute a stream when category-scoped responses place that stream in exactly one category.
        val membershipByStreamId = linkedMapOf<String, MutableSet<String>>()
        val sampleByStreamId = linkedMapOf<String, XtreamLiveStream>()
        categoryResults.forEach { (categoryId, result) ->
            val rows = (result as XtreamResult.Success).value
            rows.forEach { stream ->
                membershipByStreamId.getOrPut(stream.streamId) { linkedSetOf() }.add(categoryId)
                sampleByStreamId.putIfAbsent(stream.streamId, stream)
            }
        }
        val recoveredCategoryByStreamId = membershipByStreamId.mapNotNull { (streamId, memberships) ->
            memberships.singleOrNull()?.let { categoryId -> streamId to categoryId }
        }.toMap()
        if (recoveredCategoryByStreamId.isEmpty()) {
            return XtreamResult.Success(normalizedGlobalRows, streamsSuccess.warningCode)
        }

        val knownCategoryIds = categoryIds.toSet()
        val mergedIds = linkedSetOf<String>()
        val merged = normalizedGlobalRows.map { stream ->
            mergedIds += stream.streamId
            val existingCategory = stream.categoryId?.takeIf(knownCategoryIds::contains)
            stream.copy(
                categoryId = existingCategory ?: recoveredCategoryByStreamId[stream.streamId],
            )
        }.toMutableList()

        recoveredCategoryByStreamId.forEach { (streamId, categoryId) ->
            if (mergedIds.add(streamId)) {
                sampleByStreamId[streamId]?.let { sample ->
                    merged += sample.copy(categoryId = categoryId)
                }
            }
        }
        return XtreamResult.Success(merged, streamsSuccess.warningCode)
    }

    private fun contentCategoryKey(
        sourceId: String,
        kind: String,
        rawProviderKey: String?,
        categories: XtreamResult<List<app.ownplay.mobile.sources.data.xtream.XtreamCategory>>,
        knownCategoryIds: Map<String, String>,
    ): String? {
        val providerKey = XtreamLiveCategoryAttribution.normalizeProviderCategoryId(rawProviderKey) ?: return null
        knownCategoryIds[providerKey]?.let { return it }
        if (categories is XtreamResult.Success) {
            if (categories.value.any {
                    it.providerKey == providerKey && ProviderCategoryVisibility.isUtilityLabel(it.name)
                }
            ) return null
            if (categories.warningCode == null) return null
        }
        // The category endpoint may fail independently. Keep the existing deterministic relation.
        return StableIdentity.categoryId(sourceId, kind, providerKey)
    }

    private fun categoryIdMap(
        sourceId: String,
        kind: String,
        result: XtreamResult<List<app.ownplay.mobile.sources.data.xtream.XtreamCategory>>,
    ): Map<String, String> = when (result) {
        is XtreamResult.Failure -> emptyMap()
        is XtreamResult.Success -> result.value
            .filterNot { category -> ProviderCategoryVisibility.isUtilityLabel(category.name) }
            .mapNotNull { category ->
                val providerKey = XtreamLiveCategoryAttribution
                    .normalizeProviderCategoryId(category.providerKey)
                    ?: return@mapNotNull null
                providerKey to StableIdentity.categoryId(sourceId, kind, providerKey)
            }
            .toMap()
    }

    private fun mapCategories(
        sourceId: String,
        kind: String,
        result: XtreamResult<List<app.ownplay.mobile.sources.data.xtream.XtreamCategory>>,
    ): RemoteSection<List<ProviderCategoryRecord>> = mapXtreamResult(result) { categories ->
        categories
            .filterNot { category -> ProviderCategoryVisibility.isUtilityLabel(category.name) }
            .map { category ->
                ProviderCategoryRecord(
                categoryId = StableIdentity.categoryId(sourceId, kind, category.providerKey),
                providerKey = category.providerKey,
                name = category.name,
                providerOrder = category.providerOrder,
            )
        }
    }

    private fun <T, R> mapXtreamResult(
        result: XtreamResult<T>,
        mapper: (T) -> R,
    ): RemoteSection<R> = when (result) {
        is XtreamResult.Failure -> RemoteSection.failed(result.code)
        is XtreamResult.Success -> result.warningCode?.let { RemoteSection.partial(mapper(result.value), it) }
            ?: RemoteSection.success(mapper(result.value))
    }

    private companion object {
        const val XTREAM_CATEGORY_RECOVERY_CONCURRENCY = 4
    }

    private fun failedPayload(code: String): ProviderRefreshPayload = ProviderRefreshPayload(
        liveCategories = RemoteSection.failed(code),
        liveChannels = RemoteSection.failed(code),
        vodCategories = RemoteSection.failed(code),
        movies = RemoteSection.failed(code),
        seriesCategories = RemoteSection.failed(code),
        series = RemoteSection.failed(code),
    )

    private fun resolveLocator(baseLocator: String, raw: String): String? = try {
        if (raw.trim().startsWith("<")) return null
        val resolved = URI(baseLocator).resolve(raw.trim())
        val scheme = resolved.scheme?.lowercase(Locale.US) ?: return null
        if (scheme.isBlank()) null else resolved.normalize().toString()
    } catch (_: Exception) {
        null
    }
}
