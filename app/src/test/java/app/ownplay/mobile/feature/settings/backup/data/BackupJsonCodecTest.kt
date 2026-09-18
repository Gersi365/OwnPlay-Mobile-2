package app.ownplay.mobile.feature.settings.backup.data

import app.ownplay.mobile.downloads.domain.DownloadPreferences
import app.ownplay.mobile.feature.live.domain.LiveOrganizationMode
import app.ownplay.mobile.feature.playback.domain.PlaybackPreferences
import app.ownplay.mobile.feature.settings.backup.domain.BackupCategoryPersonalization
import app.ownplay.mobile.feature.settings.backup.domain.BackupCatalogKind
import app.ownplay.mobile.feature.settings.backup.domain.BackupGlobalSettings
import app.ownplay.mobile.feature.settings.backup.domain.BackupSourceDefinition
import app.ownplay.mobile.feature.settings.backup.domain.BackupSourceSettings
import app.ownplay.mobile.feature.settings.backup.domain.BackupValidationCode
import app.ownplay.mobile.feature.settings.backup.domain.OwnPlayBackupEnvelope
import app.ownplay.mobile.feature.settings.backup.domain.OwnPlayBackupPayload
import app.ownplay.mobile.feature.settings.domain.DisplayPreferences
import app.ownplay.mobile.feature.settings.domain.SourceRefreshSchedule
import app.ownplay.mobile.sources.domain.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupJsonCodecTest {
    @Test
    fun roundTripPreservesVersionedNonSecretPayload() {
        val envelope = sampleEnvelope()
        val encoded = BackupJsonCodec.encode(envelope)

        assertFalse(encoded.contains("password", ignoreCase = true))
        assertFalse(encoded.contains("credentialReference", ignoreCase = true))
        val decoded = BackupJsonCodec.decode(encoded)
        assertTrue(decoded is BackupDecodeResult.Success)
        assertEquals(envelope, (decoded as BackupDecodeResult.Success).envelope)
    }

    @Test
    fun olderVersionOneBackupWithoutDisplayExtensionsUsesCompatibleDefaults() {
        val encoded = BackupJsonCodec.encode(sampleEnvelope())
        val legacy = encoded
            .replace(Regex("""\s*"showChannelLogos"\s*:\s*(true|false)\s*,?"""), "")
            .replace(Regex("""\s*"preferTvgName"\s*:\s*(true|false)\s*,?"""), "")

        val result = BackupJsonCodec.decode(legacy)
        assertTrue(result is BackupDecodeResult.Success)
        val display = (result as BackupDecodeResult.Success).envelope.payload.globalSettings.display
        assertTrue(display.showChannelLogos)
        assertFalse(display.preferTvgName)
    }

    @Test
    fun decodeRejectsForbiddenSecretFieldsBeforeRestoreModelCreation() {
        val encoded = BackupJsonCodec.encode(sampleEnvelope())
        val malicious = encoded.replace(
            "\"displayName\": \"Living Room\"",
            "\"displayName\": \"Living Room\", \"password\": \"secret\"",
        )

        val result = BackupJsonCodec.decode(malicious)
        assertTrue(result is BackupDecodeResult.Failure)
        assertTrue(
            (result as BackupDecodeResult.Failure).issues.any {
                it.code == BackupValidationCode.FORBIDDEN_SECRET_FIELD
            },
        )
    }

    @Test
    fun decodeRejectsUnsafeLocatorAndUnknownActiveSource() {
        val invalid = sampleEnvelope().copy(
            payload = sampleEnvelope().payload.copy(
                sources = listOf(
                    BackupSourceDefinition(
                        sourceId = "source-1",
                        type = SourceType.XTREAM,
                        displayName = "Living Room",
                        baseLocator = "https://user:pass@example.com/player?token=secret",
                        enabled = true,
                    ),
                ),
                activeSourceId = "missing-source",
            ),
        )

        val issues = app.ownplay.mobile.feature.settings.backup.domain.BackupValidator.validate(invalid)
        assertTrue(issues.any { it.code == BackupValidationCode.INVALID_FIELD })
        assertTrue(issues.any { it.code == BackupValidationCode.INVALID_REFERENCE })
    }

    @Test
    fun validatorRejectsDuplicateSourceConnectionIdentity() {
        val first = sampleEnvelope().payload.sources.single()
        val invalid = sampleEnvelope().copy(
            payload = sampleEnvelope().payload.copy(
                sources = listOf(first, first.copy(sourceId = "source-2")),
            ),
        )

        val issues = app.ownplay.mobile.feature.settings.backup.domain.BackupValidator.validate(invalid)
        assertTrue(issues.any {
            it.code == BackupValidationCode.DUPLICATE_IDENTITY &&
                it.path == "$.payload.sources[].baseLocator"
        })
    }

    private fun sampleEnvelope(): OwnPlayBackupEnvelope = OwnPlayBackupEnvelope(
        createdAt = "2026-09-18T06:00:00Z",
        payload = OwnPlayBackupPayload(
            sources = listOf(
                BackupSourceDefinition(
                    sourceId = "source-1",
                    type = SourceType.XTREAM,
                    displayName = "Living Room",
                    baseLocator = "https://example.com/player",
                    enabled = true,
                ),
            ),
            activeSourceId = "source-1",
            globalSettings = BackupGlobalSettings(
                display = DisplayPreferences(compactMediaRows = true, showChannelLogos = false, preferTvgName = true),
                playback = PlaybackPreferences(automaticPictureInPicture = false),
                downloads = DownloadPreferences(unmeteredNetworkOnly = true),
            ),
            sourceSettings = listOf(
                BackupSourceSettings(
                    sourceId = "source-1",
                    refreshSchedule = SourceRefreshSchedule.DAILY,
                    liveOrganizationMode = LiveOrganizationMode.OWNPLAY,
                ),
            ),
            categoryPersonalization = listOf(
                BackupCategoryPersonalization(
                    sourceId = "source-1",
                    kind = BackupCatalogKind.LIVE,
                    categoryKey = "news",
                    hidden = true,
                    manualOrder = 2,
                ),
            ),
        ),
    )
}
