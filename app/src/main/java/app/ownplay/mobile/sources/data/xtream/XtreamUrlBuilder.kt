package app.ownplay.mobile.sources.data.xtream

import app.ownplay.mobile.sources.domain.ConnectionValidation
import app.ownplay.mobile.sources.domain.SourceConnectionSecurityPolicy
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object XtreamUrlBuilder {
    fun playerApi(
        baseUrl: String,
        username: String,
        password: String,
        action: String? = null,
        extraParameters: Map<String, String> = emptyMap(),
    ): String {
        val normalized = when (
            val result = SourceConnectionSecurityPolicy.normalizeXtreamBaseUrl(baseUrl)
        ) {
            is ConnectionValidation.Valid -> result.normalizedUrl
            is ConnectionValidation.Invalid -> error("Invalid Xtream base URL")
        }

        val parameters = buildList {
            add("username" to username)
            add("password" to password)
            if (!action.isNullOrBlank()) {
                add("action" to action)
            }
            extraParameters
                .toSortedMap()
                .forEach { (key, value) -> add(key to value) }
        }

        return buildString {
            append(normalized)
            append("/player_api.php?")
            append(
                parameters.joinToString("&") { (key, value) ->
                    "${encode(key)}=${encode(value)}"
                },
            )
        }
    }

    fun liveStream(
        baseUrl: String,
        username: String,
        password: String,
        streamId: String,
        extension: String,
    ): String = streamUrl(
        baseUrl = baseUrl,
        pathType = "live",
        username = username,
        password = password,
        streamId = streamId,
        extension = extension,
    )

    fun movieStream(
        baseUrl: String,
        username: String,
        password: String,
        streamId: String,
        extension: String,
    ): String = streamUrl(
        baseUrl = baseUrl,
        pathType = "movie",
        username = username,
        password = password,
        streamId = streamId,
        extension = extension,
    )

    fun seriesStream(
        baseUrl: String,
        username: String,
        password: String,
        episodeId: String,
        extension: String,
    ): String = streamUrl(
        baseUrl = baseUrl,
        pathType = "series",
        username = username,
        password = password,
        streamId = episodeId,
        extension = extension,
    )

    private fun streamUrl(
        baseUrl: String,
        pathType: String,
        username: String,
        password: String,
        streamId: String,
        extension: String,
    ): String {
        val normalized = when (
            val result = SourceConnectionSecurityPolicy.normalizeXtreamBaseUrl(baseUrl)
        ) {
            is ConnectionValidation.Valid -> result.normalizedUrl
            is ConnectionValidation.Invalid -> error("Invalid Xtream base URL")
        }

        require(streamId.isNotBlank()) { "Stream id must not be blank" }
        val safeExtension = extension
            .trim()
            .removePrefix(".")
            .lowercase()
            .takeIf { it.matches(Regex("[a-z0-9]{1,12}")) }
            ?: error("Invalid stream extension")

        return "$normalized/$pathType/${encodePath(username)}/${encodePath(password)}/${encodePath(streamId)}.$safeExtension"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
            .replace("+", "%20")

    private fun encodePath(value: String): String = encode(value)
}
