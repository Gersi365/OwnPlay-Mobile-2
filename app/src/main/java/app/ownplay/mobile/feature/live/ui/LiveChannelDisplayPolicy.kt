package app.ownplay.mobile.feature.live.ui

import app.ownplay.mobile.feature.live.domain.LiveOrganizationChannel

object LiveChannelDisplayPolicy {
    fun displayName(channel: LiveOrganizationChannel, preferTvgName: Boolean): String {
        val tvgName = channel.tvgName?.trim()?.takeIf(String::isNotEmpty)
        return if (preferTvgName && tvgName != null) tvgName else channel.name
    }
}
