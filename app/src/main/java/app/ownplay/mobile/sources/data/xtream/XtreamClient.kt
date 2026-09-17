package app.ownplay.mobile.sources.data.xtream

import app.ownplay.mobile.sources.data.ProviderPayloadKind
import app.ownplay.mobile.sources.data.ProviderPayloadValidation
import app.ownplay.mobile.sources.data.ProviderPayloadValidationResult
import app.ownplay.mobile.sources.data.ProviderTransport

interface XtreamClient {
    suspend fun liveCategories(connection: XtreamConnection): List<XtreamCategory>
    suspend fun liveStreams(connection: XtreamConnection): List<XtreamLiveStream>
    suspend fun movieCategories(connection: XtreamConnection): List<XtreamCategory>
    suspend fun movies(connection: XtreamConnection): List<XtreamMovie>
    suspend fun seriesCategories(connection: XtreamConnection): List<XtreamCategory>
    suspend fun series(connection: XtreamConnection): List<XtreamSeries>
    suspend fun seriesInfo(connection: XtreamConnection, seriesId: String): XtreamSeriesDetail
}

class OkHttpXtreamClient(
    private val transport: ProviderTransport,
) : XtreamClient {
    override suspend fun liveCategories(
        connection: XtreamConnection,
    ): List<XtreamCategory> =
        parse(connection, "get_live_categories", XtreamPayloadParser::categories)

    override suspend fun liveStreams(
        connection: XtreamConnection,
    ): List<XtreamLiveStream> =
        parse(connection, "get_live_streams", XtreamPayloadParser::liveStreams)

    override suspend fun movieCategories(
        connection: XtreamConnection,
    ): List<XtreamCategory> =
        parse(connection, "get_vod_categories", XtreamPayloadParser::categories)

    override suspend fun movies(
        connection: XtreamConnection,
    ): List<XtreamMovie> =
        parse(connection, "get_vod_streams", XtreamPayloadParser::movies)

    override suspend fun seriesCategories(
        connection: XtreamConnection,
    ): List<XtreamCategory> =
        parse(connection, "get_series_categories", XtreamPayloadParser::categories)

    override suspend fun series(
        connection: XtreamConnection,
    ): List<XtreamSeries> =
        parse(connection, "get_series", XtreamPayloadParser::series)

    override suspend fun seriesInfo(
        connection: XtreamConnection,
        seriesId: String,
    ): XtreamSeriesDetail {
        require(seriesId.isNotBlank()) { "Series id must not be blank" }
        return parse(
            connection = connection,
            action = "get_series_info",
            parser = XtreamPayloadParser::seriesInfo,
            extraParameters = mapOf("series_id" to seriesId),
        )
    }

    private suspend fun <T> parse(
        connection: XtreamConnection,
        action: String,
        parser: (String) -> T,
        extraParameters: Map<String, String> = emptyMap(),
    ): T {
        val response = transport.get(
            XtreamUrlBuilder.playerApi(
                baseUrl = connection.baseUrl,
                username = connection.username,
                password = connection.password,
                action = action,
                extraParameters = extraParameters,
            ),
        )

        if (response.statusCode == 401 || response.statusCode == 403) {
            throw XtreamClientException(XtreamClientFailureCategory.AUTHENTICATION)
        }
        if (response.statusCode !in 200..299) {
            throw XtreamClientException(XtreamClientFailureCategory.PROVIDER)
        }

        when (
            ProviderPayloadValidation.validate(
                body = response.body,
                kind = ProviderPayloadKind.JSON,
            )
        ) {
            ProviderPayloadValidationResult.Valid -> Unit
            is ProviderPayloadValidationResult.Invalid -> {
                throw XtreamClientException(XtreamClientFailureCategory.INVALID_PAYLOAD)
            }
        }

        return try {
            parser(response.body)
        } catch (_: Exception) {
            throw XtreamClientException(XtreamClientFailureCategory.INVALID_PAYLOAD)
        }
    }
}

class XtreamClientException(
    val category: XtreamClientFailureCategory,
) : Exception(category.name)

enum class XtreamClientFailureCategory {
    AUTHENTICATION,
    PROVIDER,
    INVALID_PAYLOAD,
}
