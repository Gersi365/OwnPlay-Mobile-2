package app.ownplay.mobile.sources.data

import app.ownplay.mobile.data.security.SourceSecret
import app.ownplay.mobile.sources.data.m3u.M3uClient
import app.ownplay.mobile.sources.data.m3u.M3uClientException
import app.ownplay.mobile.sources.data.m3u.M3uClientFailureCategory
import app.ownplay.mobile.sources.data.xtream.XtreamClient
import app.ownplay.mobile.sources.data.xtream.XtreamClientException
import app.ownplay.mobile.sources.data.xtream.XtreamClientFailureCategory
import app.ownplay.mobile.sources.data.xtream.XtreamConnection
import app.ownplay.mobile.sources.data.xtream.XtreamLiveStreamIdentity
import app.ownplay.mobile.sources.domain.SourceId
import app.ownplay.mobile.sources.domain.SourceRefreshFailureCategory
import app.ownplay.mobile.sources.domain.SourceType
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class DefaultSourceCatalogLoader(
    private val xtreamClient: XtreamClient,
    private val m3uClient: M3uClient,
) : SourceCatalogLoader {
    override suspend fun load(
        sourceId: SourceId,
        sourceType: SourceType,
        baseLocator: String,
        secret: SourceSecret,
    ): ProviderCatalogSnapshot = withContext(Dispatchers.Default) {
        try {
            when (sourceType) {
                SourceType.XTREAM -> loadXtream(sourceId, baseLocator, secret)
                SourceType.M3U -> loadM3u(sourceId, secret)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: CatalogLoadException) {
            throw error
        } catch (error: XtreamClientException) {
            throw CatalogLoadException(error.category.toRefreshCategory(), error)
        } catch (error: M3uClientException) {
            throw CatalogLoadException(error.category.toRefreshCategory(), error)
        } catch (error: ProviderTransportException) {
            throw CatalogLoadException(error.toRefreshCategory(), error)
        } catch (error: Exception) {
            throw CatalogLoadException(SourceRefreshFailureCategory.UNKNOWN, error)
        }
    }

    private suspend fun loadXtream(
        sourceId: SourceId,
        baseLocator: String,
        secret: SourceSecret,
    ): ProviderCatalogSnapshot = coroutineScope {
        val credential = secret as? SourceSecret.Xtream
            ?: throw CatalogLoadException(SourceRefreshFailureCategory.AUTHENTICATION)
        val connection = XtreamConnection(
            baseUrl = baseLocator,
            username = credential.username,
            password = credential.password,
        )

        val liveCategoriesDeferred = async { xtreamClient.liveCategories(connection) }
        val liveStreamsDeferred = async { xtreamClient.liveStreams(connection) }
        val movieCategoriesDeferred = async { xtreamClient.movieCategories(connection) }
        val moviesDeferred = async { xtreamClient.movies(connection) }
        val seriesCategoriesDeferred = async { xtreamClient.seriesCategories(connection) }
        val seriesDeferred = async { xtreamClient.series(connection) }

        val liveCategories = liveCategoriesDeferred.await()
        val liveStreams = liveStreamsDeferred.await()
        val movieCategories = movieCategoriesDeferred.await()
        val movies = moviesDeferred.await()
        val seriesCategories = seriesCategoriesDeferred.await()
        val series = seriesDeferred.await()

        ProviderCatalogSnapshot(
            sourceType = SourceType.XTREAM,
            categories = buildList {
                addAll(liveCategories.map { category ->
                    ProviderCategoryRecord(
                        kind = KIND_LIVE,
                        providerKey = category.providerCategoryId,
                        name = category.name,
                        providerOrder = category.providerOrder,
                    )
                })
                addAll(movieCategories.map { category ->
                    ProviderCategoryRecord(
                        kind = KIND_MOVIE,
                        providerKey = category.providerCategoryId,
                        name = category.name,
                        providerOrder = category.providerOrder,
                    )
                })
                addAll(seriesCategories.map { category ->
                    ProviderCategoryRecord(
                        kind = KIND_SERIES,
                        providerKey = category.providerCategoryId,
                        name = category.name,
                        providerOrder = category.providerOrder,
                    )
                })
            }.distinctBy { it.kind to it.providerKey },
            liveChannels = liveStreams.map { stream ->
                ProviderLiveChannelRecord(
                    proposedChannelId = StableIdentity.xtreamLiveChannel(sourceId, stream.streamId),
                    providerKey = stream.streamId,
                    providerStreamId = stream.streamId,
                    categoryProviderKey = stream.categoryId,
                    name = stream.name,
                    tvgId = stream.tvgId,
                    tvgName = stream.name,
                    logoUrl = stream.logoUrl,
                    streamLocator = XtreamLiveStreamIdentity.encode(
                        streamId = stream.streamId,
                        containerExtension = stream.containerExtension,
                    ),
                    providerOrder = stream.providerOrder,
                )
            }.distinctBy(ProviderLiveChannelRecord::proposedChannelId),
            movies = movies.map { movie ->
                ProviderMovieRecord(
                    proposedMovieId = StableIdentity.xtreamMovie(sourceId, movie.streamId),
                    providerStreamId = movie.streamId,
                    categoryProviderKey = movie.categoryId,
                    name = movie.name,
                    posterUrl = movie.posterUrl,
                    backdropUrl = null,
                    extension = movie.containerExtension,
                    rating = movie.rating,
                    providerOrder = movie.providerOrder,
                )
            }.distinctBy(ProviderMovieRecord::proposedMovieId),
            series = series.map { item ->
                ProviderSeriesRecord(
                    proposedSeriesId = StableIdentity.xtreamSeries(sourceId, item.seriesId),
                    providerSeriesId = item.seriesId,
                    categoryProviderKey = item.categoryId,
                    name = item.name,
                    posterUrl = item.posterUrl,
                    backdropUrl = null,
                    description = item.description,
                    rating = item.rating,
                    providerOrder = item.providerOrder,
                )
            }.distinctBy(ProviderSeriesRecord::proposedSeriesId),
        )
    }

    private suspend fun loadM3u(
        sourceId: SourceId,
        secret: SourceSecret,
    ): ProviderCatalogSnapshot {
        val credential = secret as? SourceSecret.M3uRemote
            ?: throw CatalogLoadException(SourceRefreshFailureCategory.AUTHENTICATION)
        val parsed = m3uClient.fetch(credential.playlistUrl)
        if (parsed.entries.isEmpty()) {
            throw CatalogLoadException(SourceRefreshFailureCategory.INVALID_PAYLOAD)
        }

        val normalizedEntries = parsed.entries.map { entry ->
            val group = entry.groupTitle?.trim()?.takeIf(String::isNotBlank) ?: PROVIDER_OTHER
            Triple(entry, group, entry.streamUrl.trim())
        }
        val tvgCounts = normalizedEntries
            .mapNotNull { (entry, _, _) -> entry.tvgId?.trim()?.takeIf(String::isNotBlank) }
            .groupingBy { it }
            .eachCount()

        val categories = linkedMapOf<String, Int>()
        normalizedEntries.forEach { (_, group, _) ->
            if (group !in categories) categories[group] = categories.size
        }

        val channels = normalizedEntries.mapIndexed { index, (entry, group, locator) ->
            val stableTvgId = entry.tvgId?.trim()?.takeIf { tvgCounts[it] == 1 }
            ProviderLiveChannelRecord(
                proposedChannelId = StableIdentity.m3uLiveChannel(
                    sourceId = sourceId,
                    tvgId = stableTvgId,
                    stableLocatorHint = locator,
                    normalizedName = entry.name,
                    normalizedGroup = group,
                ),
                providerKey = stableTvgId ?: locator,
                providerStreamId = null,
                categoryProviderKey = group,
                name = entry.name,
                tvgId = entry.tvgId,
                tvgName = entry.tvgName,
                logoUrl = entry.logoUrl,
                streamLocator = locator,
                providerOrder = index,
            )
        }.distinctBy(ProviderLiveChannelRecord::proposedChannelId)

        return ProviderCatalogSnapshot(
            sourceType = SourceType.M3U,
            categories = categories.map { (name, order) ->
                ProviderCategoryRecord(
                    kind = KIND_LIVE,
                    providerKey = name,
                    name = name,
                    providerOrder = order,
                )
            },
            liveChannels = channels,
            movies = emptyList(),
            series = emptyList(),
        )
    }

    private fun XtreamClientFailureCategory.toRefreshCategory(): SourceRefreshFailureCategory =
        when (this) {
            XtreamClientFailureCategory.AUTHENTICATION -> SourceRefreshFailureCategory.AUTHENTICATION
            XtreamClientFailureCategory.PROVIDER -> SourceRefreshFailureCategory.PROVIDER
            XtreamClientFailureCategory.INVALID_PAYLOAD -> SourceRefreshFailureCategory.INVALID_PAYLOAD
        }

    private fun M3uClientFailureCategory.toRefreshCategory(): SourceRefreshFailureCategory =
        when (this) {
            M3uClientFailureCategory.AUTHENTICATION -> SourceRefreshFailureCategory.AUTHENTICATION
            M3uClientFailureCategory.PROVIDER -> SourceRefreshFailureCategory.PROVIDER
            M3uClientFailureCategory.INVALID_PAYLOAD -> SourceRefreshFailureCategory.INVALID_PAYLOAD
        }

    private fun ProviderTransportException.toRefreshCategory(): SourceRefreshFailureCategory {
        if (cause is SocketTimeoutException) return SourceRefreshFailureCategory.TIMEOUT
        return when (category) {
            ProviderTransportFailureCategory.INVALID_REQUEST -> SourceRefreshFailureCategory.INVALID_PAYLOAD
            ProviderTransportFailureCategory.NETWORK -> SourceRefreshFailureCategory.NETWORK
            ProviderTransportFailureCategory.RESPONSE_TOO_LARGE -> SourceRefreshFailureCategory.INVALID_PAYLOAD
            ProviderTransportFailureCategory.HTTP_ERROR -> SourceRefreshFailureCategory.PROVIDER
        }
    }

    companion object {
        const val KIND_LIVE = "LIVE"
        const val KIND_MOVIE = "MOVIE"
        const val KIND_SERIES = "SERIES"
        private const val PROVIDER_OTHER = "Other"
    }
}
