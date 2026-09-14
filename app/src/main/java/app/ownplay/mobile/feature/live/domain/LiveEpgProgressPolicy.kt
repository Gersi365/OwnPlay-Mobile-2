package app.ownplay.mobile.feature.live.domain

object LiveEpgProgressPolicy {
    fun fraction(program: LiveProgram?, nowEpochSeconds: Long): Float? {
        val start = program?.startEpochSeconds ?: return null
        val end = program.endEpochSeconds ?: return null
        if (end <= start || nowEpochSeconds < start || nowEpochSeconds > end) return null

        return ((nowEpochSeconds - start).toDouble() / (end - start).toDouble())
            .toFloat()
            .coerceIn(0f, 1f)
    }
}
