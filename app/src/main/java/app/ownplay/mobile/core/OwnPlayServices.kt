package app.ownplay.mobile.core

import android.content.Context
import app.ownplay.mobile.data.security.CredentialStore
import app.ownplay.mobile.data.security.KeystoreCredentialStore

class OwnPlayServices private constructor(
    val credentialStore: CredentialStore,
) {
    companion object {
        fun create(context: Context): OwnPlayServices =
            OwnPlayServices(
                credentialStore = KeystoreCredentialStore(context.applicationContext),
            )
    }
}
