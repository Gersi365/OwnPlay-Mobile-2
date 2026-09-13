package app.ownplay.mobile.playback.domain

import java.util.Locale

data class SubtitleTrackSelectionKey(
    val groupIndex: Int,
    val trackIndex: Int,
)

object SubtitleTrackPolicy {
    fun selectionId(groupIndex: Int, trackIndex: Int): String {
        require(groupIndex >= 0) { "Subtitle group index must be non-negative." }
        require(trackIndex >= 0) { "Subtitle track index must be non-negative." }
        return "$groupIndex:$trackIndex"
    }

    fun parseSelectionId(selectionId: String): SubtitleTrackSelectionKey? {
        val parts = selectionId.split(':')
        if (parts.size != 2) return null
        val groupIndex = parts[0].toIntOrNull() ?: return null
        val trackIndex = parts[1].toIntOrNull() ?: return null
        if (groupIndex < 0 || trackIndex < 0) return null
        return SubtitleTrackSelectionKey(groupIndex, trackIndex)
    }

    fun primaryLabel(track: PlaybackSubtitleTrack, ordinal: Int): String {
        val explicit = track.label?.trim()?.takeIf { it.isNotEmpty() }
        if (explicit != null) return explicit
        val language = track.language?.trim()?.takeIf { it.isNotEmpty() }
        if (language != null) return language.uppercase(Locale.US)
        return "Subtitle ${ordinal.coerceAtLeast(1)}"
    }

    fun detailLabel(track: PlaybackSubtitleTrack): String {
        val parts = mutableListOf<String>()
        track.language
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.uppercase(Locale.US)
            ?.let(parts::add)
        track.mimeType
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { mime -> parts += formatLabel(mime) }
        if (!track.supported) parts += "Unsupported"
        return parts.distinct().joinToString(" • ").ifBlank { "Text subtitle track" }
    }

    private fun formatLabel(mimeType: String): String = when {
        mimeType.contains("vtt", ignoreCase = true) -> "WebVTT"
        mimeType.contains("ttml", ignoreCase = true) -> "TTML"
        mimeType.contains("subrip", ignoreCase = true) -> "SubRip"
        mimeType.contains("ssa", ignoreCase = true) -> "SSA/ASS"
        mimeType.contains("cea-608", ignoreCase = true) -> "CEA-608"
        mimeType.contains("cea-708", ignoreCase = true) -> "CEA-708"
        else -> mimeType.substringAfter('/', mimeType)
    }
}
