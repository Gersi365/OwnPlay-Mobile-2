package app.ownplay.mobile.playback.domain

import java.util.Locale

data class AudioTrackSelectionKey(
    val groupIndex: Int,
    val trackIndex: Int,
)

object AudioTrackPolicy {
    fun selectionId(groupIndex: Int, trackIndex: Int): String {
        require(groupIndex >= 0) { "Audio group index must be non-negative." }
        require(trackIndex >= 0) { "Audio track index must be non-negative." }
        return "$groupIndex:$trackIndex"
    }

    fun parseSelectionId(selectionId: String): AudioTrackSelectionKey? {
        val parts = selectionId.split(':')
        if (parts.size != 2) return null
        val groupIndex = parts[0].toIntOrNull() ?: return null
        val trackIndex = parts[1].toIntOrNull() ?: return null
        if (groupIndex < 0 || trackIndex < 0) return null
        return AudioTrackSelectionKey(groupIndex, trackIndex)
    }

    fun primaryLabel(track: PlaybackAudioTrack, ordinal: Int): String {
        val explicit = track.label?.trim()?.takeIf { it.isNotEmpty() }
        if (explicit != null) return explicit
        val language = track.language?.trim()?.takeIf { it.isNotEmpty() }
        if (language != null) return language.uppercase(Locale.US)
        return "Audio ${ordinal.coerceAtLeast(1)}"
    }

    fun detailLabel(track: PlaybackAudioTrack): String {
        val parts = mutableListOf<String>()
        track.language
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.uppercase(Locale.US)
            ?.let(parts::add)

        val format = AudioFormatLabelPolicy.describe(track.mimeType, track.codecs)
        if (format != "unknown format") parts += format

        track.channelCount?.takeIf { it > 0 }?.let { channels ->
            parts += when (channels) {
                1 -> "Mono"
                2 -> "Stereo"
                else -> "${channels}ch"
            }
        }
        track.sampleRate?.takeIf { it > 0 }?.let { sampleRate ->
            parts += "${sampleRate / 1000.0} kHz"
        }
        if (!track.supported) parts += "Unsupported"
        return parts.distinct().joinToString(" • ").ifBlank { "Audio track" }
    }
}
