package app.ownplay.mobile.sources.data

import app.ownplay.mobile.sources.data.m3u.M3uClient
import app.ownplay.mobile.sources.data.m3u.M3uParser
import app.ownplay.mobile.sources.data.m3u.M3uResult
import app.ownplay.mobile.sources.data.xtream.XtreamClient
import app.ownplay.mobile.sources.data.xtream.XtreamResult
import app.ownplay.mobile.sources.domain.Source
import app.ownplay.mobile.sources.domain.SourceCredential
import app.ownplay.mobile.sources.domain.SourceType
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.Dispatchers
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

        val liveCategoriesResult = xtreamClient.liveCategories(source.baseLocator, xtreamCredential)
        val liveStreamsResult = xtreamClient.liveStreams(source.baseLocator, xtreamCredential)
        val vodCategoriesResult = xtreamClient.vodCategories(source.baseLocator, xtreamCredential)
        val vodStreamsResult = xtreamClient.vodStreams(source.baseLocator, xtreamCredential)
        val seriesCategoriesResult = xtreamClient.seriesCategories(source.baseLocator, xtreamCredential)
        val seriesResult = xtreamClient.series(source.baseLocator, xtreamCredential)

        val liveCategoryMap = categoryIdMap(source.sourceId, "LIVE", liveCategoriesResult)
        val vodCategoryMap = categoryIdMap(source.sourceId, "MOVIE", vodCategoriesResult)
        val seriesCategoryMap = categoryIdMap(source.sourceId, "SERIES", seriesCategoriesResult)

        return ProviderRefreshPayload(
            liveCategories = mapCategories(source.sourceId, "LIVE", liveCategoriesResult),
            liveChannels = mapXtreamResult(liveStreamsResult) { streams ->
                streams.map { stream ->
                    ProviderLiveChannelRecord(
                        channelId = StableIdentity.xtreamContentId(source.sourceId, "live", stream.streamId),
                        providerKey = stream.streamId,
                        providerStreamId = stream.streamId,
                        categoryKey = stream.categoryId?.let(liveCategoryMap::get),
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
                        categoryKey = movie.categoryId?.let(vodCategoryMap::get),
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
                        categoryKey = item.categoryId?.let(seriesCategoryMap::get),
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
                val tvgCounts = resolvedEntries
                    .mapNotNull { (entry, _) -> entry.tvgId?.trim()?.takeIf(String::isNotEmpty) }
                    .groupingBy { it }
                    .eachCount()

                val groupNames = linkedSetOf<String>()
                resolvedEntries.forEach { (entry, _) ->
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
                val channels = resolvedEntries.mapIndexed { index, (entry, resolvedLocator) ->
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
                    liveCategories = RemoteSection.success(categories),
                    liveChannels = RemoteSection.success(channels),
                    vodCategories = RemoteSection.skipped(),
                    movies = RemoteSection.skipped(),
                    seriesCategories = RemoteSection.skipped(),
                    series = RemoteSection.skipped(),
                )
            }
        }
    }

    private fun categoryIdMap(
        sourceId: String,
        kind: String,
        result: XtreamResult<List<app.ownplay.mobile.sources.data.xtream.XtreamCategory>>,
    ): Map<String, String> = when (result) {
        is XtreamResult.Failure -> emptyMap()
        is XtreamResult.Success -> result.value.associate { category ->
            category.providerKey to StableIdentity.categoryId(sourceId, kind, category.providerKey)
        }
    }

    private fun mapCategories(
        sourceId: String,
        kind: String,
        result: XtreamResult<List<app.ownplay.mobile.sources.data.xtream.XtreamCategory>>,
    ): RemoteSection<List<ProviderCategoryRecord>> = mapXtreamResult(result) { categories ->
        categories.map { category ->
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
        is XtreamResult.Success -> RemoteSection.success(mapper(result.value))
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
        val resolved = URI(baseLocator).resolve(raw.trim())
        val scheme = resolved.scheme?.lowercase(Locale.US) ?: return null
        if (scheme.isBlank()) null else resolved.normalize().toString()
    } catch (_: Exception) {
        null
    }
}
