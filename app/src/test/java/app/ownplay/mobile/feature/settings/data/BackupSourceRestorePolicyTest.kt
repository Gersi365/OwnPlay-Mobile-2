package app.ownplay.mobile.feature.settings.data

import app.ownplay.mobile.sources.domain.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupSourceRestorePolicyTest {
    @Test
    fun configuredXtreamKeepsExistingEndpoint() {
        val resolved = BackupSourceRestorePolicy.resolveBaseLocator(
            sourceType = SourceType.XTREAM.name,
            existingBaseLocator = "https://trusted.example",
            existingCredentialReference = "source-1",
            importedSafeLocator = "https://imported.example",
        )

        assertEquals("https://trusted.example", resolved)
    }

    @Test
    fun credentialFreeXtreamCanAdoptImportedSafeEndpoint() {
        val resolved = BackupSourceRestorePolicy.resolveBaseLocator(
            sourceType = SourceType.XTREAM.name,
            existingBaseLocator = "https://reconnect.invalid/ownplay",
            existingCredentialReference = null,
            importedSafeLocator = "https://provider.example",
        )

        assertEquals("https://provider.example", resolved)
    }

    @Test
    fun m3uNeverImportsRemoteLocatorFromOrdinaryBackup() {
        val resolved = BackupSourceRestorePolicy.resolveBaseLocator(
            sourceType = SourceType.M3U.name,
            existingBaseLocator = "https://redacted-existing.example/list.m3u",
            existingCredentialReference = "source-2",
            importedSafeLocator = "https://reconnect.invalid/ownplay",
        )

        assertEquals("https://redacted-existing.example/list.m3u", resolved)
    }
}
