package app.ownplay.mobile.feature.live.ui

import app.ownplay.mobile.feature.live.domain.LiveOrganizationChannel

object LiveChannelDisplayPolicy {
    fun displayName(
        channel: LiveOrganizationChannel,
        preferTvgName: Boolean,
        hideChannelPrefix: Boolean = false,
    ): String {
        val localName = channel.localName?.trim()?.takeIf(String::isNotEmpty)
        val tvgName = channel.tvgName?.trim()?.takeIf(String::isNotEmpty)
        val base = localName ?: if (preferTvgName && tvgName != null) tvgName else channel.name.trim()
        return ChannelNamePrefixPolicy.displayName(base, hideChannelPrefix)
    }
}

internal object ChannelNamePrefixPolicy {
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
