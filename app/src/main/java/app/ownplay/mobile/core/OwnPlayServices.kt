package app.ownplay.mobile.core

import android.content.Context
import app.ownplay.mobile.data.db.OwnPlayDatabase
import app.ownplay.mobile.data.prefs.ActiveSourcePreferences
import app.ownplay.mobile.data.security.CredentialStore
import app.ownplay.mobile.data.security.KeystoreCredentialStore
import app.ownplay.mobile.feature.live.data.LiveRepositoryImpl
import app.ownplay.mobile.feature.live.domain.LiveRepository
import app.ownplay.mobile.playback.Media3PlaybackController
import app.ownplay.mobile.playback.PlaybackController
import app.ownplay.mobile.sources.data.ProviderHttpTransport
import app.ownplay.mobile.sources.data.SourceCatalogLoader
import app.ownplay.mobile.sources.data.SourceRepositoryImpl
import app.ownplay.mobile.sources.data.m3u.M3uParser
import app.ownplay.mobile.sources.data.m3u.OkHttpM3uClient
import app.ownplay.mobile.sources.data.xtream.OkHttpXtreamClient
import app.ownplay.mobile.sources.domain.SourceRepository
import java.util.concurrent.TimeUnit
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

    private val httpClient: OkHttpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val sourceRepository: SourceRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val transport = ProviderHttpTransport(httpClient)
        val catalogLoader = SourceCatalogLoader(
            xtreamClient = OkHttpXtreamClient(transport),
            m3uClient = OkHttpM3uClient(transport),
            m3uParser = M3uParser(),
        )
        SourceRepositoryImpl(
            database = database,
            sourceDao = database.sourceDao(),
            catalogDao = database.catalogDao(),
            activeSourcePreferences = ActiveSourcePreferences(applicationContext),
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
        )
    }

    companion object {
        fun create(context: Context): OwnPlayServices = OwnPlayServices(context)
    }
}
