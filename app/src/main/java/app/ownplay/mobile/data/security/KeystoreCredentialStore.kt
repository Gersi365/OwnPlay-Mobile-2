package app.ownplay.mobile.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.ownplay.mobile.sources.domain.SourceCredential
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KeystoreCredentialStore(
    context: Context,
) : CredentialStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    override suspend fun put(sourceId: String, credential: SourceCredential) = withContext(Dispatchers.IO) {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            cipher.updateAAD(sourceId.toByteArray(Charsets.UTF_8))
            val encrypted = cipher.doFinal(encodeCredential(credential))
            val serialized = listOf(
                FORMAT_VERSION,
                Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
                Base64.encodeToString(encrypted, Base64.NO_WRAP),
            ).joinToString(":")
            check(preferences.edit().putString(sourceId, serialized).commit())
        } catch (_: Exception) {
            throw CredentialStoreException()
        }
    }

    override suspend fun get(sourceId: String): SourceCredential? = withContext(Dispatchers.IO) {
        val serialized = preferences.getString(sourceId, null) ?: return@withContext null
        try {
            val parts = serialized.split(':', limit = 3)
            if (parts.size != 3 || parts[0] != FORMAT_VERSION) throw CredentialStoreException()
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            val encrypted = Base64.decode(parts[2], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(sourceId.toByteArray(Charsets.UTF_8))
            decodeCredential(cipher.doFinal(encrypted))
        } catch (_: CredentialStoreException) {
            throw CredentialStoreException()
        } catch (_: Exception) {
            throw CredentialStoreException()
        }
    }

    override suspend fun remove(sourceId: String) = withContext(Dispatchers.IO) {
        try {
            check(preferences.edit().remove(sourceId).commit())
        } catch (_: Exception) {
            throw CredentialStoreException()
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    private fun encodeCredential(credential: SourceCredential): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { output ->
            when (credential) {
                is SourceCredential.Xtream -> {
                    output.writeByte(TYPE_XTREAM)
                    output.writeUtf8(credential.username)
                    output.writeUtf8(credential.password)
                }
                is SourceCredential.M3uRemoteLocator -> {
                    output.writeByte(TYPE_M3U)
                    output.writeUtf8(credential.locator)
                }
            }
        }
        return buffer.toByteArray()
    }

    private fun decodeCredential(bytes: ByteArray): SourceCredential {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            return when (input.readUnsignedByte()) {
                TYPE_XTREAM -> SourceCredential.Xtream(
                    username = input.readUtf8(),
                    password = input.readUtf8(),
                )
                TYPE_M3U -> SourceCredential.M3uRemoteLocator(input.readUtf8())
                else -> throw CredentialStoreException()
            }
        }
    }

    private fun DataOutputStream.writeUtf8(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readUtf8(): String {
        val length = readInt()
        if (length < 0 || length > MAX_SECRET_BYTES) throw CredentialStoreException()
        val bytes = ByteArray(length)
        readFully(bytes)
        return bytes.toString(Charsets.UTF_8)
    }

    private companion object {
        const val PREFS_NAME = "ownplay_secure_credentials_v1"
        const val KEY_ALIAS = "ownplay.credentials.v1"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION = "1"
        const val TAG_BITS = 128
        const val TYPE_XTREAM = 1
        const val TYPE_M3U = 2
        const val MAX_SECRET_BYTES = 1024 * 1024
    }
}
