package app.ownplay.mobile.downloads.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadPermissionPolicyTest {
    @Test
    fun `legacy public storage is requested only through Android 9`() {
        assertEquals(
            DownloadPermissionPrompt.LEGACY_PUBLIC_STORAGE,
            DownloadPermissionPolicy.nextPrompt(28, legacyStorageGranted = false, notificationsGranted = true),
        )
        assertEquals(
            DownloadPermissionPrompt.NONE,
            DownloadPermissionPolicy.nextPrompt(29, legacyStorageGranted = false, notificationsGranted = true),
        )
    }

    @Test
    fun `notification permission is requested from Android 13`() {
        assertEquals(
            DownloadPermissionPrompt.NOTIFICATIONS,
            DownloadPermissionPolicy.nextPrompt(33, legacyStorageGranted = true, notificationsGranted = false),
        )
        assertEquals(
            DownloadPermissionPrompt.NONE,
            DownloadPermissionPolicy.nextPrompt(32, legacyStorageGranted = true, notificationsGranted = false),
        )
    }

    @Test
    fun `no prompt is needed when applicable permissions are granted`() {
        assertEquals(
            DownloadPermissionPrompt.NONE,
            DownloadPermissionPolicy.nextPrompt(36, legacyStorageGranted = true, notificationsGranted = true),
        )
    }
}
