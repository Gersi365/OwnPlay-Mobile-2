package app.ownplay.mobile.downloads.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadPermissionPolicyTest {
    @Test
    fun `legacy public storage is requested only through Android 9`() {
        assertEquals(
            DownloadPermissionPrompt.LEGACY_PUBLIC_STORAGE,
            DownloadPermissionPolicy.nextPrompt(
                sdkInt = 28,
                legacyStorageGranted = false,
                notificationsGranted = true,
                notificationPermissionPrompted = false,
            ),
        )
        assertEquals(
            DownloadPermissionPrompt.NONE,
            DownloadPermissionPolicy.nextPrompt(
                sdkInt = 29,
                legacyStorageGranted = false,
                notificationsGranted = true,
                notificationPermissionPrompted = false,
            ),
        )
    }

    @Test
    fun `notification permission is requested once from Android 13`() {
        assertEquals(
            DownloadPermissionPrompt.NOTIFICATIONS,
            DownloadPermissionPolicy.nextPrompt(
                sdkInt = 33,
                legacyStorageGranted = true,
                notificationsGranted = false,
                notificationPermissionPrompted = false,
            ),
        )
        assertEquals(
            DownloadPermissionPrompt.NONE,
            DownloadPermissionPolicy.nextPrompt(
                sdkInt = 33,
                legacyStorageGranted = true,
                notificationsGranted = false,
                notificationPermissionPrompted = true,
            ),
        )
    }

    @Test
    fun `notification permission is not requested before Android 13`() {
        assertEquals(
            DownloadPermissionPrompt.NONE,
            DownloadPermissionPolicy.nextPrompt(
                sdkInt = 32,
                legacyStorageGranted = true,
                notificationsGranted = false,
                notificationPermissionPrompted = false,
            ),
        )
    }

    @Test
    fun `no prompt is needed when applicable permissions are granted`() {
        assertEquals(
            DownloadPermissionPrompt.NONE,
            DownloadPermissionPolicy.nextPrompt(
                sdkInt = 36,
                legacyStorageGranted = true,
                notificationsGranted = true,
                notificationPermissionPrompted = false,
            ),
        )
    }
}
