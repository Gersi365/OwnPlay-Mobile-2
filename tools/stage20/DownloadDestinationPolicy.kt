package app.ownplay.mobile.downloads.data

import java.util.Locale

internal data class DownloadDestination(
    val relativeDirectory: String,
    val displayName: String,
    val mimeType: String,
    val collisionSuffix: String,
)

internal object DownloadDestinationPolicy {
    fun movie(
        title: String,
        identityKey: String,
        extension: String?,
    ): DownloadDestination {
        val safeTitle = safeComponent(title)
        val safeExtension = safeExtension(extension)
        return DownloadDestination(
            relativeDirectory = "$ROOT_DIRECTORY/Movies/$safeTitle",
            displayName = "$safeTitle.$safeExtension",
            mimeType = mimeTypeFor(safeExtension),
            collisionSuffix = stableSuffix(identityKey),
        )
    }

    fun episode(
        seriesTitle: String,
        seasonNumber: Int,
        episodeNumber: Int,
        episodeTitle: String,
        identityKey: String,
        extension: String?,
    ): DownloadDestination {
        val safeSeriesTitle = safeComponent(seriesTitle)
        val safeEpisodeTitle = safeComponent(episodeTitle)
        val seasonLabel = "Season ${seasonNumber.coerceAtLeast(0).toString().padStart(2, '0')}"
        val episodeLabel = "S${seasonNumber.coerceAtLeast(0).toString().padStart(2, '0')}E${episodeNumber.coerceAtLeast(0).toString().padStart(2, '0')}"
        val safeExtension = safeExtension(extension)
        return DownloadDestination(
            relativeDirectory = "$ROOT_DIRECTORY/Series/$safeSeriesTitle/$seasonLabel",
            displayName = "$episodeLabel - $safeEpisodeTitle.$safeExtension",
            mimeType = mimeTypeFor(safeExtension),
            collisionSuffix = stableSuffix(identityKey),
        )
    }

    fun collisionDisplayName(
        destination: DownloadDestination,
        ordinal: Int,
    ): String {
        require(ordinal >= 1)
        val extension = destination.displayName.substringAfterLast('.', missingDelimiterValue = "")
        val stem = if (extension.isBlank()) {
            destination.displayName
        } else {
            destination.displayName.removeSuffix(".$extension")
        }
        val suffix = if (ordinal == 1) {
            destination.collisionSuffix
        } else {
            "${destination.collisionSuffix}-$ordinal"
        }
        return if (extension.isBlank()) {
            "$stem - $suffix"
        } else {
            "$stem - $suffix.$extension"
        }
    }

    fun extensionFromUri(uri: String): String? {
        val path = uri.substringBefore('?').substringBefore('#')
        val tail = path.substringAfterLast('/', missingDelimiterValue = path)
        val extension = tail.substringAfterLast('.', missingDelimiterValue = "")
        return extension.takeIf { candidate ->
            candidate.isNotBlank() &&
                candidate.length <= MAX_EXTENSION_LENGTH &&
                candidate.all { character -> character.isLetterOrDigit() }
        }
    }

    private fun stableSuffix(identityKey: String): String {
        val hash = identityKey.hashCode().toUInt().toString(16).padStart(8, '0')
        return hash.takeLast(COLLISION_SUFFIX_LENGTH)
    }

    private fun safeComponent(value: String): String {
        val sanitized = buildString(value.length) {
            value.forEach { character ->
                when {
                    character.code < 32 -> append(' ')
                    character in FORBIDDEN_PATH_CHARACTERS -> append(' ')
                    else -> append(character)
                }
            }
        }
            .replace(WHITESPACE_REGEX, " ")
            .trim()
            .trimEnd('.', ' ')
            .take(MAX_COMPONENT_LENGTH)
            .trim()
            .trimEnd('.', ' ')
        return sanitized.ifBlank { "Untitled" }
    }

    private fun safeExtension(value: String?): String {
        val normalized = value
            ?.trim()
            ?.removePrefix(".")
            ?.lowercase(Locale.US)
            ?.takeIf { candidate ->
                candidate.isNotBlank() &&
                    candidate.length <= MAX_EXTENSION_LENGTH &&
                    candidate.all { character -> character.isLetterOrDigit() }
            }
        return normalized ?: FALLBACK_EXTENSION
    }

    private fun mimeTypeFor(extension: String): String = when (extension.lowercase(Locale.US)) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "ts", "m2ts" -> "video/mp2t"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "webm" -> "video/webm"
        else -> "application/octet-stream"
    }

    private val WHITESPACE_REGEX = Regex("\\s+")
    private val FORBIDDEN_PATH_CHARACTERS = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

    private const val ROOT_DIRECTORY = "OwnPlay Downloads"
    private const val FALLBACK_EXTENSION = "media"
    private const val MAX_COMPONENT_LENGTH = 96
    private const val MAX_EXTENSION_LENGTH = 8
    private const val COLLISION_SUFFIX_LENGTH = 8
}
