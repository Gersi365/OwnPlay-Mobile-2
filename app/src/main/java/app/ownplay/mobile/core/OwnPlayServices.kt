package app.ownplay.mobile.core

import android.content.Context
import app.ownplay.mobile.data.db.OwnPlayDatabase
import app.ownplay.mobile.data.prefs.ActiveSourcePreferences
import app.ownplay.mobile.data.security.CredentialStore
import app.ownplay.mobile.data.security.KeystoreCredentialStore
import app.ownplay.mobile.downloads.data.AndroidDownloadStorage
import app.ownplay.mobile.downloads.data.DownloadExecutor
import app.ownplay.mobile.downloads.data.OkHttpDownloadTransferClient
import app.ownplay.mobile.downloads.data.RoomDownloadRepository
import app.ownplay.mobile.downloads.data.SourceBackedDownloadMediaResolver
import app.ownplay.mobile.downloads.data.WorkManagedDownloadRepository
import app.ownplay.mobile.downloads.data.WorkManagerDownloadScheduler
import app.ownplay.mobile.downloads.domain.DownloadRepository
import app.ownplay.mobile.feature.library.data.LibraryArtworkLoader
import app.ownplay.mobile.feature.library.data.LibraryPlaybackLocator
import app.ownplay.mobile.feature.library.data.OkHttpLibraryArtworkLoader
import app.ownplay.mobile.feature.library.data.RoomLibraryPlaybackProgressStore
import app.ownplay.mobile.feature.library.data.RoomLibraryRepository
import app.ownplay.mobile.feature.library.data.SourceBackedLibraryMovieDetailLoader
import app.ownplay.mobile.feature.library.data.SourceBackedLibraryPlaybackLocator
import app.ownplay.mobile.feature.library.data.SourceBackedLibrarySeriesDetailRefresher
import app.ownplay.mobile.feature.library.domain.LibraryRepository
import app.ownplay.mobile.feature.live.data.RoomLiveOrganizationRefreshStore
import app.ownplay.mobile.feature.live.data.RoomLiveOrganizationRepository
import app.ownplay.mobile.feature.live.domain.LiveOrganizationRepository
import app.ownplay.mobile.feature.playback.data.DefaultLivePlaybackMediaPreparer
import app.ownplay.mobile.feature.playback.data.Media3PlaybackEngine
import app.ownplay.mobile.feature.playback.data.Media3PlaybackEngineAdapter
import app.ownplay.mobile.feature.playback.data.SourceBackedLivePlaybackSourceResolver
import app.ownplay.mobile.feature.playback.domain.PlaybackSessionController
import app.ownplay.mobile.sources.data.DefaultSourceCatalogLoader
import app.ownplay.mobile.sources.data.OkHttpProviderTransport
import app.ownplay.mobile.sources.data.ProviderTransport
import app.ownplay.mobile.sources.data.RoomCatalogRefreshStore
import app.ownplay.mobile.sources.data.SourceRepositoryImpl
import app.ownplay.mobile.sources.data.m3u.M3uClient
import app.ownplay.mobile.sources.data.m3u.OkHttpM3uClient
import app.ownplay.mobile.sources.data.xtream.OkHttpXtreamClient
import app.ownplay.mobile.sources.data.xtream.XtreamClient
import app.ownplay.mobile.sources.domain.SourceRepository

