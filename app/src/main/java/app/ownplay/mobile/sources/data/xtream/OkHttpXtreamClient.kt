package app.ownplay.mobile.sources.data.xtream

import app.ownplay.mobile.sources.data.ProviderHttpTransport
import app.ownplay.mobile.sources.data.TransportResult
import app.ownplay.mobile.sources.domain.SourceCredential
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class OkHttpXtreamClient(
    private val transport: ProviderHttpTransport,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    },
) : XtreamClient {
    override suspend fun authenticate(
        baseUrl: String,
        credential: SourceCredential.Xtream,
    ): XtreamResult<XtreamAccountInfo> {
        return when (val root = getJson(XtreamUrlBuilder.apiUrl(baseUrl, credential))) {
            is XtreamResult.Failure -> root
            is XtreamResult.Success -> {
                val user = root.value.asObject()?.get("user_info")?.asObject()
                    ?: return XtreamResult.Failure("XTREAM_AUTH_FORMAT")
                val authPrimitive = user["auth"]?.asPrimitive()
                val authenticated = authPrimitive?.intOrNull == 1 || authPrimitive?.booleanOrNull == true
                XtreamResult.Success(
                    XtreamAccountInfo(
                        authenticated = authenticated,
                        status = user["status"]?.text(),
                        expirationEpochSeconds = user["exp_date"]?.text()?.toLongOrNull(),
                    ),
                )
            }
        }
    }

    override suspend fun liveCategories(
        baseUrl: String,
        credential: SourceCredential.Xtream,
    ): XtreamResult<List<XtreamCategory>> = categories(baseUrl, credential, "get_live_categories")

    override suspend fun vodCategories(
        baseUrl: String,
        credential: SourceCredential.Xtream,
    ): XtreamResult<List<XtreamCategory>> = categories(baseUrl, credential, "get_vod_categories")

    override suspend fun seriesCategories(
        baseUrl: String,
        credential: SourceCredential.Xtream,
    ): XtreamResult<List<XtreamCategory>> = categories(baseUrl, credential, "get_series_categories")

    override suspend fun liveStreams(
        baseUrl: String,
        credential: SourceCredential.Xtream,
        categoryId: String?,
    ): XtreamResult<List<XtreamLiveStream>> {
        val extra = categoryId
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { mapOf("category_id" to it) }
            .orEmpty()
        return mapArray(
            XtreamUrlBuilder.apiUrl(baseUrl, credential, "get_live_streams", extra = extra),
        ) { obj, index ->
            val streamId = obj["stream_id"]?.text()?.takeIf(String::isNotBlank) ?: return@mapArray null
            XtreamLiveStream(
                streamId = streamId,
                categoryId = obj["category_id"]?.text()?.trim()?.takeIf(String::isNotEmpty),
                name = obj["name"]?.text().orEmpty().ifBlank { "Unnamed channel" },
                epgChannelId = obj["epg_channel_id"]?.text(),
                streamIcon = obj["stream_icon"]?.text(),
                providerOrder = obj["num"]?.asPrimitive()?.intOrNull ?: index,
            )
        }
    }

    override suspend fun vodStreams(
        baseUrl: String,
        credential: SourceCredential.Xtream,
    ): XtreamResult<List<XtreamMovie>> {
        return mapArray(XtreamUrlBuilder.apiUrl(baseUrl, credential, "get_vod_streams")) { obj, index ->
            val streamId = obj["stream_id"]?.text()?.takeIf(String::isNotBlank) ?: return@mapArray null
            XtreamMovie(
                streamId = streamId,
                categoryId = obj["category_id"]?.text()?.trim()?.takeIf(String::isNotEmpty),
                name = obj["name"]?.text().orEmpty().ifBlank { "Untitled movie" },
                posterUrl = obj["stream_icon"]?.text(),
                extension = obj["container_extension"]?.text(),
                rating = obj["rating"]?.text(),
                providerOrder = obj["num"]?.asPrimitive()?.intOrNull ?: index,
            )
        }
    }

    override suspend fun series(
        baseUrl: String,
        credential: SourceCredential.Xtream,
    ): XtreamResult<List<XtreamSeries>> {
        return mapArray(XtreamUrlBuilder.apiUrl(baseUrl, credential, "get_series")) { obj, index ->
            val seriesId = obj["series_id"]?.text()?.takeIf(String::isNotBlank) ?: return@mapArray null
            val backdrops = obj["backdrop_path"]?.asArray()
            XtreamSeries(
                seriesId = seriesId,
                categoryId = obj["category_id"]?.text()?.trim()?.takeIf(String::isNotEmpty),
                name = obj["name"]?.text().orEmpty().ifBlank { "Untitled series" },
                posterUrl = obj["cover"]?.text(),
                backdropUrl = backdrops?.firstOrNull()?.text(),
                description = obj["plot"]?.text(),
                rating = obj["rating"]?.text(),
                providerOrder = obj["num"]?.asPrimitive()?.intOrNull ?: index,
            )
        }
    }

    override suspend fun seriesInfo(
        baseUrl: String,
        credential: SourceCredential.Xtream,
        seriesId: String,
    ): XtreamResult<XtreamSeriesInfo> {
        val url = XtreamUrlBuilder.apiUrl(
            baseUrl = baseUrl,
            credential = credential,
            action = "get_series_info",
            extra = mapOf("series_id" to seriesId),
        )
        return when (val root = getJson(url)) {
            is XtreamResult.Failure -> root
            is XtreamResult.Success -> {
                val episodesObject = root.value.asObject()?.get("episodes")?.asObject()
                    ?: return XtreamResult.Success(XtreamSeriesInfo(seriesId, emptyList()))
                val episodes = buildList {
                    episodesObject.entries
                        .sortedBy { it.key.toIntOrNull() ?: Int.MAX_VALUE }
                        .forEach { (seasonKey, value) ->
                            val season = seasonKey.toIntOrNull() ?: 0
                            value.asArray().orEmpty().forEachIndexed { index, element ->
                                val obj = element.asObject() ?: return@forEachIndexed
                                val episodeId = obj["id"]?.text()?.takeIf(String::isNotBlank) ?: return@forEachIndexed
                                add(
                                    XtreamEpisode(
                                        episodeId = episodeId,
                                        seasonNumber = obj["season"]?.asPrimitive()?.intOrNull ?: season,
                                        episodeNumber = obj["episode_num"]?.asPrimitive()?.intOrNull ?: index + 1,
                                        title = obj["title"]?.text().orEmpty().ifBlank { "Episode ${index + 1}" },
                                        durationSeconds = parseDurationSeconds(obj),
                                        extension = obj["container_extension"]?.text(),
                                    ),
                                )
                            }
                        }
                }
                XtreamResult.Success(XtreamSeriesInfo(seriesId, episodes))
            }
        }
    }

    override suspend fun shortEpg(
        baseUrl: String,
        credential: SourceCredential.Xtream,
        streamId: String,
        limit: Int,
    ): XtreamResult<List<XtreamEpgEntry>> {
        val url = XtreamUrlBuilder.apiUrl(
            baseUrl = baseUrl,
            credential = credential,
            action = "get_short_epg",
            extra = mapOf("stream_id" to streamId, "limit" to limit.coerceIn(1, 20).toString()),
        )
        return when (val root = getJson(url)) {
            is XtreamResult.Failure -> root
            is XtreamResult.Success -> {
                val listings = root.value.asObject()?.get("epg_listings")?.asArray().orEmpty()
                XtreamResult.Success(
                    listings.mapNotNull { element ->
                        val obj = element.asObject() ?: return@mapNotNull null
                        XtreamEpgEntry(
                            title = XtreamEpgTextPolicy.decode(obj["title"]?.text().orEmpty()),
                            startEpochSeconds = obj["start_timestamp"]?.text()?.toLongOrNull(),
                            endEpochSeconds = obj["stop_timestamp"]?.text()?.toLongOrNull(),
                        )
                    },
                )
            }
        }
    }

    private suspend fun categories(
        baseUrl: String,
        credential: SourceCredential.Xtream,
        action: String,
    ): XtreamResult<List<XtreamCategory>> = mapArray(
        XtreamUrlBuilder.apiUrl(baseUrl, credential, action),
    ) { obj, index ->
        val key = obj["category_id"]?.text()?.trim()?.takeIf(String::isNotEmpty) ?: return@mapArray null
        XtreamCategory(
            providerKey = key,
            name = obj["category_name"]?.text().orEmpty().ifBlank { "Other" },
            providerOrder = index,
        )
    }

    private suspend fun <T> mapArray(
        url: String,
        mapper: (JsonObject, Int) -> T?,
    ): XtreamResult<List<T>> {
        return when (val root = getJson(url)) {
            is XtreamResult.Failure -> root
            is XtreamResult.Success -> {
                val array = root.value.asArray() ?: return XtreamResult.Failure("XTREAM_ARRAY_FORMAT")
                XtreamResult.Success(
                    array.mapIndexedNotNull { index, element ->
                        element.asObject()?.let { mapper(it, index) }
                    },
                )
            }
        }
    }

    private suspend fun getJson(url: String): XtreamResult<JsonElement> {
        return when (val response = transport.getText(url)) {
            is TransportResult.Failure -> XtreamResult.Failure(response.code)
            is TransportResult.Success -> try {
                XtreamResult.Success(json.parseToJsonElement(response.body))
            } catch (_: Exception) {
                XtreamResult.Failure("XTREAM_JSON")
            }
        }
    }

    private fun parseDurationSeconds(obj: JsonObject): Long? {
        obj["duration_secs"]?.asPrimitive()?.longOrNull?.let { return it }
        val duration = obj["duration"]?.text() ?: return null
        val parts = duration.split(':').mapNotNull(String::toLongOrNull)
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            else -> duration.toLongOrNull()
        }
    }

    private fun JsonElement.asObject(): JsonObject? = this as? JsonObject
    private fun JsonElement.asArray(): JsonArray? = this as? JsonArray
    private fun JsonElement.asPrimitive(): JsonPrimitive? = this as? JsonPrimitive
    private fun JsonElement.text(): String? = (this as? JsonPrimitive)?.contentOrNull
}
