from pathlib import Path


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)


def write(path, content):
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content)


write("app/src/main/java/app/ownplay/mobile/sources/data/ProviderPayloadValidation.kt", '''package app.ownplay.mobile.sources.data

internal object ProviderPayloadValidation {
    fun m3uFailureCode(parsedEntryCount: Int, diagnosticCount: Int, resolvedEntryCount: Int): String? = when {
        parsedEntryCount == 0 && diagnosticCount > 0 -> "M3U_PARSE_CONTENT"
        parsedEntryCount > 0 && resolvedEntryCount == 0 -> "M3U_LOCATOR_CONTENT"
        else -> null
    }

    fun xtreamArrayFailureCode(rawRowCount: Int, mappedRowCount: Int): String? =
        if (rawRowCount > 0 && mappedRowCount == 0) "XTREAM_ARRAY_CONTENT" else null

    fun <T> distinctM3uByLocator(rows: List<Pair<T, String>>): List<Pair<T, String>> =
        rows.distinctBy { (_, locator) -> locator }
}
''')

path = Path("app/src/main/java/app/ownplay/mobile/sources/data/SourceCatalogLoader.kt")
text = path.read_text()
text = replace_once(text, '''                val parsed = m3uParser.parse(fetch.value)
                val resolvedEntries = parsed.entries.mapNotNull { entry ->
                    resolveLocator(locator, entry.streamLocator)?.let { resolved -> entry to resolved }
                }
                val visibleEntries = resolvedEntries.filterNot { (entry, _) ->
                    ProviderCategoryVisibility.isUtilityLabel(entry.groupTitle.orEmpty())
                }
''', '''                val parsed = m3uParser.parse(fetch.value)
                val resolvedEntries = parsed.entries.mapNotNull { entry ->
                    resolveLocator(locator, entry.streamLocator)?.let { resolved -> entry to resolved }
                }
                val validationFailure = ProviderPayloadValidation.m3uFailureCode(
                    parsedEntryCount = parsed.entries.size,
                    diagnosticCount = parsed.diagnostics.size,
                    resolvedEntryCount = resolvedEntries.size,
                )
                if (validationFailure != null) {
                    return failedPayload(validationFailure)
                }
                val visibleEntries = ProviderPayloadValidation.distinctM3uByLocator(
                    resolvedEntries.filterNot { (entry, _) ->
                        ProviderCategoryVisibility.isUtilityLabel(entry.groupTitle.orEmpty())
                    },
                )
''', "M3U payload guard")
path.write_text(text)

path = Path("app/src/main/java/app/ownplay/mobile/sources/data/xtream/OkHttpXtreamClient.kt")
text = path.read_text()
text = replace_once(text, '''import app.ownplay.mobile.sources.data.ProviderHttpTransport
import app.ownplay.mobile.sources.data.TransportResult
''', '''import app.ownplay.mobile.sources.data.ProviderHttpTransport
import app.ownplay.mobile.sources.data.ProviderPayloadValidation
import app.ownplay.mobile.sources.data.TransportResult
''', "Xtream validation import")
text = replace_once(text, '''                val array = root.value.asArray() ?: return XtreamResult.Failure("XTREAM_ARRAY_FORMAT")
                XtreamResult.Success(
                    array.mapIndexedNotNull { index, element ->
                        element.asObject()?.let { mapper(it, index) }
                    },
                )
''', '''                val array = root.value.asArray() ?: return XtreamResult.Failure("XTREAM_ARRAY_FORMAT")
                val mapped = array.mapIndexedNotNull { index, element ->
                    element.asObject()?.let { mapper(it, index) }
                }
                ProviderPayloadValidation.xtreamArrayFailureCode(
                    rawRowCount = array.size,
                    mappedRowCount = mapped.size,
                )?.let { code ->
                    return XtreamResult.Failure(code)
                }
                XtreamResult.Success(mapped)
''', "Xtream content guard")
path.write_text(text)

