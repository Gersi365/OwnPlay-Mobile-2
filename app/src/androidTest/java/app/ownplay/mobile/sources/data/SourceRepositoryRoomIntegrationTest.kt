package app.ownplay.mobile.sources.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.ownplay.mobile.data.db.CategoryPersonalizationEntity
import app.ownplay.mobile.data.db.ChannelPersonalizationEntity
import app.ownplay.mobile.data.db.CustomGroupEntity
import app.ownplay.mobile.data.db.CustomGroupMembershipEntity
import app.ownplay.mobile.data.db.DownloadEntity
import app.ownplay.mobile.data.db.OwnPlayDatabase
import app.ownplay.mobile.data.db.PlaybackProgressEntity
import app.ownplay.mobile.data.prefs.ActiveSourcePreferences
import app.ownplay.mobile.data.security.CredentialStore
import app.ownplay.mobile.sources.data.m3u.M3uClient
import app.ownplay.mobile.sources.data.m3u.M3uParser
import app.ownplay.mobile.sources.data.m3u.M3uResult
import app.ownplay.mobile.sources.data.xtream.XtreamAccountInfo
import app.ownplay.mobile.sources.data.xtream.XtreamCategory
import app.ownplay.mobile.sources.data.xtream.XtreamClient
import app.ownplay.mobile.sources.data.xtream.XtreamEpgEntry
import app.ownplay.mobile.sources.data.xtream.XtreamLiveStream
import app.ownplay.mobile.sources.data.xtream.XtreamMovie
import app.ownplay.mobile.sources.data.xtream.XtreamResult
import app.ownplay.mobile.sources.data.xtream.XtreamSeries
import app.ownplay.mobile.sources.data.xtream.XtreamSeriesInfo
import app.ownplay.mobile.sources.domain.NewSource
import app.ownplay.mobile.sources.domain.RefreshStatus
import app.ownplay.mobile.sources.domain.SourceCredential
import app.ownplay.mobile.sources.domain.SourceResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceRepositoryRoomIntegrationTest {
    private lateinit var context: Context
    private lateinit var database: OwnPlayDatabase
    private lateinit var activeSourcePreferences: ActiveSourcePreferences
    private lateinit var credentialStore: InMemoryCredentialStore
    private lateinit var xtreamClient: FakeXtreamClient
    private lateinit var repository: SourceRepositoryImpl
    private var clockMs: Long = 1_000L

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, OwnPlayDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        activeSourcePreferences = ActiveSourcePreferences(context)
        activeSourcePreferences.setSelectedSourceId(null)
        credentialStore = InMemoryCredentialStore()
        xtreamClient = FakeXtreamClient()
        repository = SourceRepositoryImpl(
            database = database,
            sourceDao = database.sourceDao(),
            catalogDao = database.catalogDao(),
            activeSourcePreferences = activeSourcePreferences,
            credentialStore = credentialStore,
            catalogLoader = SourceCatalogLoader(
                xtreamClient = xtreamClient,
                m3uClient = object : M3uClient {
                    override suspend fun fetch(remoteLocator: String): M3uResult<String> =
                        M3uResult.Failure("UNUSED")
                },
                m3uParser = M3uParser(),
            ),
            nowMillis = { clockMs },
            newSourceId = { SOURCE_ID },
        )
    }

    @After
    fun tearDown() = runBlocking {
        activeSourcePreferences.setSelectedSourceId(null)
        database.close()
    }

    @Test
    fun partialXtreamRefreshPreservesLastKnownGoodRowsAndLocalState() = runBlocking {
        xtreamClient.liveCategoriesResult = XtreamResult.Success(
            listOf(XtreamCategory(providerKey = CATEGORY_KEY, name = "News", providerOrder = 0)),
        )
        xtreamClient.liveStreamsResult = XtreamResult.Success(
            listOf(
                liveStream(streamId = "1", categoryId = CATEGORY_KEY, name = "One", order = 0),
                liveStream(streamId = "2", categoryId = CATEGORY_KEY, name = "Two", order = 1),
            ),
        )

        val added = repository.addSource(
            NewSource.Xtream(
                displayName = "Integration source",
                baseUrl = "https://provider.example",
                credential = SourceCredential.Xtream(username = "user", password = "password"),
            ),
        )
        assertTrue(added is SourceResult.Success)

        val initialRefresh = repository.refresh(SOURCE_ID)
        assertTrue(initialRefresh is SourceResult.Success)
        assertEquals(RefreshStatus.SUCCESS, (initialRefresh as SourceResult.Success).value.status)

        val categoryId = StableIdentity.categoryId(SOURCE_ID, "LIVE", CATEGORY_KEY)
        val channelOneId = StableIdentity.xtreamContentId(SOURCE_ID, "live", "1")
        val channelTwoId = StableIdentity.xtreamContentId(SOURCE_ID, "live", "2")
        assertNotNull(database.catalogDao().getLiveChannel(channelTwoId))

        val channelPersonalization = ChannelPersonalizationEntity(
            channelId = channelTwoId,
            favorite = true,
            hidden = true,
            localName = "Pinned Two",
            localLogo = "https://local.example/two.png",
            manualOrder = 7,
        )
        database.catalogDao().upsertChannelPersonalization(channelPersonalization)

        val categoryPersonalization = CategoryPersonalizationEntity(
            sourceId = SOURCE_ID,
            kind = "LIVE",
            categoryKey = categoryId,
            hidden = true,
            manualOrder = 4,
        )
        database.catalogDao().upsertCategoryPersonalization(categoryPersonalization)

        val group = CustomGroupEntity(
            groupId = "group-1",
            sourceId = SOURCE_ID,
            name = "Pinned",
            manualOrder = 2,
        )
        val membership = CustomGroupMembershipEntity(
            groupId = group.groupId,
            channelId = channelTwoId,
            manualOrder = 0,
        )
        database.backupDao().upsertCustomGroups(listOf(group))
        database.backupDao().upsertCustomGroupMembership(membership)

        val progress = PlaybackProgressEntity(
            sourceId = SOURCE_ID,
            mediaKind = "MOVIE",
            contentId = "movie-progress",
            positionMs = 1_500L,
            durationMs = 10_000L,
            completed = false,
            updatedAt = 1_500L,
        )
        database.libraryDao().upsertProgress(progress)

        val download = DownloadEntity(
            downloadId = "download-1",
            sourceId = SOURCE_ID,
            mediaKind = "MOVIE",
            contentId = "movie-progress",
            title = "Keep me",
            streamIdentity = "movie-progress",
            state = "PAUSED",
            bytesDownloaded = 512L,
            totalBytes = 1_024L,
            localReference = null,
            integrityMetadata = null,
            failureReason = null,
            createdAt = 1_000L,
            updatedAt = 1_500L,
        )
        database.downloadDao().insert(download)

        xtreamClient.liveCategoriesResult = XtreamResult.Failure("NETWORK_TIMEOUT")
        xtreamClient.liveStreamsResult = XtreamResult.Success(
            value = listOf(
                liveStream(streamId = "1", categoryId = null, name = "One updated", order = 0),
            ),
            warningCode = "XTREAM_PARTIAL_ROWS",
        )
        clockMs = 2_000L

        val partialRefresh = repository.refresh(SOURCE_ID)
        assertTrue(partialRefresh is SourceResult.Success)
        assertEquals(RefreshStatus.PARTIAL, (partialRefresh as SourceResult.Success).value.status)

        val updatedOne = database.catalogDao().getLiveChannel(channelOneId)
        assertNotNull(updatedOne)
        assertEquals("One updated", updatedOne?.name)
        assertEquals(categoryId, updatedOne?.categoryKey)

        val preservedTwo = database.catalogDao().getLiveChannel(channelTwoId)
        assertNotNull(preservedTwo)
        assertTrue(preservedTwo?.available == true)
        assertEquals(channelPersonalization, database.catalogDao().getChannelPersonalization(channelTwoId))
        assertEquals(
            categoryPersonalization,
            database.catalogDao().getCategoryPersonalization(SOURCE_ID, "LIVE", categoryId),
        )
        assertEquals(group, database.backupDao().getCustomGroup(group.groupId))
        assertEquals(listOf(membership), database.backupDao().getCustomGroupMembershipRows(group.groupId))
        assertEquals(progress, database.libraryDao().getProgress(SOURCE_ID, "MOVIE", "movie-progress"))
        assertEquals(download, database.downloadDao().get(download.downloadId))

        val refreshState = database.refreshStateDao().get(SOURCE_ID)
        assertEquals(2L, refreshState?.generation)
        assertEquals("PARTIAL", refreshState?.state)
        assertTrue(refreshState?.errorCode.orEmpty().contains("NETWORK_TIMEOUT"))
        assertTrue(refreshState?.errorCode.orEmpty().contains("XTREAM_PARTIAL_ROWS"))
    }

    private fun liveStream(
        streamId: String,
        categoryId: String?,
        name: String,
        order: Int,
    ): XtreamLiveStream = XtreamLiveStream(
        streamId = streamId,
        categoryId = categoryId,
        name = name,
        epgChannelId = null,
        streamIcon = null,
        providerOrder = order,
    )

    private class InMemoryCredentialStore : CredentialStore {
        private val values = linkedMapOf<String, SourceCredential>()

        override suspend fun put(sourceId: String, credential: SourceCredential) {
            values[sourceId] = credential
        }

        override suspend fun get(sourceId: String): SourceCredential? = values[sourceId]

        override suspend fun remove(sourceId: String) {
            values.remove(sourceId)
        }
    }

    private class FakeXtreamClient : XtreamClient {
        var liveCategoriesResult: XtreamResult<List<XtreamCategory>> = XtreamResult.Success(emptyList())
        var liveStreamsResult: XtreamResult<List<XtreamLiveStream>> = XtreamResult.Success(emptyList())
        var vodCategoriesResult: XtreamResult<List<XtreamCategory>> = XtreamResult.Success(emptyList())
        var vodStreamsResult: XtreamResult<List<XtreamMovie>> = XtreamResult.Success(emptyList())
        var seriesCategoriesResult: XtreamResult<List<XtreamCategory>> = XtreamResult.Success(emptyList())
        var seriesResult: XtreamResult<List<XtreamSeries>> = XtreamResult.Success(emptyList())

        override suspend fun authenticate(
            baseUrl: String,
            credential: SourceCredential.Xtream,
        ): XtreamResult<XtreamAccountInfo> = XtreamResult.Success(
            XtreamAccountInfo(authenticated = true, status = "Active", expirationEpochSeconds = null),
        )

        override suspend fun liveCategories(
            baseUrl: String,
            credential: SourceCredential.Xtream,
        ): XtreamResult<List<XtreamCategory>> = liveCategoriesResult

        override suspend fun liveStreams(
            baseUrl: String,
            credential: SourceCredential.Xtream,
            categoryId: String?,
        ): XtreamResult<List<XtreamLiveStream>> = liveStreamsResult

        override suspend fun vodCategories(
            baseUrl: String,
            credential: SourceCredential.Xtream,
        ): XtreamResult<List<XtreamCategory>> = vodCategoriesResult

        override suspend fun vodStreams(
            baseUrl: String,
            credential: SourceCredential.Xtream,
        ): XtreamResult<List<XtreamMovie>> = vodStreamsResult

        override suspend fun seriesCategories(
            baseUrl: String,
            credential: SourceCredential.Xtream,
        ): XtreamResult<List<XtreamCategory>> = seriesCategoriesResult

        override suspend fun series(
            baseUrl: String,
            credential: SourceCredential.Xtream,
        ): XtreamResult<List<XtreamSeries>> = seriesResult

        override suspend fun seriesInfo(
            baseUrl: String,
            credential: SourceCredential.Xtream,
            seriesId: String,
        ): XtreamResult<XtreamSeriesInfo> = XtreamResult.Failure("UNUSED")

        override suspend fun shortEpg(
            baseUrl: String,
            credential: SourceCredential.Xtream,
            streamId: String,
            limit: Int,
        ): XtreamResult<List<XtreamEpgEntry>> = XtreamResult.Success(emptyList())
    }

    private companion object {
        const val SOURCE_ID = "source-integration"
        const val CATEGORY_KEY = "10"
    }
}
