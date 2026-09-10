package app.ownplay.mobile.playback.domain

object PlayerLocalControlPolicy {
    const val VOLUME_STEP: Float = 0.05f
    const val MIN_BRIGHTNESS: Float = 0.05f
    const val MAX_LEVEL: Float = 1f
    const val GESTURE_SENSITIVITY: Float = 1.2f

    fun canHandlePlayerControls(
        target: VideoTarget,
        mediaId: String?,
    ): Boolean = mediaId != null && (
        target == VideoTarget.PREVIEW || target == VideoTarget.FULLSCREEN
    )

    fun clampVolume(value: Float): Float = value.coerceIn(0f, MAX_LEVEL)

    fun clampBrightness(value: Float): Float = value.coerceIn(MIN_BRIGHTNESS, MAX_LEVEL)

    fun volumeAfterStep(current: Float, direction: Int): Float {
        val signedStep = when {
            direction > 0 -> VOLUME_STEP
            direction < 0 -> -VOLUME_STEP
            else -> 0f
        }
        return clampVolume(current + signedStep)
    }

    fun normalizedGestureDelta(
        deltaY: Float,
        surfaceHeight: Float,
    ): Float {
        if (surfaceHeight <= 0f) return 0f
        return (-deltaY / surfaceHeight * GESTURE_SENSITIVITY).coerceIn(-1f, 1f)
    }
}
