package app.ownplay.mobile.core

import android.content.Context
import app.ownplay.mobile.data.db.OwnPlayDatabase
import app.ownplay.mobile.data.prefs.ActiveSourcePreferences
import app.ownplay.mobile.data.security.CredentialStore
import app.ownplay.mobile.data.security.KeystoreCredentialStore
import app.ownplay.mobile.sources.data.OkHttpProviderTransport
import app.ownplay.mobile.sources.data.ProviderTransport
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
            return OwnPlayServices(
                database = database,
                activeSourcePreferences = activeSourcePreferences,
                credentialStore = credentialStore,
                sourceRepository = SourceRepositoryImpl(
                    sourceDao = database.sourceDao(),
                    refreshStateDao = database.refreshStateDao(),
                    activeSourceStore = activeSourcePreferences,
                    credentialStore = credentialStore,
                ),
                providerTransport = transport,
                m3uClient = OkHttpM3uClient(transport),
                xtreamClient = OkHttpXtreamClient(transport),
            )
        }
    }
}
