package app.ownplay.mobile.sources.domain

import java.util.Locale

object ProviderCategoryVisibility {
    private val exactUtilityLabels = setOf(
        "all",
        "all channels",
        "all live",
        "all live channels",
        "all movies",
        "all series",
        "all tv",
        "all vod",
    )

    fun isUtilityLabel(label: String): Boolean {
        val normalized = normalize(label)
        if (normalized in exactUtilityLabels) return true
        return normalized.contains("account information") ||
            normalized.contains("account info")
    }

    fun normalized(label: String): String = normalize(label)

    private fun normalize(label: String): String = label
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ")
}
