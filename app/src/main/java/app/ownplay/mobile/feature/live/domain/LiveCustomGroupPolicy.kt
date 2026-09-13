package app.ownplay.mobile.feature.live.domain

internal object LiveCustomGroupPolicy {
    const val MAX_NAME_LENGTH = 60

    fun normalizeName(value: String?): String? = value
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.take(MAX_NAME_LENGTH)
}
