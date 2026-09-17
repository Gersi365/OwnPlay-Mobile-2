package app.ownplay.mobile.sources.data

import app.ownplay.mobile.data.db.RefreshStateDao
import app.ownplay.mobile.data.db.RefreshStateEntity
import app.ownplay.mobile.data.db.SourceDao
import app.ownplay.mobile.data.db.SourceEntity
import app.ownplay.mobile.data.prefs.ActiveSourceSelectionStore
import app.ownplay.mobile.data.security.CredentialStore
import app.ownplay.mobile.data.security.SourceSecret
import app.ownplay.mobile.sources.domain.SourceId
import app.ownplay.mobile.sources.domain.SourceInput
import app.ownplay.mobile.sources.domain.SourceMutationRejection
import app.ownplay.mobile.sources.domain.SourceMutationResult
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceRepositoryImplTest {
    @Test
    fun addXtreamSourceStoresCredentialsOutsideRoomAndSelectsFirstSource() = runBlocking {
        val sourceDao = FakeSourceDao()
        val refreshStateDao = FakeRefreshStateDao()
        val activeSourceStore = FakeActiveSourceStore()
        val credentialStore = FakeCredentialStore()
        val repository = repository(
            sourceDao = sourceDao,
            refreshStateDao = refreshStateDao,
            activeSourceStore = activeSourceStore,
            credentialStore = credentialStore,
        )

        val result = repository.addSource(
            SourceInput.Xtream(
                displayName = "  Home  ",
                serverUrl = "HTTPS://Example.COM/portal/",
                username = "alice",
                password = "secret-password",
            ),
        )

        assertEquals(SourceMutationResult.Success(SourceId("source-1")), result)
        val row = sourceDao.get("source-1")!!
        assertEquals("Home", row.displayName)
        assertEquals("XTREAM", row.type)
        assertEquals("https://example.com/portal", row.baseLocator)
        assertEquals("source-1", row.credentialReference)
        assertFalse(row.toString().contains("alice"))
        assertFalse(row.toString().contains("secret-password"))
        assertTrue(credentialStore.secrets[SourceId("source-1")] is SourceSecret.Xtream)
        assertEquals("source-1", activeSourceStore.currentSelectedSourceId())
    }

    @Test
    fun addM3uSourceRedactsSecretBearingLocatorAndReportsRefreshState() = runBlocking {
        val sourceDao = FakeSourceDao()
        val refreshStateDao = FakeRefreshStateDao()
        val activeSourceStore = FakeActiveSourceStore()
        val credentialStore = FakeCredentialStore()
        val repository = repository(
            sourceDao = sourceDao,
            refreshStateDao = refreshStateDao,
            activeSourceStore = activeSourceStore,
            credentialStore = credentialStore,
        )

        repository.addSource(
            SourceInput.M3u(
                displayName = "Remote list",
                playlistUrl = "https://media.example/list.m3u?token=playlist-secret",
                epgUrl = "https://media.example/epg.xml?key=epg-secret",
            ),
        )
        refreshStateDao.upsert(
            RefreshStateEntity(
                sourceId = "source-1",
                generation = 4,
                state = "SUCCESS",
                lastAttempt = 100L,
                lastSuccess = 90L,
                errorCode = null,
            ),
        )

        val row = sourceDao.get("source-1")!!
        assertEquals("https://media.example/list.m3u", row.baseLocator)
        assertFalse(row.baseLocator.contains("playlist-secret"))
        val storedSecret = credentialStore.secrets[SourceId("source-1")] as SourceSecret.M3uRemote
        assertTrue(storedSecret.playlistUrl.contains("playlist-secret"))
        assertTrue(storedSecret.epgUrl!!.contains("epg-secret"))

        val summary = repository.observeSources().first().single()
        assertEquals("https://media.example/list.m3u", summary.connectionLabel)
        assertEquals(90L, summary.lastSuccessfulRefreshAtEpochMs)
    }

    @Test
    fun duplicateConnectionIsRejectedBeforeSecondCredentialWrite() = runBlocking {
        val sourceDao = FakeSourceDao()
        val credentialStore = FakeCredentialStore()
        val repository = repository(
            sourceDao = sourceDao,
            credentialStore = credentialStore,
        )

        repository.addSource(
            SourceInput.Xtream("One", "https://example.com/api", "u1", "p1"),
        )
        val duplicate = repository.addSource(
            SourceInput.Xtream("Two", "https://EXAMPLE.com/api/", "u2", "p2"),
        )

        assertEquals(
            SourceMutationResult.Rejected(SourceMutationRejection.DUPLICATE_SOURCE),
            duplicate,
        )
        assertEquals(1, credentialStore.putCalls)
        assertEquals(1, sourceDao.getAll().size)
    }

    @Test
    fun failedRoomInsertRollsBackCredentialWrite() = runBlocking {
        val sourceDao = FakeSourceDao().apply { failInsert = true }
        val credentialStore = FakeCredentialStore()
        val repository = repository(
            sourceDao = sourceDao,
            credentialStore = credentialStore,
        )

        val result = repository.addSource(
            SourceInput.Xtream("Home", "https://example.com", "user", "password"),
        )

        assertEquals(
            SourceMutationResult.Rejected(SourceMutationRejection.STORAGE_FAILURE),
            result,
        )
        assertTrue(credentialStore.secrets.isEmpty())
        assertTrue(sourceDao.getAll().isEmpty())
    }

    @Test
    fun disabledSourceCannotBecomeActive() = runBlocking {
        val disabled = source(
            id = "disabled",
            enabled = false,
            updatedAt = 2L,
        )
        val activeSourceStore = FakeActiveSourceStore()
        val repository = repository(
            sourceDao = FakeSourceDao(listOf(disabled)),
            activeSourceStore = activeSourceStore,
        )

        assertFalse(repository.setActiveSource(SourceId("disabled")))
        assertNull(activeSourceStore.currentSelectedSourceId())
    }

    @Test
    fun removingPersistedActiveSourceSelectsNextEnabledSourceAndDeletesSecret() = runBlocking {
        val active = source(id = "source-a", enabled = true, updatedAt = 20L)
        val fallback = source(id = "source-b", enabled = true, updatedAt = 10L)
        val sourceDao = FakeSourceDao(listOf(active, fallback))
        val activeSourceStore = FakeActiveSourceStore("source-a")
        val credentialStore = FakeCredentialStore().apply {
            seed(SourceId("source-a"), SourceSecret.Xtream("a", "a-secret"))
            seed(SourceId("source-b"), SourceSecret.Xtream("b", "b-secret"))
        }
        val repository = repository(
            sourceDao = sourceDao,
            activeSourceStore = activeSourceStore,
            credentialStore = credentialStore,
        )

        assertTrue(repository.removeSource(SourceId("source-a")))
        assertNull(sourceDao.get("source-a"))
        assertEquals("source-b", activeSourceStore.currentSelectedSourceId())
        assertFalse(credentialStore.secrets.containsKey(SourceId("source-a")))
        assertTrue(credentialStore.secrets.containsKey(SourceId("source-b")))
    }

    private fun repository(
        sourceDao: FakeSourceDao = FakeSourceDao(),
        refreshStateDao: FakeRefreshStateDao = FakeRefreshStateDao(),
        activeSourceStore: FakeActiveSourceStore = FakeActiveSourceStore(),
        credentialStore: FakeCredentialStore = FakeCredentialStore(),
    ): SourceRepositoryImpl {
        val ids = AtomicInteger(0)
        return SourceRepositoryImpl(
            sourceDao = sourceDao,
            refreshStateDao = refreshStateDao,
            activeSourceStore = activeSourceStore,
            credentialStore = credentialStore,
            nowMillis = { 1_000L },
            newSourceId = { SourceId("source-${ids.incrementAndGet()}") },
        )
    }

    private fun source(
        id: String,
        enabled: Boolean,
        updatedAt: Long,
    ) = SourceEntity(
        sourceId = id,
        displayName = id,
        type = "XTREAM",
        baseLocator = "https://$id.example",
        credentialReference = id,
        enabled = enabled,
        createdAt = 1L,
        updatedAt = updatedAt,
    )
}

