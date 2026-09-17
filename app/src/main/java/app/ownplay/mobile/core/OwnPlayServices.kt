package app.ownplay.mobile.core

import android.content.Context
import app.ownplay.mobile.data.db.OwnPlayDatabase
import app.ownplay.mobile.data.prefs.ActiveSourcePreferences
import app.ownplay.mobile.data.security.CredentialStore
import app.ownplay.mobile.data.security.KeystoreCredentialStore
import app.ownplay.mobile.sources.data.OkHttpProviderTransport
import app.ownplay.mobile.sources.data.ProviderTransport
import app.ownplay.mobile.sources.data.m3u.M3uClient
import app.ownplay.mobile.sources.data.m3u.OkHttpM3uClient
import app.ownplay.mobile.sources.data.xtream.OkHttpXtreamClient
import app.ownplay.mobile.sources.data.xtream.XtreamClient

class OwnPlayServices private constructor(
    val database: OwnPlayDatabase,
    val activeSourcePreferences: ActiveSourcePreferences,
    val credentialStore: CredentialStore,
    val providerTransport: ProviderTransport,
    val m3uClient: M3uClient,
    val xtreamClient: XtreamClient,
) {
    companion object {
        fun create(context: Context): OwnPlayServices {
            val applicationContext = context.applicationContext
            val transport = OkHttpProviderTransport()
            return OwnPlayServices(
                database = OwnPlayDatabase.create(applicationContext),
                activeSourcePreferences = ActiveSourcePreferences(applicationContext),
                credentialStore = KeystoreCredentialStore(applicationContext),
                providerTransport = transport,
                m3uClient = OkHttpM3uClient(transport),
                xtreamClient = OkHttpXtreamClient(transport),
            )
        }
    }
}
