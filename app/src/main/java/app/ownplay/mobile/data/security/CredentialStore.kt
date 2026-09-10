package app.ownplay.mobile.data.security

import app.ownplay.mobile.sources.domain.SourceCredential

interface CredentialStore {
    suspend fun put(sourceId: String, credential: SourceCredential)
    suspend fun get(sourceId: String): SourceCredential?
    suspend fun remove(sourceId: String)
}

class CredentialStoreException : Exception("Secure credential operation failed.")
