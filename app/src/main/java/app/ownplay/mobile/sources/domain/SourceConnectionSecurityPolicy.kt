package app.ownplay.mobile.sources.domain

import java.net.URI

object SourceConnectionSecurityPolicy {
    const val CLEAR_TEXT_WARNING =
        "HTTP is supported for providers that do not offer HTTPS. Credentials and traffic are not protected by TLS, so use this connection only with a provider and network you trust."

    fun isCleartext(locator: String): Boolean = runCatching {
        URI(locator.trim()).scheme.equals("http", ignoreCase = true)
    }.getOrDefault(false)

    fun warning(locator: String): String? =
        CLEAR_TEXT_WARNING.takeIf { isCleartext(locator) }
}
