package app.ownplay.mobile.downloads.domain

enum class DownloadPermissionPrompt {
    NONE,
    LEGACY_PUBLIC_STORAGE,
    NOTIFICATIONS,
}

object DownloadPermissionPolicy {
    private const val ANDROID_9_API = 28
    private const val ANDROID_13_API = 33

    fun nextPrompt(
        sdkInt: Int,
        legacyStorageGranted: Boolean,
        notificationsGranted: Boolean,
    ): DownloadPermissionPrompt = when {
        sdkInt <= ANDROID_9_API && !legacyStorageGranted -> DownloadPermissionPrompt.LEGACY_PUBLIC_STORAGE
        sdkInt >= ANDROID_13_API && !notificationsGranted -> DownloadPermissionPrompt.NOTIFICATIONS
        else -> DownloadPermissionPrompt.NONE
    }
}
