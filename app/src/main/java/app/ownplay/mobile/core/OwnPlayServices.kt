package app.ownplay.mobile.core

import android.content.Context
import androidx.work.WorkManager
import app.ownplay.mobile.data.db.OwnPlayDatabase
import app.ownplay.mobile.data.prefs.ActiveSourcePreferences
import app.ownplay.mobile.data.prefs.SettingsPreferences
import app.ownplay.mobile.data.security.CredentialStore
import app.ownplay.mobile.data.security.KeystoreCredentialStore
import app.ownplay.mobile.downloads.data.DownloadRepositoryImpl
import app.ownplay.mobile.downloads.data.DownloadStreamResolver
import app.ownplay.mobile.downloads.domain.DownloadRepository
import app.ownplay.mobile.feature.library.data.LibraryRepositoryImpl
import app.ownplay.mobile.feature.library.domain.LibraryRepository
import app.ownplay.mobile.feature.live.data.LiveRepositoryImpl
import app.ownplay.mobile.feature.live.domain.LiveRepository
import app.ownplay.mobile.feature.settings.data.BackupRepositoryImpl
import app.ownplay.mobile.feature.settings.data.ProviderRefreshScheduler
import app.ownplay.mobile.feature.settings.domain.BackupRepository
import app.ownplay.mobile.playback.Media3PlaybackController
import app.ownplay.mobile.playback.PlaybackController
import app.ownplay.mobile.sources.data.ProviderHttpTransport
import app.ownplay.mobile.sources.data.SourceCatalogLoader
import app.ownplay.mobile.sources.data.SourceRepositoryImpl
import app.ownplay.mobile.sources.data.m3u.M3uParser
import app.ownplay.mobile.sources.data.m3u.OkHttpM3uClient
import app.ownplay.mobile.sources.data.xtream.OkHttpXtreamClient
import app.ownplay.mobile.sources.data.xtream.XtreamClient
import app.ownplay.mobile.sources.domain.SourceRepository
import java.util.concurrent.TimeUnit
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

class OwnPlayServices private constructor(
    context: Context,
) {
    private val applicationContext = context.applicationContext

    val database: OwnPlayDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        OwnPlayDatabase.create(applicationContext)
    }

    val credentialStore: CredentialStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        KeystoreCredentialStore(applicationContext)
    }

    val playbackController: PlaybackController by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Media3PlaybackController(applicationContext)
    }

    private val activeSourcePreferences: ActiveSourcePreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ActiveSourcePreferences(applicationContext)
    }

    val settingsPreferences: SettingsPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SettingsPreferences(applicationContext)
    }

    val backupRepository: BackupRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        BackupRepositoryImpl(
            context = applicationContext,
            database = database,
            sourceDao = database.sourceDao(),
            backupDao = database.backupDao(),
            activeSourcePreferences = activeSourcePreferences,
            settingsPreferences = settingsPreferences,
        )
    }

    val providerRefreshScheduler: ProviderRefreshScheduler by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ProviderRefreshScheduler(applicationContext)
    }

    private val providerHttpDispatcher: Dispatcher by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Dispatcher().apply {
            maxRequests = MAX_PROVIDER_REQUESTS
            maxRequestsPerHost = MAX_PROVIDER_REQUESTS_PER_HOST
        }
    }

    private val downloadHttpDispatcher: Dispatcher by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Dispatcher().apply {
            maxRequests = MAX_DOWNLOAD_REQUESTS
            maxRequestsPerHost = MAX_DOWNLOAD_REQUESTS_PER_HOST
        }
    }

    private val httpClient: OkHttpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        OkHttpClient.Builder()
            .dispatcher(providerHttpDispatcher)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val downloadHttpClient: OkHttpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        httpClient.newBuilder()
            .dispatcher(downloadHttpDispatcher)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    private val providerTransport: ProviderHttpTransport by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ProviderHttpTransport(httpClient)
    }

    private val xtreamClient: XtreamClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        OkHttpXtreamClient(providerTransport)
    }

    val sourceRepository: SourceRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val catalogLoader = SourceCatalogLoader(
            xtreamClient = xtreamClient,
            m3uClient = OkHttpM3uClient(providerTransport),
            m3uParser = M3uParser(),
        )
        SourceRepositoryImpl(
            database = database,
            sourceDao = database.sourceDao(),
            catalogDao = database.catalogDao(),
            activeSourcePreferences = activeSourcePreferences,
            credentialStore = credentialStore,
            catalogLoader = catalogLoader,
        )
    }

    val liveRepository: LiveRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LiveRepositoryImpl(
            sourceRepository = sourceRepository,
            sourceDao = database.sourceDao(),
            catalogDao = database.catalogDao(),
            credentialStore = credentialStore,
            xtreamClient = xtreamClient,
        )
    }

    val libraryRepository: LibraryRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LibraryRepositoryImpl(
            database = database,
            sourceRepository = sourceRepository,
            sourceDao = database.sourceDao(),
            catalogDao = database.catalogDao(),
            libraryDao = database.libraryDao(),
            credentialStore = credentialStore,
            xtreamClient = xtreamClient,
        )
    }

    val downloadRepository: DownloadRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DownloadRepositoryImpl(
            context = applicationContext,
            sourceRepository = sourceRepository,
            downloadDao = database.downloadDao(),
            libraryDao = database.libraryDao(),
            streamResolver = DownloadStreamResolver(
                sourceDao = database.sourceDao(),
                libraryDao = database.libraryDao(),
                credentialStore = credentialStore,
            ),
            workManager = WorkManager.getInstance(applicationContext),
            httpClient = downloadHttpClient,
        )
    }

    companion object {
        private const val MAX_PROVIDER_REQUESTS = 12
        private const val MAX_PROVIDER_REQUESTS_PER_HOST = 4
        private const val MAX_DOWNLOAD_REQUESTS = 4
        private const val MAX_DOWNLOAD_REQUESTS_PER_HOST = 2

        fun create(context: Context): OwnPlayServices = OwnPlayServices(context)
    }
}