path = Path("app/src/main/java/app/ownplay/mobile/sources/data/SourceRepositoryImpl.kt")
text = path.read_text()
text = replace_once(text, '''import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
''', '''import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
''', "Refresh mutex imports")
text = replace_once(text, ''') : SourceRepository {
    override fun observeSources(): Flow<List<Source>> = sourceDao.observeAll().map { rows ->
''', ''') : SourceRepository {
    // Reconciliation marks missing rows unavailable by generation; overlapping refreshes must not race.
    private val refreshMutex = Mutex()

    override fun observeSources(): Flow<List<Source>> = sourceDao.observeAll().map { rows ->
''', "Refresh mutex field")
text = replace_once(text, '''    override suspend fun refresh(sourceId: String): SourceResult<RefreshSummary> {
''', '''    override suspend fun refresh(sourceId: String): SourceResult<RefreshSummary> = refreshMutex.withLock {
''', "Refresh serialization")
path.write_text(text)

path = Path("app/src/main/java/app/ownplay/mobile/data/db/BackupDao.kt")
text = path.read_text()
text = replace_once(text, '''    suspend fun getChannelPersonalization(): List<BackupChannelPersonalizationView>

    @Query("SELECT * FROM custom_groups ORDER BY sourceId ASC, manualOrder ASC, groupId ASC")
''', '''    suspend fun getChannelPersonalization(): List<BackupChannelPersonalizationView>

    @Query("SELECT * FROM channel_personalization WHERE channelId = :channelId LIMIT 1")
    suspend fun getChannelPersonalizationRow(channelId: String): ChannelPersonalizationEntity?

    @Query("SELECT * FROM custom_groups ORDER BY sourceId ASC, manualOrder ASC, groupId ASC")
''', "Backup personalization lookup")
path.write_text(text)

write("app/src/main/java/app/ownplay/mobile/feature/settings/data/BackupChannelPersonalizationRestorePolicy.kt", '''package app.ownplay.mobile.feature.settings.data

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
''')

path = Path("app/src/main/java/app/ownplay/mobile/feature/settings/data/BackupRepositoryImpl.kt")
text = path.read_text()
text = replace_once(text, 'import app.ownplay.mobile.data.db.ChannelPersonalizationEntity\n', '', "Remove entity import")
text = replace_once(text, '''                        if (backupDao.hasLiveChannel(record.sourceId, record.channelId)) {
                            backupDao.upsertChannelPersonalization(record.toEntity())
                            personalizationRestored += 1
''', '''                        if (backupDao.hasLiveChannel(record.sourceId, record.channelId)) {
                            val existingPersonalization = backupDao.getChannelPersonalizationRow(record.channelId)
                            backupDao.upsertChannelPersonalization(
                                BackupChannelPersonalizationRestorePolicy.merge(record, existingPersonalization),
                            )
                            personalizationRestored += 1
''', "Immediate personalization restore")
text = replace_once(text, '''                    } else if (backupDao.hasLiveChannel(record.sourceId, record.channelId)) {
                        backupDao.upsertChannelPersonalization(record.toEntity())
                        applied += 1
''', '''                    } else if (backupDao.hasLiveChannel(record.sourceId, record.channelId)) {
                        val existingPersonalization = backupDao.getChannelPersonalizationRow(record.channelId)
                        backupDao.upsertChannelPersonalization(
                            BackupChannelPersonalizationRestorePolicy.merge(record, existingPersonalization),
                        )
                        applied += 1
''', "Pending personalization restore")
text = replace_once(text, '''    private fun BackupChannelPersonalizationRecord.toEntity() = ChannelPersonalizationEntity(
        channelId = channelId,
        favorite = favorite,
        hidden = hidden,
        localName = localName,
        localLogo = null,
        manualOrder = manualOrder,
    )

''', '', "Remove destructive conversion")
path.write_text(text)

