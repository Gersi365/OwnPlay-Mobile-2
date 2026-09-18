package app.ownplay.mobile

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.ownplay.mobile.data.db.LiveChannelEntity
import app.ownplay.mobile.data.db.OwnPlayDatabase
import app.ownplay.mobile.data.db.ProviderCategoryEntity
import app.ownplay.mobile.data.db.SourceEntity
import app.ownplay.mobile.feature.live.data.RoomLiveOrganizationRefreshStore
import app.ownplay.mobile.feature.live.data.RoomLiveOrganizationRepository
import app.ownplay.mobile.feature.live.domain.OwnPlayLiveCatalogSnapshot
import app.ownplay.mobile.feature.live.domain.OwnPlayLivePlacement
import app.ownplay.mobile.feature.live.domain.OwnPlayLiveSemanticCategory
import app.ownplay.mobile.sources.domain.SourceId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OwnPlayManualPlacementInstrumentedTest {
    private lateinit var database: OwnPlayDatabase
    private lateinit var repository: RoomLiveOrganizationRepository
    private lateinit var refreshStore: RoomLiveOrganizationRefreshStore
    private val sourceId = SourceId("manual-placement-qa")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OwnPlayDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomLiveOrganizationRepository(database, database.liveOrganizationDao())
        refreshStore = RoomLiveOrganizationRefreshStore(database.liveOrganizationDao())
        runBlocking {
            database.sourceDao().insert(
                SourceEntity(
                    sourceId = sourceId.value,
                    displayName = "QA",
                    type = "M3U",
                    baseLocator = "https://example.invalid/list.m3u",
                    credentialReference = sourceId.value,
                    enabled = true,
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun manualMoveSurvivesAutomaticReclassificationAndResetRevealsLatestAutomaticPlacement() = runBlocking {
        val initialCategory = category("al-live", "AL News", generation = 1L)
        val initialChannel = channel("channel-1", "al-live", "Top News", generation = 1L)
        persistProviderGeneration(initialCategory, initialChannel)
        refreshStore.reconcileAutomatic(
            sourceId = sourceId,
            generation = 1L,
            providerCategories = listOf(initialCategory),
            liveChannels = listOf(initialChannel),
        )

        assertEquals(
            OwnPlayLivePlacement("country:AL", OwnPlayLiveSemanticCategory.NEWS),
            repository.observeOwnPlayCatalog(sourceId).first().placementOf("channel-1"),
        )

        val manual = OwnPlayLivePlacement("country:AL", OwnPlayLiveSemanticCategory.SPORTS)
        assertTrue(repository.moveChannel(sourceId, "channel-1", manual))
        val moved = repository.observeOwnPlayCatalog(sourceId).first()
        assertEquals(manual, moved.placementOf("channel-1"))
        assertTrue("channel-1" in moved.manualPlacementChannelIds)

        val refreshedCategory = category("al-live", "AL Movies", generation = 2L)
        val refreshedChannel = channel("channel-1", "al-live", "Cinema One", generation = 2L)
        persistProviderGeneration(refreshedCategory, refreshedChannel)
        refreshStore.reconcileAutomatic(
            sourceId = sourceId,
            generation = 2L,
            providerCategories = listOf(refreshedCategory),
            liveChannels = listOf(refreshedChannel),
        )

        val afterRefresh = repository.observeOwnPlayCatalog(sourceId).first()
        assertEquals(manual, afterRefresh.placementOf("channel-1"))
        assertTrue("channel-1" in afterRefresh.manualPlacementChannelIds)

        assertTrue(repository.resetChannelToAutomatic(sourceId, "channel-1"))
        val reset = repository.observeOwnPlayCatalog(sourceId).first()
        assertEquals(
            OwnPlayLivePlacement("country:AL", OwnPlayLiveSemanticCategory.MOVIES),
            reset.placementOf("channel-1"),
        )
        assertFalse("channel-1" in reset.manualPlacementChannelIds)
    }

    private suspend fun persistProviderGeneration(
        category: ProviderCategoryEntity,
        channel: LiveChannelEntity,
    ) {
        database.refreshStateDao().upsertCategories(listOf(category))
        database.refreshStateDao().upsertLiveChannels(listOf(channel))
    }

    private fun category(
        key: String,
        name: String,
        generation: Long,
    ) = ProviderCategoryEntity(
        sourceId = sourceId.value,
        kind = "LIVE",
        categoryKey = key,
        providerKey = key,
        name = name,
        providerOrder = 0,
        available = true,
        lastSeenGeneration = generation,
    )

    private fun channel(
        id: String,
        categoryKey: String,
        name: String,
        generation: Long,
    ) = LiveChannelEntity(
        channelId = id,
        sourceId = sourceId.value,
        providerKey = id,
        providerStreamId = id,
        categoryKey = categoryKey,
        name = name,
        tvgId = id,
        tvgName = name,
        logoUrl = null,
        streamLocator = "https://example.invalid/$id.ts",
        providerOrder = 0,
        available = true,
        lastSeenGeneration = generation,
    )

    private fun OwnPlayLiveCatalogSnapshot.placementOf(channelId: String): OwnPlayLivePlacement? =
        channelIdsByPlacement.entries.firstOrNull { (_, ids) -> channelId in ids }?.key
}
