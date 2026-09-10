package app.ownplay.mobile.data.security

import app.ownplay.mobile.sources.domain.SourceCredential

internal object CredentialInputPolicy {
    const val MAX_FIELD_BYTES = 16 * 1024

    fun requireSupportedSize(credential: SourceCredential) {
        when (credential) {
            is SourceCredential.Xtream -> {
                requireFieldSize(credential.username)
                requireFieldSize(credential.password)
            }

            is SourceCredential.M3uRemoteLocator -> requireFieldSize(credential.locator)
        }
    }

    private fun requireFieldSize(value: String) {
        require(value.toByteArray(Charsets.UTF_8).size <= MAX_FIELD_BYTES) {
            "Credential field exceeds the supported size."
        }
    }
}
