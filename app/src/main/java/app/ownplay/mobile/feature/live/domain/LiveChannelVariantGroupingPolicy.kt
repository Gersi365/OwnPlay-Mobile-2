package app.ownplay.mobile.feature.live.domain

object LiveChannelVariantGroupingPolicy {
    fun group(
        channels: List<LiveChannel>,
        preserveManualOrder: Boolean,
    ): List<LiveChannel> {
        if (channels.size < 2 || preserveManualOrder) return channels
        val grouped = linkedMapOf<String, MutableList<LiveChannel>>()
        channels.forEach { channel ->
            val identity = baseIdentity(channel.name).ifBlank { "channel:${channel.channelId}" }
            grouped.getOrPut(identity, ::mutableListOf).add(channel)
        }
        return grouped.values.flatten()
    }

    fun baseIdentity(name: String): String =
        LiveOwnPlayDiscoveryPolicy.normalizedIdentity(name)
}
