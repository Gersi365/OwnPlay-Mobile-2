package app.ownplay.mobile.downloads.domain

data class DownloadNotificationSnapshot(
    val downloadId: String,
    val title: String,
    val state: DownloadState,
    val bytesDownloaded: Long,
    val totalBytes: Long?,
)

data class DownloadNotificationPresentation(
    val visible: Boolean,
    val statusText: String,
    val progressPercent: Int?,
    val progressIndeterminate: Boolean,
    val action: DownloadAction?,
)

object DownloadNotificationPolicy {
    fun present(snapshot: DownloadNotificationSnapshot): DownloadNotificationPresentation {
        val percent = progressPercent(snapshot.bytesDownloaded, snapshot.totalBytes)
        val stateLabel = when (snapshot.state) {
            DownloadState.QUEUED -> "Queued"
            DownloadState.DOWNLOADING -> "Downloading"
            DownloadState.PAUSED -> "Paused"
            DownloadState.FAILED -> "Failed"
            DownloadState.COMPLETED -> "Downloaded"
        }
        val visible = snapshot.state == DownloadState.QUEUED ||
            snapshot.state == DownloadState.DOWNLOADING ||
            snapshot.state == DownloadState.PAUSED
        val action = when (snapshot.state) {
            DownloadState.QUEUED,
            DownloadState.DOWNLOADING,
            -> DownloadAction.PAUSE
            DownloadState.PAUSED -> DownloadAction.RESUME
            DownloadState.FAILED,
            DownloadState.COMPLETED,
            -> null
        }
        return DownloadNotificationPresentation(
            visible = visible,
            statusText = if (percent == null) stateLabel else "$stateLabel · $percent%",
            progressPercent = percent,
            progressIndeterminate = percent == null &&
                (snapshot.state == DownloadState.QUEUED || snapshot.state == DownloadState.DOWNLOADING),
            action = action,
        )
    }

    private fun progressPercent(bytesDownloaded: Long, totalBytes: Long?): Int? {
        val total = totalBytes?.takeIf { it > 0L } ?: return null
        val bytes = bytesDownloaded.coerceAtLeast(0L)
        return ((bytes.toDouble() / total.toDouble()) * 100.0)
            .toInt()
            .coerceIn(0, 100)
    }
}
