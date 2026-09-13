package app.ownplay.mobile.feature.settings.data

import app.ownplay.mobile.data.db.ChannelPersonalizationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupChannelPersonalizationRestorePolicyTest {
    @Test fun v1RestorePreservesExistingLocalLogo() {
        val existing = ChannelPersonalizationEntity(
            channelId = "channel-1", favorite = false, hidden = false, localName = "Current",
            localLogo = "https://images.test/custom.png", manualOrder = 9,
        )
        val record = BackupChannelPersonalizationRecord(
            sourceId = "source-1", channelId = "channel-1", favorite = true, hidden = true,
            localName = "Backup", manualOrder = 2,
        )
        val restored = BackupChannelPersonalizationRestorePolicy.merge(record, existing)
        assertEquals(true, restored.favorite)
        assertEquals(true, restored.hidden)
        assertEquals("Backup", restored.localName)
        assertEquals(2, restored.manualOrder)
        assertEquals("https://images.test/custom.png", restored.localLogo)
    }

    @Test fun v1RestoreDoesNotInventLocalLogo() {
        val record = BackupChannelPersonalizationRecord(
            sourceId = "source-1", channelId = "channel-1", favorite = false, hidden = false,
            localName = null, manualOrder = null,
        )
        assertNull(BackupChannelPersonalizationRestorePolicy.merge(record, null).localLogo)
    }
}
