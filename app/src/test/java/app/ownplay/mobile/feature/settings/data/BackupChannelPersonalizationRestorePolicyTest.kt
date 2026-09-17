package app.ownplay.mobile.feature.settings.data

import app.ownplay.mobile.data.db.ChannelPersonalizationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupChannelPersonalizationRestorePolicyTest {
    @Test fun legacyV1RestorePreservesExistingLocalLogo() {
        val existing = ChannelPersonalizationEntity(
            channelId = "channel-1", favorite = false, hidden = false, localName = "Current",
            localLogo = "https://images.test/custom.png", manualOrder = 9,
        )
        val record = BackupChannelPersonalizationRecord(
            sourceId = "source-1", channelId = "channel-1", favorite = true, hidden = true,
            localName = "Backup", localLogoIncluded = false, manualOrder = 2,
        )
        val restored = BackupChannelPersonalizationRestorePolicy.merge(record, existing)
        assertEquals(true, restored.favorite)
        assertEquals(true, restored.hidden)
        assertEquals("Backup", restored.localName)
        assertEquals(2, restored.manualOrder)
        assertEquals("https://images.test/custom.png", restored.localLogo)
    }

    @Test fun currentV1RestoreAppliesBackedUpLocalLogo() {
        val existing = ChannelPersonalizationEntity(
            channelId = "channel-1", localLogo = "https://images.test/current.png",
        )
        val record = BackupChannelPersonalizationRecord(
            sourceId = "source-1", channelId = "channel-1", favorite = false, hidden = false,
            localName = null, localLogo = "https://images.test/backup.png", manualOrder = null,
        )
        assertEquals(
            "https://images.test/backup.png",
            BackupChannelPersonalizationRestorePolicy.merge(record, existing).localLogo,
        )
    }

    @Test fun currentV1RestoreCanClearLocalLogo() {
        val existing = ChannelPersonalizationEntity(
            channelId = "channel-1", localLogo = "https://images.test/current.png",
        )
        val record = BackupChannelPersonalizationRecord(
            sourceId = "source-1", channelId = "channel-1", favorite = false, hidden = false,
            localName = null, localLogo = null, manualOrder = null,
        )
        assertNull(BackupChannelPersonalizationRestorePolicy.merge(record, existing).localLogo)
    }
}
