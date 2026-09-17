package app.ownplay.mobile.sources.data.xtream

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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

    fun seriesInfo(body: String): XtreamSeriesDetail {
        val root = json.parseToJsonElement(body) as? JsonObject
            ?: return XtreamSeriesDetail(emptyList())
        val episodePayload = root["episodes"] ?: return XtreamSeriesDetail(emptyList())
        val episodes = when (episodePayload) {
            is JsonObject -> episodePayload.entries.flatMap { (seasonKey, value) ->
                parseSeasonEpisodes(seasonKey, value)
            }
            is JsonArray -> episodePayload.mapNotNull { element ->
                parseEpisode(element as? JsonObject ?: return@mapNotNull null, fallbackSeason = null)
            }
            else -> emptyList()
        }
        return XtreamSeriesDetail(
            episodes = episodes.distinctBy(XtreamSeriesEpisode::providerEpisodeId),
        )
    }

    private fun parseSeasonEpisodes(
        seasonKey: String,
        payload: JsonElement,
    ): List<XtreamSeriesEpisode> = when (payload) {
        is JsonArray -> payload.mapNotNull { element ->
            parseEpisode(
                objectValue = element as? JsonObject ?: return@mapNotNull null,
                fallbackSeason = seasonKey.toIntOrNull(),
            )
        }
        is JsonObject -> listOfNotNull(
            parseEpisode(
                objectValue = payload,
                fallbackSeason = seasonKey.toIntOrNull(),
            ),
        )
        else -> emptyList()
    }

    private fun parseEpisode(
        objectValue: JsonObject,
        fallbackSeason: Int?,
    ): XtreamSeriesEpisode? {
        val providerEpisodeId = objectValue.text("id") ?: return null
        val seasonNumber = objectValue.int("season") ?: fallbackSeason ?: return null
        val episodeNumber = objectValue.int("episode_num") ?: return null
        if (seasonNumber < 0 || episodeNumber < 0) return null
        val info = objectValue["info"] as? JsonObject
        val title = objectValue.text("title") ?: info?.text("name") ?: return null
        val durationSeconds = info?.long("duration_secs")
        return XtreamSeriesEpisode(
            providerEpisodeId = providerEpisodeId,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            title = title,
            containerExtension = objectValue.text("container_extension"),
            durationMs = durationSeconds
                ?.takeIf { it > 0L && it <= Long.MAX_VALUE / 1_000L }
                ?.times(1_000L),
        )
    }

    private fun parseArray(body: String): JsonArray =
        json.parseToJsonElement(body).jsonArray

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)
            ?.contentOrNull
            ?.trim()
            ?.takeIf(String::isNotBlank)

    private fun JsonObject.int(key: String): Int? = text(key)?.toIntOrNull()

    private fun JsonObject.long(key: String): Long? = text(key)?.toLongOrNull()
}
