package app.ownplay.mobile.sources.data

import org.junit.Assert.*
import org.junit.Test

class M3uIdentityReconciliationTest {
    @Test fun `adding duplicate tvg id preserves original and does not transfer its personalization identity`() {
        val original = row("id", "a", unique = true)
        val result = M3uIdentityReconciliation.reconcile("source", listOf(
            row("id", "b", unique = false), row("id", "a", unique = false).copy(name = "Renamed"),
        ), listOf(original.persisted()))
        assertEquals(original.channelId, result.single { it.streamLocator.endsWith("/a") }.channelId)
        assertNotEquals(original.channelId, result.single { it.streamLocator.endsWith("/b") }.channelId)
        assertEquals("Renamed", result.single { it.channelId == original.channelId }.name)
    }

    @Test fun `removing duplicate tvg id keeps remaining URL identity through future refreshes`() {
        val a = row("id", "a", unique = false)
        val b = row("id", "b", unique = false)
        val result = M3uIdentityReconciliation.reconcile("source", listOf(row("id", "b", unique = true)),
            listOf(a.persisted(), b.persisted()))
        assertEquals(b.channelId, result.single().channelId)
        val again = M3uIdentityReconciliation.reconcile("source", listOf(row("id", "b", unique = true)),
            listOf(a.persisted().copy(available = false), result.single().persisted()))
        assertEquals(b.channelId, again.single().channelId)
    }

    @Test fun `new remaining channel must not claim identity reserved by a removed ambiguous channel`() {
        val a = row("id", "a", unique = true)
        val b = row("id", "b", unique = false)
        val result = M3uIdentityReconciliation.reconcile("source", listOf(row("id", "c", unique = true)),
            listOf(a.persisted().copy(available = false), b.persisted().copy(available = false)))
        assertNotEquals(a.channelId, result.single().channelId)
        assertNotEquals(b.channelId, result.single().channelId)
    }

    @Test fun `unique tvg id survives locator update and rename without changing hash baseline`() {
        val old = row("id", "old", unique = true)
        val incoming = row("id", "new", unique = true).copy(name = "New name", categoryKey = "new group")
        val result = M3uIdentityReconciliation.reconcile("source", listOf(incoming), listOf(old.persisted()))
        assertEquals(old.channelId, result.single().channelId)
        assertEquals(incoming.streamLocator, result.single().streamLocator)
    }

    @Test fun `reordered batch reserves all exact matches before assigning new IDs`() {
        val a = row("id", "a", unique = true)
        val b = row("id", "b", unique = false)
        val previous = listOf(a.persisted(), b.persisted())
        val first = M3uIdentityReconciliation.reconcile("source", listOf(b, a.copy(channelId = b.channelId)), previous)
        val second = M3uIdentityReconciliation.reconcile("source", listOf(a, b), previous)
        assertEquals(second.associate { it.streamLocator to it.channelId }, first.associate { it.streamLocator to it.channelId })
        assertEquals(2, first.map { it.channelId }.distinct().size)
    }

    @Test fun `new source retains existing deterministic identity format`() {
        val rows = listOf(row("a", "a", true), row(null, "b", false))
        assertEquals(rows, M3uIdentityReconciliation.reconcile("source", rows, emptyList()))
    }

    private fun row(tvg: String?, locator: String, unique: Boolean): ProviderLiveChannelRecord {
        val uri = "http://fixture.invalid/$locator"
        return ProviderLiveChannelRecord(
            channelId = StableIdentity.m3uChannelId("source", tvg, unique, uri, "Name", "group"),
            providerKey = tvg ?: uri, providerStreamId = null, categoryKey = "group", name = "Name",
            tvgId = tvg, tvgName = "Name", logoUrl = null, streamLocator = uri, providerOrder = 0,
        )
    }
    private fun ProviderLiveChannelRecord.persisted() = PersistedM3uIdentity(channelId, tvgId, streamLocator, true)
}
