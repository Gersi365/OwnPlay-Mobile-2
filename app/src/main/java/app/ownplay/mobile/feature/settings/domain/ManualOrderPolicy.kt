package app.ownplay.mobile.feature.settings.domain

object ManualOrderPolicy {
    fun move(ids: List<String>, id: String, direction: Int): List<String> {
        if (direction == 0 || ids.size < 2) return ids
        val from = ids.indexOf(id)
        if (from < 0) return ids
        val to = from + if (direction > 0) 1 else -1
        if (to !in ids.indices) return ids
        return ids.toMutableList().also { list ->
            val value = list[from]
            list[from] = list[to]
            list[to] = value
        }
    }
}
