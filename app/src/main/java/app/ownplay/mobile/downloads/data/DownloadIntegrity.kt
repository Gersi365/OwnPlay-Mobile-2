package app.ownplay.mobile.downloads.data

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

internal data class DownloadIntegrityMetadata(
    val sha256: String,
    val bytes: Long,
) {
    fun encode(): String = "sha256=$sha256;bytes=$bytes"
}

internal object DownloadIntegrity {
    fun metadataFor(file: File): DownloadIntegrityMetadata = FileInputStream(file).use { input ->
        metadataFor(input)
    }

    fun metadataFor(input: InputStream): DownloadIntegrityMetadata {
        val digest = MessageDigest.getInstance("SHA-256")
        var bytes = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            digest.update(buffer, 0, count)
            bytes += count
        }
        return DownloadIntegrityMetadata(
            sha256 = digest.digest().toHexString(),
            bytes = bytes,
        )
    }

    fun verify(file: File, encoded: String): Boolean {
        if (!file.isFile) return false
        val metadata = parse(encoded) ?: return false
        if (metadata.bytes < 0L || file.length() != metadata.bytes) return false
        return FileInputStream(file).use { input -> verify(input, metadata) }
    }

    fun verify(input: InputStream, encoded: String): Boolean {
        val metadata = parse(encoded) ?: return false
        return verify(input, metadata)
    }

    fun parse(encoded: String): DownloadIntegrityMetadata? {
        val values = encoded.split(';')
            .mapNotNull { part ->
                val key = part.substringBefore('=', missingDelimiterValue = "").trim()
                val value = part.substringAfter('=', missingDelimiterValue = "").trim()
                if (key.isBlank() || value.isBlank()) null else key to value
            }
            .toMap()
        val sha = values["sha256"]?.lowercase()?.takeIf { value ->
            value.length == 64 && value.all { character -> character in '0'..'9' || character in 'a'..'f' }
        } ?: return null
        val bytes = values["bytes"]?.toLongOrNull()?.takeIf { it >= 0L } ?: return null
        return DownloadIntegrityMetadata(sha256 = sha, bytes = bytes)
    }

    private fun verify(input: InputStream, metadata: DownloadIntegrityMetadata): Boolean {
        val actual = metadataFor(input)
        return actual.bytes == metadata.bytes && actual.sha256.equals(metadata.sha256, ignoreCase = true)
    }

    private fun ByteArray.toHexString(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte) }
}
