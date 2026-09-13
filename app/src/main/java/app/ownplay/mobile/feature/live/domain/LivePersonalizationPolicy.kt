package app.ownplay.mobile.feature.live.domain

object LivePersonalizationPolicy {
    const val MAX_LOCAL_NAME_LENGTH: Int = 120

    fun normalizeLocalName(value: String?): String? = value
        ?.trim()
        ?.take(MAX_LOCAL_NAME_LENGTH)
        ?.takeIf { it.isNotEmpty() }
}
