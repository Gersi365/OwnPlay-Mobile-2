package app.ownplay.mobile.playback.domain

enum class PhysicalOrientationBand {
    PORTRAIT,
    LANDSCAPE,
    TRANSITION,
}

object FullscreenOrientationPolicy {
    const val STABILITY_MILLIS: Long = 500L

    fun classify(orientationDegrees: Int): PhysicalOrientationBand = when {
        orientationDegrees in 60..120 || orientationDegrees in 240..300 ->
            PhysicalOrientationBand.LANDSCAPE
        orientationDegrees in 0..30 ||
            orientationDegrees in 150..210 ||
            orientationDegrees in 330..359 -> PhysicalOrientationBand.PORTRAIT
        else -> PhysicalOrientationBand.TRANSITION
    }
}

class StableOrientationLatch(
    private val targetBand: PhysicalOrientationBand,
    private val stabilityMillis: Long = FullscreenOrientationPolicy.STABILITY_MILLIS,
) {
    private var candidate = PhysicalOrientationBand.TRANSITION
    private var candidateSinceMillis: Long? = null
    private var emitted = false

    init {
        require(targetBand != PhysicalOrientationBand.TRANSITION) {
            "A stable orientation latch requires a portrait or landscape target."
        }
        require(stabilityMillis >= 0L) { "Stability duration must not be negative." }
    }

    fun reset() {
        candidate = PhysicalOrientationBand.TRANSITION
        candidateSinceMillis = null
        emitted = false
    }

    fun onOrientation(
        orientationDegrees: Int,
        elapsedRealtimeMillis: Long,
    ): Boolean {
        if (emitted) return false

        val observed = FullscreenOrientationPolicy.classify(orientationDegrees)
        if (observed == PhysicalOrientationBand.TRANSITION || observed != targetBand) {
            candidate = observed
            candidateSinceMillis = null
            return false
        }

        if (candidate != observed) {
            candidate = observed
            candidateSinceMillis = elapsedRealtimeMillis
            return false
        }

        val candidateSince = candidateSinceMillis ?: elapsedRealtimeMillis.also {
            candidateSinceMillis = it
        }
        if (elapsedRealtimeMillis - candidateSince < stabilityMillis) return false

        emitted = true
        return true
    }
}

class FullscreenOrientationLatch(
    private val stabilityMillis: Long = FullscreenOrientationPolicy.STABILITY_MILLIS,
) {
    private var candidate = PhysicalOrientationBand.TRANSITION
    private var candidateSinceMillis: Long? = null
    private var landscapeConfirmed = false
    private var exitEmitted = false

    init {
        require(stabilityMillis >= 0L) { "Stability duration must not be negative." }
    }

    fun reset() {
        candidate = PhysicalOrientationBand.TRANSITION
        candidateSinceMillis = null
        landscapeConfirmed = false
        exitEmitted = false
    }

    fun onOrientation(
        orientationDegrees: Int,
        elapsedRealtimeMillis: Long,
    ): Boolean {
        if (exitEmitted) return false

        val observed = FullscreenOrientationPolicy.classify(orientationDegrees)
        if (observed == PhysicalOrientationBand.TRANSITION) {
            candidate = PhysicalOrientationBand.TRANSITION
            candidateSinceMillis = null
            return false
        }

        if (observed != candidate) {
            candidate = observed
            candidateSinceMillis = elapsedRealtimeMillis
            return false
        }

        val candidateSince = candidateSinceMillis ?: elapsedRealtimeMillis.also {
            candidateSinceMillis = it
        }
        if (elapsedRealtimeMillis - candidateSince < stabilityMillis) return false

        if (!landscapeConfirmed) {
            if (observed == PhysicalOrientationBand.LANDSCAPE) {
                landscapeConfirmed = true
            }
            return false
        }

        if (observed == PhysicalOrientationBand.PORTRAIT) {
            exitEmitted = true
            return true
        }
        return false
    }
}
