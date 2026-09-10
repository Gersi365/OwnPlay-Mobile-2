package app.ownplay.mobile.downloads.data

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadIntegrityTest {
    @Test
    fun completeFilePassesIntegrityVerification() {
        val file = tempFile("ownplay-integrity", "media bytes")
        try {
            val metadata = DownloadIntegrity.metadataFor(file).encode()
            assertTrue(DownloadIntegrity.verify(file, metadata))
        } finally {
            file.delete()
        }
    }

    @Test
    fun modifiedFileFailsIntegrityVerification() {
        val file = tempFile("ownplay-integrity-modified", "original")
        try {
            val metadata = DownloadIntegrity.metadataFor(file).encode()
            file.appendText("tampered")
            assertFalse(DownloadIntegrity.verify(file, metadata))
        } finally {
            file.delete()
        }
    }

    @Test
    fun missingOrMalformedIntegrityMetadataFailsClosed() {
        val file = tempFile("ownplay-integrity-malformed", "content")
        try {
            assertFalse(DownloadIntegrity.verify(file, ""))
            assertFalse(DownloadIntegrity.verify(file, "sha256=abc;bytes=${file.length()}"))
            assertFalse(DownloadIntegrity.verify(File(file.parentFile, "missing-file"), DownloadIntegrity.metadataFor(file).encode()))
        } finally {
            file.delete()
        }
    }

    private fun tempFile(prefix: String, content: String): File =
        Files.createTempFile(prefix, ".bin").toFile().apply { writeText(content) }
}
