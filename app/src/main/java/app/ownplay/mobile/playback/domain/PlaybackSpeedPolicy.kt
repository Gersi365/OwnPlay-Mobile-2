package app.ownplay.mobile.playback.domain

import kotlin.math.abs

object PlaybackSpeedPolicy {
    val supportedSpeeds: List<Float> = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

    fun normalize(requested: Float): Float {
        if (!requested.isFinite()) return 1f
        return supportedSpeeds.minBy { candidate -> abs(candidate - requested) }
    }

    fun label(speed: Float): String {
        val normalized = normalize(speed)
        return if (normalized % 1f == 0f) {
            "${normalized.toInt()}.0×"
        } else {
            "${normalized}×"
        }
    }
}
