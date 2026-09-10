package app.ownplay.mobile.sources.domain

import java.net.URI

object SourceConnectionSecurityPolicy {
    const val CLEAR_TEXT_WARNING =
        "This source uses HTTP. Provider credentials and traffic are not protected by TLS. Use HTTPS when the provider supports it."

    fun isCleartext(locator: String): Boolean = runCatching {
        URI(locator.trim()).scheme.equals("http", ignoreCase = true)
    }.getOrDefault(false)

    fun warning(locator: String): String? =
        CLEAR_TEXT_WARNING.takeIf { isCleartext(locator) }
}
