package app.ownplay.mobile.feature.live.domain

import java.net.URI

object LivePersonalizationPolicy {
    const val MAX_LOCAL_NAME_LENGTH: Int = 120
    const val MAX_LOCAL_LOGO_URL_LENGTH: Int = 2_048

    fun normalizeLocalName(value: String?): String? = value
        ?.trim()
        ?.take(MAX_LOCAL_NAME_LENGTH)
        ?.takeIf { it.isNotEmpty() }

    fun normalizeLocalLogo(value: String?): String? {
        val normalized = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (normalized.length > MAX_LOCAL_LOGO_URL_LENGTH) return null
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        if (uri.host.isNullOrBlank()) return null
        if (uri.userInfo != null) return null
        return normalized
    }

    fun isValidLocalLogoInput(value: String?): Boolean =
        value.isNullOrBlank() || normalizeLocalLogo(value) != null
}
