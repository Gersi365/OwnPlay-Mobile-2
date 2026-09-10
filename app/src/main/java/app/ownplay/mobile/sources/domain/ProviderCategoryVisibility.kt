package app.ownplay.mobile.sources.domain

import java.text.Normalizer
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

    private val compatibilityFold = mapOf(
        'ᴀ' to 'a', 'ʙ' to 'b', 'ᴄ' to 'c', 'ᴅ' to 'd', 'ᴇ' to 'e',
        'ꜰ' to 'f', 'ɢ' to 'g', 'ʜ' to 'h', 'ɪ' to 'i', 'ᴊ' to 'j',
        'ᴋ' to 'k', 'ʟ' to 'l', 'ᴍ' to 'm', 'ɴ' to 'n', 'ᴏ' to 'o',
        'ᴘ' to 'p', 'ʀ' to 'r', 'ꜱ' to 's', 'ᴛ' to 't', 'ᴜ' to 'u',
        'ᴠ' to 'v', 'ᴡ' to 'w', 'ʏ' to 'y', 'ᴢ' to 'z',
    )

    fun isUtilityLabel(label: String): Boolean {
        val normalized = normalize(label)
        if (normalized in exactUtilityLabels) return true

        val compact = normalized.replace(" ", "")
        return compact.contains("accountinformation") || compact.contains("accountinfo")
    }

    fun normalized(label: String): String = normalize(label)

    private fun normalize(label: String): String {
        val compatibilityNormalized = Normalizer.normalize(label, Normalizer.Form.NFKC)
            .lowercase(Locale.US)
        val folded = buildString(compatibilityNormalized.length) {
            compatibilityNormalized.forEach { character ->
                append(compatibilityFold[character] ?: character)
            }
        }
        val decomposed = Normalizer.normalize(folded, Normalizer.Form.NFKD)
            .filterNot { character ->
                when (Character.getType(character)) {
                    Character.NON_SPACING_MARK.toInt(),
                    Character.COMBINING_SPACING_MARK.toInt(),
                    Character.ENCLOSING_MARK.toInt(),
                    Character.FORMAT.toInt() -> true
                    else -> false
                }
            }

        return decomposed
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }
}
