package app.ownplay.mobile.feature.settings.data

import app.ownplay.mobile.feature.settings.domain.ProviderRefreshInterval
import app.ownplay.mobile.feature.settings.domain.SettingsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {
    @Test
    fun `backup round trip preserves supported non-secret state`() {
        val document = sampleDocument()

        val encoded = BackupCodec.encode(document)
        val decoded = BackupCodec.decode(encoded)

        assertEquals(document, decoded)
        assertFalse(encoded.contains("credential", ignoreCase = true))
        assertFalse(encoded.contains("username", ignoreCase = true))
        assertFalse(encoded.contains("password", ignoreCase = true))
        assertFalse(encoded.contains("streamLocator", ignoreCase = true))
        assertFalse(encoded.contains("localReference", ignoreCase = true))
        assertNull(decoded.sources.single { it.type == "M3U" }.safeBaseLocator)
    }

    @Test
    fun `unsupported backup version is rejected`() {
        val encoded = BackupCodec.encode(sampleDocument())
            .replace("\"version\":1", "\"version\":2")

        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode(encoded)
        }
    }

    @Test
    fun `pending restore round trip keeps only deferred personalization`() {
        val pending = PendingRestoreDocument(
            channelPersonalization = listOf(
                BackupChannelPersonalizationRecord(
                    sourceId = "source-a",
                    channelId = "channel-a",
                    favorite = true,
                    hidden = false,
                    localName = "Local channel",
                    manualOrder = 2,
                ),
            ),
            memberships = listOf(
                BackupMembershipRecord(
                    sourceId = "source-a",
                    groupId = "group-a",
                    channelId = "channel-a",
                    manualOrder = 1,
                ),
            ),
        )

        assertEquals(pending, BackupCodec.decodePending(BackupCodec.encodePending(pending)))
    }

    private fun sampleDocument() = BackupDocument(
        generatedAt = 100,
        activeSourceId = "source-a",
        settings = SettingsSnapshot(
            pictureInPictureEnabled = true,
            resumePlaybackEnabled = false,
            autoRefreshProviders = true,
            providerRefreshInterval = ProviderRefreshInterval.TWELVE_HOURS,
            showChannelLogos = true,
        ),
        sources = listOf(
            BackupSourceRecord(
                sourceId = "source-a",
                displayName = "Home",
                type = "XTREAM",
                safeBaseLocator = "https://example.com/provider",
                enabled = true,
                createdAt = 1,
            ),
            BackupSourceRecord(
                sourceId = "source-b",
                displayName = "Playlist",
                type = "M3U",
                safeBaseLocator = null,
                enabled = true,
                createdAt = 2,
            ),
        ),
        channelPersonalization = listOf(
            BackupChannelPersonalizationRecord(
                sourceId = "source-a",
                channelId = "channel-a",
                favorite = true,
                hidden = false,
                localName = "Sports",
                manualOrder = 4,
            ),
        ),
        groups = listOf(BackupGroupRecord("group-a", "source-a", "Favorites", 0)),
        memberships = listOf(BackupMembershipRecord("source-a", "group-a", "channel-a", 0)),
        favorites = listOf(BackupFavoriteRecord("source-a", "MOVIE", "movie-a", 10)),
        progress = listOf(BackupProgressRecord("source-a", "MOVIE", "movie-a", 1000, 5000, false, 20)),
    )
}
