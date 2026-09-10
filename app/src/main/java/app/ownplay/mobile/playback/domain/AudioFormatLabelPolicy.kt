package app.ownplay.mobile.playback.domain

import java.util.Locale

object AudioFormatLabelPolicy {
    fun describe(mimeType: String?, codecs: String?): String {
        val normalizedMime = mimeType?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotEmpty() }
        val codec = codecs?.trim()?.takeIf { it.isNotEmpty() }
        val label = when (normalizedMime) {
            "audio/mpeg-l1" -> "MPEG Layer I"
            "audio/mpeg-l2" -> "MPEG Layer II"
            "audio/mpeg" -> "MPEG audio"
            "audio/mp4a-latm", "audio/aac" -> "AAC"
            "audio/ac3" -> "AC-3"
            "audio/eac3", "audio/eac3-joc" -> "E-AC-3"
            "audio/ac4" -> "AC-4"
            "audio/vnd.dts", "audio/vnd.dts.hd" -> "DTS"
            "audio/true-hd" -> "TrueHD"
            "audio/opus" -> "Opus"
            "audio/vorbis" -> "Vorbis"
            "audio/flac" -> "FLAC"
            "audio/alac" -> "ALAC"
            "audio/g711-alaw" -> "PCM A-law"
            "audio/g711-mlaw" -> "PCM μ-law"
            else -> null
        }
        return when {
            label != null && codec != null -> "$label ($codec)"
            label != null -> label
            normalizedMime != null && codec != null -> "$normalizedMime ($codec)"
            normalizedMime != null -> normalizedMime
            codec != null -> codec
            else -> "unknown format"
        }
    }
}
