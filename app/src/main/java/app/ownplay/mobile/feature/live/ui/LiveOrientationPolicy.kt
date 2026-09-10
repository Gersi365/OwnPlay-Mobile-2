package app.ownplay.mobile.feature.live.ui

internal object LiveOrientationPolicy {
    fun isLandscape(orientationDegrees: Int): Boolean =
        orientationDegrees in 60..120 || orientationDegrees in 240..300

    fun isPortrait(orientationDegrees: Int): Boolean =
        orientationDegrees in 0..30 || orientationDegrees in 150..210 || orientationDegrees in 330..359
}
