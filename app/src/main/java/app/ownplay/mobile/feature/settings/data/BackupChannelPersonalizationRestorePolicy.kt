package app.ownplay.mobile.feature.settings.data

import app.ownplay.mobile.data.db.ChannelPersonalizationEntity

internal object BackupChannelPersonalizationRestorePolicy {
    fun merge(record: BackupChannelPersonalizationRecord, existing: ChannelPersonalizationEntity?): ChannelPersonalizationEntity =
        ChannelPersonalizationEntity(
            channelId = record.channelId,
            favorite = record.favorite,
            hidden = record.hidden,
            localName = record.localName,
            localLogo = existing?.localLogo,
            manualOrder = record.manualOrder,
        )
}
