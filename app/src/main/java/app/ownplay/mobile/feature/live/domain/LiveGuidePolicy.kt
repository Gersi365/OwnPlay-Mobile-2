package app.ownplay.mobile.feature.live.domain

object LiveGuidePolicy {
    fun nowNext(
        programs: List<LiveProgram>,
        nowEpochSeconds: Long,
    ): LiveNowNext {
        val usable = programs.filter { it.title.isNotBlank() }
        if (usable.isEmpty()) return LiveNowNext()

        val current = usable.firstOrNull { program ->
            val start = program.startEpochSeconds
            val end = program.endEpochSeconds
            start != null && end != null && start <= nowEpochSeconds && nowEpochSeconds < end
        }

        val next = when {
            current != null -> {
                val currentIndex = usable.indexOf(current)
                usable.drop(currentIndex + 1).firstOrNull { candidate ->
                    candidate.startEpochSeconds == null || candidate.startEpochSeconds >= (current.endEpochSeconds ?: nowEpochSeconds)
                }
            }
            else -> usable.firstOrNull { candidate ->
                candidate.startEpochSeconds?.let { it > nowEpochSeconds } == true
            }
        }

        if (current != null || next != null) {
            return LiveNowNext(now = current, next = next)
        }

        // Some Xtream providers omit timestamps in get_short_epg while retaining list order.
        return LiveNowNext(
            now = usable.getOrNull(0),
            next = usable.getOrNull(1),
        )
    }
}
