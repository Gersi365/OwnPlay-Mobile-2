package app.ownplay.mobile.sources.data

import app.ownplay.mobile.sources.data.xtream.XtreamUrlBuilder
import java.net.URI
import java.util.Locale

object SourceLocatorPolicy {
    fun normalizeXtream(raw: String): String = XtreamUrlBuilder.normalizeBaseUrl(raw)

    fun validateM3uRemote(raw: String): String {
        val uri = URI(raw.trim())
        require(uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true))
        require(!uri.host.isNullOrBlank())
        return uri.toString()
    }

    fun redactRemoteLocator(raw: String): String {
        val uri = URI(validateM3uRemote(raw))
        return URI(
            uri.scheme.lowercase(Locale.US),
            null,
            uri.host,
            uri.port,
            null,
            null,
            null,
        ).toString()
    }
}
