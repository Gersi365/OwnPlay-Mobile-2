package app.ownplay.mobile.sources.data.xtream

import app.ownplay.mobile.sources.domain.SourceCredential
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

object XtreamUrlBuilder {
    fun normalizeBaseUrl(raw: String): String {
        val uri = URI(raw.trim())
        require(uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true))
        require(!uri.host.isNullOrBlank())
        require(uri.userInfo == null) { "User info must be supplied as credentials, not in the base URL." }
        require(uri.rawQuery == null) { "Query parameters are not allowed in the Xtream base URL." }

        val path = uri.path.orEmpty()
            .removeSuffix("/")
            .removeSuffix("/player_api.php")
            .removeSuffix("player_api.php")
            .removeSuffix("/")
        return URI(
            uri.scheme.lowercase(Locale.US),
            null,
            uri.host,
            uri.port,
            path.ifBlank { null },
            null,
            null,
        ).toString().removeSuffix("/")
    }

    fun apiUrl(
        baseUrl: String,
        credential: SourceCredential.Xtream,
        action: String? = null,
        extra: Map<String, String> = emptyMap(),
    ): String {
        val normalized = normalizeBaseUrl(baseUrl)
        val params = linkedMapOf(
            "username" to credential.username,
            "password" to credential.password,
        )
        if (!action.isNullOrBlank()) params["action"] = action
        params.putAll(extra)
        val query = params.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        return "$normalized/player_api.php?$query"
    }

    fun streamUrl(
        baseUrl: String,
        credential: SourceCredential.Xtream,
        kind: String,
        providerId: String,
        extension: String? = null,
    ): String {
        val normalized = normalizeBaseUrl(baseUrl)
        val safeKind = when (kind.lowercase(Locale.US)) {
            "live" -> "live"
            "movie" -> "movie"
            "series" -> "series"
            else -> error("Unsupported Xtream stream kind")
        }
        val suffix = extension?.trim()?.removePrefix(".")?.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
        return "$normalized/$safeKind/${encodePath(credential.username)}/${encodePath(credential.password)}/${encodePath(providerId)}$suffix"
    }

    fun redact(url: String): String = url
        .replace(Regex("(?i)(username|password|token)=([^&]*)")) { match ->
            "${match.groupValues[1]}=<redacted>"
        }
        .replace(Regex("(?i)/(live|movie|series)/[^/]+/[^/]+/")) { match ->
            "/${match.groupValues[1]}/<redacted>/<redacted>/"
        }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun encodePath(value: String): String = encode(value).replace("+", "%20")
}
