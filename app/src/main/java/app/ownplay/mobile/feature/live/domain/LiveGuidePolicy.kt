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

        // Some Xtream providers omit timing entirely in get_short_epg while retaining list order.
        // Only use list-order fallback when timing is genuinely absent; never relabel stale timed
        // programs as current after their end time has passed.
        val timingAbsent = usable.all { program ->
            program.startEpochSeconds == null && program.endEpochSeconds == null
        }
        if (!timingAbsent) return LiveNowNext()

        return LiveNowNext(
            now = usable.getOrNull(0),
            next = usable.getOrNull(1),
        )
    }

    fun nextBoundaryEpochSeconds(
        guide: LiveNowNext,
        nowEpochSeconds: Long,
    ): Long? = sequenceOf(
        guide.now?.endEpochSeconds,
        guide.next?.startEpochSeconds,
    )
        .filterNotNull()
        .filter { boundary -> boundary > nowEpochSeconds }
        .minOrNull()

    fun refreshDelayMs(guide: LiveNowNext, nowMs: Long): Long {
        if (guide.now == null && guide.next == null) return EMPTY_GUIDE_RETRY_MS
        val boundary = nextBoundaryEpochSeconds(guide, nowMs / 1_000L)
        return boundary
            ?.let { epochSeconds -> (epochSeconds * 1_000L + 100L - nowMs).coerceAtLeast(MIN_REFRESH_DELAY_MS) }
            ?.coerceAtMost(MAX_REFRESH_DELAY_MS)
            ?: MAX_REFRESH_DELAY_MS
    }

    private const val EMPTY_GUIDE_RETRY_MS = 15_000L
    private const val MIN_REFRESH_DELAY_MS = 250L
    private const val MAX_REFRESH_DELAY_MS = 125_000L
}
