package app.ownplay.mobile.feature.library.data

import app.ownplay.mobile.downloads.domain.DownloadId
import app.ownplay.mobile.downloads.domain.DownloadMediaKind
import app.ownplay.mobile.downloads.domain.DownloadRepository
import app.ownplay.mobile.downloads.domain.DownloadStatus
import app.ownplay.mobile.downloads.data.DownloadedMediaVerifier
import app.ownplay.mobile.feature.playback.domain.LibraryPlaybackMediaResolver
import app.ownplay.mobile.feature.playback.domain.PlaybackTarget
import app.ownplay.mobile.feature.playback.domain.PreparedPlaybackMedia
import kotlinx.coroutines.CancellationException

internal class DownloadAwareLibraryPlaybackResolver(
    private val onlineResolver: LibraryPlaybackMediaResolver,
    private val downloadRepository: DownloadRepository,
    private val verifier: DownloadedMediaVerifier,
) : LibraryPlaybackMediaResolver {
    override suspend fun resolve(target: PlaybackTarget.Library): PreparedPlaybackMedia? {
        val offlineDownloadId = target.offlineDownloadId ?: return onlineResolver.resolve(target)
        return try {
            val item = downloadRepository.get(DownloadId(offlineDownloadId)) ?: return null
            val expectedKind = when (target) {
                is PlaybackTarget.Movie -> DownloadMediaKind.MOVIE
                is PlaybackTarget.Episode -> DownloadMediaKind.EPISODE
            }
            val expectedContentId = when (target) {
                is PlaybackTarget.Movie -> target.movieId
                is PlaybackTarget.Episode -> target.episodeId
            }
            if (
                item.sourceId != target.sourceId ||
                item.mediaKind != expectedKind ||
                item.contentId != expectedContentId ||
                item.status != DownloadStatus.COMPLETED ||
                item.verifiedBytes == null ||
                item.verifiedBytes != item.bytesDownloaded ||
                (item.totalBytes != null && item.totalBytes != item.verifiedBytes)
            ) {
                return null
            }
            val reference = item.localReference?.takeIf(String::isNotBlank) ?: return null
            if (!verifier.verify(item)) return null
            // Verification can take time. A removed or replaced record must not be played.
            if (downloadRepository.get(item.downloadId) != item) return null
            PreparedPlaybackMedia(uri = reference)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }
}
