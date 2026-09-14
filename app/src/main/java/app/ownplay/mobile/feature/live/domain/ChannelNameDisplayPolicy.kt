package app.ownplay.mobile.feature.live.domain

object ChannelNameDisplayPolicy {
    fun displayName(rawName: String, hidePrefix: Boolean): String {
        val trimmed = rawName.trim()
        if (!hidePrefix) return trimmed

        val separatorIndex = trimmed.indexOf('|')
        if (separatorIndex <= 0) return trimmed

        val prefix = trimmed.substring(0, separatorIndex).trim()
        val remainder = trimmed.substring(separatorIndex + 1).trimStart()
        return if (prefix.isNotEmpty() && remainder.isNotEmpty()) remainder else trimmed
    }
}
