package app.ownplay.mobile.sources.data

enum class CatalogSection {
    LIVE_CATEGORIES,
    LIVE_CHANNELS,
    VOD_CATEGORIES,
    MOVIES,
    SERIES_CATEGORIES,
    SERIES,
}

enum class SectionStatus {
    SUCCESS,
    FAILED,
    SKIPPED,
}

data class RemoteSection<T>(
    val status: SectionStatus,
    val value: T? = null,
    val errorCode: String? = null,
) {
    companion object {
        fun <T> success(value: T): RemoteSection<T> = RemoteSection(SectionStatus.SUCCESS, value = value)
        fun <T> failed(code: String): RemoteSection<T> = RemoteSection(SectionStatus.FAILED, errorCode = code)
        fun <T> skipped(): RemoteSection<T> = RemoteSection(SectionStatus.SKIPPED)
    }
}

data class ProviderCategoryRecord(
    val categoryId: String,
    val providerKey: String,
    val name: String,
    val providerOrder: Int,
)

data class ProviderLiveChannelRecord(
    val channelId: String,
    val providerKey: String,
    val providerStreamId: String?,
    val categoryKey: String?,
    val name: String,
    val tvgId: String?,
    val tvgName: String?,
    val logoUrl: String?,
    val streamLocator: String,
    val providerOrder: Int,
)

data class ProviderMovieRecord(
    val movieId: String,
    val providerStreamId: String,
    val categoryKey: String?,
    val name: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val extension: String?,
    val rating: String?,
    val providerOrder: Int,
)

data class ProviderSeriesRecord(
    val seriesId: String,
    val providerSeriesId: String,
    val categoryKey: String?,
    val name: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val description: String?,
    val rating: String?,
    val providerOrder: Int,
)

data class ProviderRefreshPayload(
    val liveCategories: RemoteSection<List<ProviderCategoryRecord>>,
    val liveChannels: RemoteSection<List<ProviderLiveChannelRecord>>,
    val vodCategories: RemoteSection<List<ProviderCategoryRecord>>,
    val movies: RemoteSection<List<ProviderMovieRecord>>,
    val seriesCategories: RemoteSection<List<ProviderCategoryRecord>>,
    val series: RemoteSection<List<ProviderSeriesRecord>>,
) {
    fun sectionStatus(section: CatalogSection): SectionStatus = when (section) {
        CatalogSection.LIVE_CATEGORIES -> liveCategories.status
        CatalogSection.LIVE_CHANNELS -> liveChannels.status
        CatalogSection.VOD_CATEGORIES -> vodCategories.status
        CatalogSection.MOVIES -> movies.status
        CatalogSection.SERIES_CATEGORIES -> seriesCategories.status
        CatalogSection.SERIES -> series.status
    }

    fun errorCodes(): List<String> = listOfNotNull(
        liveCategories.errorCode,
        liveChannels.errorCode,
        vodCategories.errorCode,
        movies.errorCode,
        seriesCategories.errorCode,
        series.errorCode,
    ).distinct()
}

data class RefreshPlan(
    val generation: Long,
    val successfulSections: Set<CatalogSection>,
    val state: String,
    val errorCode: String?,
)

object RefreshPolicy {
    fun plan(previousGeneration: Long, payload: ProviderRefreshPayload): RefreshPlan {
        val successful = CatalogSection.entries
            .filterTo(linkedSetOf()) { payload.sectionStatus(it) == SectionStatus.SUCCESS }
        val failed = CatalogSection.entries.any { payload.sectionStatus(it) == SectionStatus.FAILED }
        val nextGeneration = if (successful.isEmpty()) previousGeneration else previousGeneration + 1
        val state = when {
            successful.isEmpty() -> "FAILED"
            failed -> "PARTIAL"
            else -> "SUCCESS"
        }
        return RefreshPlan(
            generation = nextGeneration,
            successfulSections = successful,
            state = state,
            errorCode = payload.errorCodes().takeIf { it.isNotEmpty() }?.joinToString(","),
        )
    }
}