private class FakeSourceDao(
    initial: List<SourceEntity> = emptyList(),
) : SourceDao {
    private val rows = MutableStateFlow(sort(initial))
    var failInsert: Boolean = false

    override fun observeAll(): Flow<List<SourceEntity>> = rows

    override suspend fun get(sourceId: String): SourceEntity? =
        rows.value.firstOrNull { it.sourceId == sourceId }

    override suspend fun getAll(): List<SourceEntity> = rows.value

    override suspend fun insert(entity: SourceEntity) {
        if (failInsert) error("insert failed")
        check(rows.value.none { it.sourceId == entity.sourceId })
        rows.value = sort(rows.value + entity)
    }

    override suspend fun update(entity: SourceEntity) {
        check(rows.value.any { it.sourceId == entity.sourceId })
        rows.value = sort(rows.value.map { row ->
            if (row.sourceId == entity.sourceId) entity else row
        })
    }

    override suspend fun delete(sourceId: String): Int {
        val before = rows.value.size
        rows.value = rows.value.filterNot { it.sourceId == sourceId }
        return before - rows.value.size
    }

    private companion object {
        fun sort(rows: List<SourceEntity>): List<SourceEntity> = rows.sortedWith(
            compareByDescending<SourceEntity> { it.updatedAt }
                .thenBy { it.createdAt }
                .thenBy { it.sourceId },
        )
    }
}

private class FakeRefreshStateDao(
    initial: List<RefreshStateEntity> = emptyList(),
) : RefreshStateDao {
    private val rows = MutableStateFlow(initial.sortedBy(RefreshStateEntity::sourceId))

    override fun observeAll(): Flow<List<RefreshStateEntity>> = rows

    override suspend fun get(sourceId: String): RefreshStateEntity? =
        rows.value.firstOrNull { it.sourceId == sourceId }

    override suspend fun upsert(entity: RefreshStateEntity) {
        rows.value = (rows.value.filterNot { it.sourceId == entity.sourceId } + entity)
            .sortedBy(RefreshStateEntity::sourceId)
    }
}

private class FakeActiveSourceStore(
    initial: String? = null,
) : ActiveSourceSelectionStore {
    private val selected = MutableStateFlow(initial)

    override val selectedSourceId: Flow<String?> = selected

    override suspend fun currentSelectedSourceId(): String? = selected.value

    override suspend fun setSelectedSourceId(sourceId: String?) {
        selected.value = sourceId
    }
}

private class FakeCredentialStore : CredentialStore {
    val secrets = linkedMapOf<SourceId, SourceSecret>()
    var putCalls: Int = 0

    override suspend fun put(sourceId: SourceId, secret: SourceSecret) {
        putCalls += 1
        secrets[sourceId] = secret
    }

    override suspend fun get(sourceId: SourceId): SourceSecret? = secrets[sourceId]

    override suspend fun delete(sourceId: SourceId) {
        secrets.remove(sourceId)
    }

    fun seed(sourceId: SourceId, secret: SourceSecret) {
        secrets[sourceId] = secret
    }
}