path = Path("app/src/main/java/app/ownplay/mobile/playback/Media3PlaybackController.kt")
text = path.read_text()
text = replace_once(text, '''                    subtitleSelection = selection
                }
            }
            refreshSnapshot()
        }
    }

    override suspend fun setPlaybackSpeed(speed: Float) {
''', '''                    subtitleSelection = selection
                }
            }
            currentSubtitleCues = emptyList()
            refreshSnapshot()
        }
    }

    override suspend fun setPlaybackSpeed(speed: Float) {
''', "Subtitle cue clearing")
path.write_text(text)

path = Path("app/src/main/java/app/ownplay/mobile/sources/domain/SourceRefreshFailurePolicy.kt")
text = path.read_text()
text = replace_once(text, '''            has("XTREAM_JSON") || has("XTREAM_ARRAY_FORMAT") || hasPrefix("XTREAM_") -> SourceError(
''', '''            hasPrefix("M3U_") -> SourceError(
                code = "REFRESH_M3U_FORMAT",
                safeMessage = "The playlist was read, but it did not contain a usable M3U catalog. The last known catalog was preserved.",
            )

            has("XTREAM_JSON") || has("XTREAM_ARRAY_FORMAT") || hasPrefix("XTREAM_") -> SourceError(
''', "M3U safe failure")
path.write_text(text)

write("app/src/test/java/app/ownplay/mobile/sources/data/ProviderPayloadValidationTest.kt", '''package app.ownplay.mobile.sources.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderPayloadValidationTest {
    @Test fun malformedOnlyM3uIsRejectedButCleanEmptyIsAllowed() {
        assertEquals("M3U_PARSE_CONTENT", ProviderPayloadValidation.m3uFailureCode(0, 2, 0))
        assertNull(ProviderPayloadValidation.m3uFailureCode(0, 0, 0))
    }

    @Test fun m3uEntriesWithoutAnyResolvableLocatorAreRejected() {
        assertEquals("M3U_LOCATOR_CONTENT", ProviderPayloadValidation.m3uFailureCode(3, 0, 0))
        assertNull(ProviderPayloadValidation.m3uFailureCode(3, 1, 2))
    }

    @Test fun nonEmptyXtreamArrayWithNoUsableRowsIsRejected() {
        assertEquals("XTREAM_ARRAY_CONTENT", ProviderPayloadValidation.xtreamArrayFailureCode(4, 0))
        assertNull(ProviderPayloadValidation.xtreamArrayFailureCode(0, 0))
        assertNull(ProviderPayloadValidation.xtreamArrayFailureCode(4, 3))
    }

    @Test fun duplicateM3uLocatorsKeepFirstDeterministicRow() {
        val rows = listOf(
            "first" to "https://stream.test/a",
            "duplicate" to "https://stream.test/a",
            "second" to "https://stream.test/b",
        )
        assertEquals(
            listOf("first" to "https://stream.test/a", "second" to "https://stream.test/b"),
            ProviderPayloadValidation.distinctM3uByLocator(rows),
        )
    }
}
''')

write("app/src/test/java/app/ownplay/mobile/feature/settings/data/BackupChannelPersonalizationRestorePolicyTest.kt", '''package app.ownplay.mobile.feature.settings.data

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
''')

write("app/src/test/java/app/ownplay/mobile/sources/domain/SourceRefreshFailureHardeningTest.kt", '''package app.ownplay.mobile.sources.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SourceRefreshFailureHardeningTest {
    @Test fun malformedM3uPayloadUsesSafeFormatFailure() {
        val error = SourceRefreshFailurePolicy.present("M3U_PARSE_CONTENT")
        assertEquals("REFRESH_M3U_FORMAT", error.code)
        assertFalse(error.safeMessage.contains("http://"))
        assertFalse(error.safeMessage.contains("https://"))
    }

    @Test fun unusableXtreamRowsRemainXtreamFormatFailure() {
        assertEquals(
            "REFRESH_XTREAM_FORMAT",
            SourceRefreshFailurePolicy.present("XTREAM_ARRAY_CONTENT").code,
        )
    }
}
''')

print("Stage 30 hardening patch applied")
