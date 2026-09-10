package app.ownplay.mobile.downloads.domain

import java.security.MessageDigest

object DownloadStatePolicy {
    fun transition(
        state: DownloadState?,
        action: DownloadAction,
    ): DownloadTransition? = when (state) {
        null -> if (action == DownloadAction.DOWNLOAD) DownloadTransition.CREATE_QUEUED else null
        DownloadState.QUEUED,
        DownloadState.DOWNLOADING,
        -> when (action) {
            DownloadAction.PAUSE -> DownloadTransition.MARK_PAUSED
            DownloadAction.REMOVE -> DownloadTransition.REMOVE
            else -> null
        }

        DownloadState.PAUSED -> when (action) {
            DownloadAction.RESUME -> DownloadTransition.MARK_QUEUED
            DownloadAction.REMOVE -> DownloadTransition.REMOVE
            else -> null
        }

        DownloadState.FAILED -> when (action) {
            DownloadAction.RETRY -> DownloadTransition.MARK_QUEUED
            DownloadAction.REMOVE -> DownloadTransition.REMOVE
            else -> null
        }

        DownloadState.COMPLETED -> when (action) {
            DownloadAction.PLAY_OFFLINE,
            DownloadAction.RESUME_OFFLINE,
            -> DownloadTransition.PLAY_OFFLINE

            DownloadAction.REMOVE -> DownloadTransition.REMOVE
            else -> null
        }
    }

    fun primaryAction(item: DownloadItem?): DownloadAction = when (item?.state) {
        null -> DownloadAction.DOWNLOAD
        DownloadState.QUEUED,
        DownloadState.DOWNLOADING,
        -> DownloadAction.PAUSE

        DownloadState.PAUSED -> DownloadAction.RESUME
        DownloadState.FAILED -> DownloadAction.RETRY
        DownloadState.COMPLETED -> if (item.resumePositionMs != null) {
            DownloadAction.RESUME_OFFLINE
        } else {
            DownloadAction.PLAY_OFFLINE
        }
    }

    fun stateAfterOnlinePlaybackStarts(state: DownloadState?): DownloadState? = state
}

object DownloadOrderingPolicy {
    fun ordered(items: List<DownloadItem>): List<DownloadItem> = items.sortedWith(
        compareByDescending<DownloadItem> { it.createdAt }
            .thenBy { it.downloadId },
    )
}

object DownloadIdentity {
    fun idFor(sourceId: String, mediaKind: String, contentId: String): String {
        val input = "$sourceId|$mediaKind|$contentId".toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256")
            .digest(input)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
