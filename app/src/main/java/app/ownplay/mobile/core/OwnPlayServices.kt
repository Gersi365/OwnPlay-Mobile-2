package app.ownplay.mobile.core

import android.content.Context
import app.ownplay.mobile.data.db.OwnPlayDatabase
import app.ownplay.mobile.data.prefs.ActiveSourcePreferences
import app.ownplay.mobile.data.security.CredentialStore
import app.ownplay.mobile.data.security.KeystoreCredentialStore
import app.ownplay.mobile.feature.live.data.RoomLiveOrganizationRefreshStore
import app.ownplay.mobile.feature.live.data.RoomLiveOrganizationRepository
import app.ownplay.mobile.feature.live.domain.LiveOrganizationRepository
import app.ownplay.mobile.feature.playback.data.DefaultLivePlaybackMediaPreparer
import app.ownplay.mobile.feature.playback.data.Media3PlaybackEngine
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
    val playbackSessionController: PlaybackSessionController,
    val playbackEngine: Media3PlaybackEngine,
    val providerTransport: ProviderTransport,
    val m3uClient: M3uClient,
    val xtreamClient: XtreamClient,
) {
    companion object {
        fun create(context: Context): OwnPlayServices {
            val applicationContext = context.applicationContext
            val database = OwnPlayDatabase.create(applicationContext)
            val activeSourcePreferences = ActiveSourcePreferences(applicationContext)
            val credentialStore = KeystoreCredentialStore(applicationContext)
            val transport = OkHttpProviderTransport()
            val m3uClient = OkHttpM3uClient(transport)
            val xtreamClient = OkHttpXtreamClient(transport)
            val refreshStateDao = database.refreshStateDao()
            val liveOrganizationDao = database.liveOrganizationDao()
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
            val playbackSourceResolver = SourceBackedLivePlaybackSourceResolver(
                sourceDao = database.sourceDao(),
                liveOrganizationDao = liveOrganizationDao,
                credentialStore = credentialStore,
            )
            val playbackEngine = Media3PlaybackEngine(applicationContext)
            val playbackSessionController = PlaybackSessionController(
                sourceResolver = playbackSourceResolver,
                mediaPreparer = DefaultLivePlaybackMediaPreparer(),
                playbackEngine = playbackEngine,
            )

            return OwnPlayServices(
                database = database,
                activeSourcePreferences = activeSourcePreferences,
                credentialStore = credentialStore,
                sourceRepository = SourceRepositoryImpl(
                    sourceDao = database.sourceDao(),
                    refreshStateDao = refreshStateDao,
                    activeSourceStore = activeSourcePreferences,
                    credentialStore = credentialStore,
                    catalogLoader = catalogLoader,
                    catalogRefreshStore = catalogRefreshStore,
                ),
                liveOrganizationRepository = liveOrganizationRepository,
                playbackSessionController = playbackSessionController,
                playbackEngine = playbackEngine,
                providerTransport = transport,
                m3uClient = m3uClient,
                xtreamClient = xtreamClient,
            )
        }
    }
}
