package app.ownplay.mobile.sources.data

import app.ownplay.mobile.sources.domain.SourceId
import app.ownplay.mobile.sources.domain.SourceRefreshFailureCategory
import app.ownplay.mobile.sources.domain.SourceType

data class ProviderCatalogSnapshot(
    val sourceType: SourceType,
    val categories: List<ProviderCategoryRecord>,
    val liveChannels: List<ProviderLiveChannelRecord>,
    val movies: List<ProviderMovieRecord>,
    val series: List<ProviderSeriesRecord>,
)

data class ProviderCategoryRecord(
    val kind: String,
    val providerKey: String,
    val name: String,
    val providerOrder: Int,
)

data class ProviderLiveChannelRecord(
    val proposedChannelId: String,
    val providerKey: String,
    val providerStreamId: String?,
    val categoryProviderKey: String?,
    val name: String,
    val tvgId: String?,
    val tvgName: String?,
    val logoUrl: String?,
    val streamLocator: String,
    val providerOrder: Int,
) {
    override fun toString(): String =
        "ProviderLiveChannelRecord(proposedChannelId=$proposedChannelId, providerKey=<redacted>, " +
            "providerStreamId=$providerStreamId, categoryProviderKey=$categoryProviderKey, " +
            "name=$name, tvgId=$tvgId, tvgName=$tvgName, logoUrl=$logoUrl, " +
            "streamLocator=<redacted>, providerOrder=$providerOrder)"
}

data class ProviderMovieRecord(
    val proposedMovieId: String,
    val providerStreamId: String,
    val categoryProviderKey: String?,
    val name: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val extension: String?,
    val rating: String?,
    val providerOrder: Int,
)

data class ProviderSeriesRecord(
    val proposedSeriesId: String,
    val providerSeriesId: String,
    val categoryProviderKey: String?,
    val name: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val description: String?,
    val rating: String?,
    val providerOrder: Int,
)

interface SourceCatalogLoader {
    suspend fun load(
        sourceId: SourceId,
        sourceType: SourceType,
        baseLocator: String,
        secret: app.ownplay.mobile.data.security.SourceSecret,
    ): ProviderCatalogSnapshot
}

class CatalogLoadException(
    val category: SourceRefreshFailureCategory,
    cause: Throwable? = null,
) : Exception(category.name, cause)
