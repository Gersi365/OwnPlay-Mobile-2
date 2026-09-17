package app.ownplay.mobile.sources.data.xtream

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray

object XtreamPayloadParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun categories(body: String): List<XtreamCategory> =
        parseArray(body).mapIndexedNotNull { index, element ->
            val objectValue = element as? JsonObject ?: return@mapIndexedNotNull null
            val id = objectValue.text("category_id") ?: return@mapIndexedNotNull null
            val name = objectValue.text("category_name") ?: return@mapIndexedNotNull null
            XtreamCategory(
                providerCategoryId = id,
                name = name,
                providerOrder = index,
            )
        }

    fun liveStreams(body: String): List<XtreamLiveStream> =
        parseArray(body).mapIndexedNotNull { index, element ->
            val objectValue = element as? JsonObject ?: return@mapIndexedNotNull null
            val id = objectValue.text("stream_id") ?: return@mapIndexedNotNull null
            val name = objectValue.text("name") ?: return@mapIndexedNotNull null
            XtreamLiveStream(
                streamId = id,
                categoryId = objectValue.text("category_id"),
                name = name,
                tvgId = objectValue.text("epg_channel_id") ?: objectValue.text("tvg_id"),
                logoUrl = objectValue.text("stream_icon"),
                containerExtension = objectValue.text("container_extension"),
                providerOrder = index,
            )
        }

    fun movies(body: String): List<XtreamMovie> =
        parseArray(body).mapIndexedNotNull { index, element ->
            val objectValue = element as? JsonObject ?: return@mapIndexedNotNull null
            val id = objectValue.text("stream_id") ?: return@mapIndexedNotNull null
            val name = objectValue.text("name") ?: return@mapIndexedNotNull null
            XtreamMovie(
                streamId = id,
                categoryId = objectValue.text("category_id"),
                name = name,
                posterUrl = objectValue.text("stream_icon"),
                containerExtension = objectValue.text("container_extension"),
                rating = objectValue.text("rating"),
                providerOrder = index,
            )
        }

    fun series(body: String): List<XtreamSeries> =
        parseArray(body).mapIndexedNotNull { index, element ->
            val objectValue = element as? JsonObject ?: return@mapIndexedNotNull null
            val id = objectValue.text("series_id") ?: return@mapIndexedNotNull null
            val name = objectValue.text("name") ?: return@mapIndexedNotNull null
            XtreamSeries(
                seriesId = id,
                categoryId = objectValue.text("category_id"),
                name = name,
                posterUrl = objectValue.text("cover"),
                description = objectValue.text("plot"),
                rating = objectValue.text("rating"),
                providerOrder = index,
            )
        }

    private fun parseArray(body: String): JsonArray =
        json.parseToJsonElement(body).jsonArray

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)
            ?.contentOrNull
            ?.trim()
            ?.takeIf(String::isNotBlank)
}
