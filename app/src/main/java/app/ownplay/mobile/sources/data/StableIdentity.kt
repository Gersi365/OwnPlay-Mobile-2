package app.ownplay.mobile.sources.data

import java.security.MessageDigest
import java.util.Locale

object StableIdentity {
    fun categoryId(
        sourceId: String,
        kind: String,
        providerKey: String,
    ): String = hash("category", sourceId, kind.lowercase(Locale.US), providerKey)

    fun xtreamContentId(
        sourceId: String,
        kind: String,
        providerKey: String,
    ): String = hash("xtream", sourceId, kind.lowercase(Locale.US), providerKey)

    fun m3uChannelId(
        sourceId: String,
        tvgId: String?,
        tvgIdIsUnique: Boolean,
        normalizedStreamLocator: String,
        fallbackName: String,
        fallbackGroup: String?,
    ): String {
        val stableTvgId = tvgId?.trim()?.takeIf { it.isNotEmpty() && tvgIdIsUnique }
        if (stableTvgId != null) {
            return hash("m3u", sourceId, "tvg-id", stableTvgId)
        }

        if (normalizedStreamLocator.isNotBlank()) {
            return hash("m3u", sourceId, "stream", normalizedStreamLocator)
        }

        return hash(
            "m3u",
            sourceId,
            "compound",
            fallbackName.trim().lowercase(Locale.US),
            fallbackGroup.orEmpty().trim().lowercase(Locale.US),
        )
    }

    private fun hash(vararg parts: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val canonical = parts.joinToString(separator = "\u001f")
        val bytes = digest.digest(canonical.toByteArray(Charsets.UTF_8))
        return buildString(bytes.size * 2) {
            bytes.forEach { byte -> append("%02x".format(byte.toInt() and 0xff)) }
        }
    }
}
