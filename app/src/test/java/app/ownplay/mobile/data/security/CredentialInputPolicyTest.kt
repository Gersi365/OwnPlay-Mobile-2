package app.ownplay.mobile.data.security

import app.ownplay.mobile.sources.domain.SourceCredential
import org.junit.Assert.assertThrows
import org.junit.Test

class CredentialInputPolicyTest {
    @Test
    fun `xtream credentials at the byte limit are accepted`() {
        CredentialInputPolicy.requireSupportedSize(
            SourceCredential.Xtream(
                username = "u".repeat(CredentialInputPolicy.MAX_FIELD_BYTES),
                password = "p".repeat(CredentialInputPolicy.MAX_FIELD_BYTES),
            ),
        )
    }

    @Test
    fun `oversized xtream credential is rejected before storage`() {
        assertThrows(IllegalArgumentException::class.java) {
            CredentialInputPolicy.requireSupportedSize(
                SourceCredential.Xtream(
                    username = "user",
                    password = "p".repeat(CredentialInputPolicy.MAX_FIELD_BYTES + 1),
                ),
            )
        }
    }

    @Test
    fun `utf8 byte size is enforced for m3u locator credentials`() {
        val multibyte = "é".repeat(CredentialInputPolicy.MAX_FIELD_BYTES / 2 + 1)

        assertThrows(IllegalArgumentException::class.java) {
            CredentialInputPolicy.requireSupportedSize(SourceCredential.M3uRemoteLocator(multibyte))
        }
    }
}