class OwnPlayServices private constructor(
    val database: OwnPlayDatabase,
    val activeSourcePreferences: ActiveSourcePreferences,
    val credentialStore: CredentialStore,
    val sourceRepository: SourceRepository,
    val liveOrganizationRepository: LiveOrganizationRepository,
    val libraryRepository: LibraryRepository,
    val downloadRepository: DownloadRepository,
    internal val downloadExecutor: DownloadExecutor,
    internal val libraryPlaybackLocator: LibraryPlaybackLocator,
    internal val libraryArtworkLoader: LibraryArtworkLoader,
    val playbackSessionController: PlaybackSessionController,
    val playbackEngine: Media3PlaybackEngine,
    val providerTransport: ProviderTransport,
    val m3uClient: M3uClient,
    val xtreamClient: XtreamClient,
) {
    internal fun releasePlayback() {
        playbackSessionController.release()
    }

    companion object {
        fun create(context: Context): OwnPlayServices {
            val applicationContext = context.applicationContext
            val database = OwnPlayDatabase.create(applicationContext)
            val activeSourcePreferences = ActiveSourcePreferences(applicationContext)
            val credentialStore = KeystoreCredentialStore(applicationContext)
            val transport = OkHttpProviderTransport()
            val m3uClient = OkHttpM3uClient(transport)
            val xtreamClient = OkHttpXtreamClient(transport)
            val sourceDao = database.sourceDao()
            val refreshStateDao = database.refreshStateDao()
            val liveOrganizationDao = database.liveOrganizationDao()
            val libraryDao = database.libraryDao()
            val catalogLoader = DefaultSourceCatalogLoader(
                xtreamClient = xtreamClient,
                m3uClient = m3uClient,
            )
            val liveOrganizationRefreshStore = RoomLiveOrganizationRefreshStore(liveOrganizationDao)
            val catalogRefreshStore = RoomCatalogRefreshStore(
                database = database,
                refreshStateDao = refreshStateDao,
                liveOrganizationRefreshStore = liveOrganizationRefreshStore,
            )
            val liveOrganizationRepository = RoomLiveOrganizationRepository(
                database = database,
                dao = liveOrganizationDao,
            )
            val libraryDetailRefresher = SourceBackedLibrarySeriesDetailRefresher(
                sourceDao = sourceDao,
                libraryDao = libraryDao,
                credentialStore = credentialStore,
                xtreamClient = xtreamClient,
            )
            val libraryMovieDetailLoader = SourceBackedLibraryMovieDetailLoader(
                sourceDao = sourceDao,
                libraryDao = libraryDao,
                credentialStore = credentialStore,
                xtreamClient = xtreamClient,
            )
            val libraryRepository = RoomLibraryRepository(
                dao = libraryDao,
                detailRefresher = libraryDetailRefresher,
                movieDetailLoader = libraryMovieDetailLoader,
            )
            val libraryPlaybackLocator = SourceBackedLibraryPlaybackLocator(
                sourceDao = sourceDao,
                libraryDao = libraryDao,
                credentialStore = credentialStore,
            )
            val downloadStorage = AndroidDownloadStorage(applicationContext)
            val downloadRepository = WorkManagedDownloadRepository(
                delegate = RoomDownloadRepository(database.downloadDao()),
                scheduler = WorkManagerDownloadScheduler(applicationContext),
                storage = downloadStorage,
            )
            val downloadExecutor = DownloadExecutor(
                repository = downloadRepository,
                mediaResolver = SourceBackedDownloadMediaResolver(
                    libraryDao = libraryDao,
                    libraryPlaybackLocator = libraryPlaybackLocator,
                ),
                storage = downloadStorage,
                transferClient = OkHttpDownloadTransferClient(),
            )
            val libraryArtworkLoader = OkHttpLibraryArtworkLoader()
            val libraryPlaybackProgressStore = RoomLibraryPlaybackProgressStore(libraryDao)
            val playbackSourceResolver = SourceBackedLivePlaybackSourceResolver(
                sourceDao = sourceDao,
                liveOrganizationDao = liveOrganizationDao,
                credentialStore = credentialStore,
            )
            val playbackEngine = Media3PlaybackEngine(applicationContext)
            val playbackEngineAdapter = Media3PlaybackEngineAdapter(playbackEngine)
            val playbackSessionController = PlaybackSessionController(
                sourceResolver = playbackSourceResolver,
                mediaPreparer = DefaultLivePlaybackMediaPreparer(),
                playbackEngine = playbackEngineAdapter,
                libraryMediaResolver = libraryPlaybackLocator,
                playbackProgressEngine = playbackEngineAdapter,
                libraryProgressStore = libraryPlaybackProgressStore,
            )

            return OwnPlayServices(
                database = database,
                activeSourcePreferences = activeSourcePreferences,
                credentialStore = credentialStore,
                sourceRepository = SourceRepositoryImpl(
                    sourceDao = sourceDao,
                    refreshStateDao = refreshStateDao,
                    activeSourceStore = activeSourcePreferences,
                    credentialStore = credentialStore,
                    catalogLoader = catalogLoader,
                    catalogRefreshStore = catalogRefreshStore,
                ),
                liveOrganizationRepository = liveOrganizationRepository,
                libraryRepository = libraryRepository,
                downloadRepository = downloadRepository,
                downloadExecutor = downloadExecutor,
                libraryPlaybackLocator = libraryPlaybackLocator,
                libraryArtworkLoader = libraryArtworkLoader,
                playbackSessionController = playbackSessionController,
                playbackEngine = playbackEngine,
                providerTransport = transport,
                m3uClient = m3uClient,
                xtreamClient = xtreamClient,
            )
        }
    }
}
