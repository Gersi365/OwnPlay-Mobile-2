package app.ownplay.mobile.downloads.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import app.ownplay.mobile.data.db.DownloadDao
import app.ownplay.mobile.data.db.DownloadEntity
import app.ownplay.mobile.data.db.LibraryDao
import app.ownplay.mobile.data.db.PlaybackProgressEntity
import app.ownplay.mobile.downloads.domain.DownloadIdentity
import app.ownplay.mobile.downloads.domain.DownloadItem
import app.ownplay.mobile.downloads.domain.DownloadOperationResult
import app.ownplay.mobile.downloads.domain.DownloadOrderingPolicy
import app.ownplay.mobile.downloads.domain.DownloadRepository
import app.ownplay.mobile.downloads.domain.DownloadState
import app.ownplay.mobile.downloads.domain.DownloadWorkResult
import app.ownplay.mobile.feature.library.domain.LibraryMediaKind
import app.ownplay.mobile.feature.library.domain.LibraryPlaybackResolution
import app.ownplay.mobile.feature.library.domain.LibraryStartMode
import app.ownplay.mobile.feature.library.domain.LibraryStartPolicy
import app.ownplay.mobile.feature.library.domain.ResolvedLibraryPlayback
import app.ownplay.mobile.playback.domain.PlaybackStreamFormat
import app.ownplay.mobile.sources.domain.SourceRepository
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal class DownloadRepositoryImpl(
    context: Context,
    private val sourceRepository: SourceRepository,
    private val downloadDao: DownloadDao,
    private val libraryDao: LibraryDao,
    private val streamResolver: DownloadStreamResolver,
    private val workManager: WorkManager,
    private val httpClient: OkHttpClient,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : DownloadRepository {
    private val fileStore = DownloadFileStore(context.applicationContext.filesDir)
    private val publicFileStore = PublicDownloadFileStore(context.applicationContext)
    private val transferMutex = Mutex()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeDownloads(): Flow<List<DownloadItem>> =
        sourceRepository.observeActiveSource().flatMapLatest { source ->
            if (source == null) {
                flowOf(emptyList())
            } else {
                combine(
                    downloadDao.observeForSource(source.sourceId),
                    libraryDao.observeIncompleteProgress(source.sourceId),
                ) { downloads, progress ->
                    val progressByKey = progress.associateBy { row ->
                        ProgressKey(row.mediaKind.uppercase(Locale.US), row.contentId)
                    }
                    DownloadOrderingPolicy.ordered(
                        downloads.mapNotNull { row ->
                            row.toDomainOrNull(
                                progress = progressByKey[
                                    ProgressKey(row.mediaKind.uppercase(Locale.US), row.contentId)
                                ],
                            )
                        },
                    )
                }
            }
        }

    override suspend fun requestDownload(
        sourceId: String,
        mediaKind: LibraryMediaKind,
        contentId: String,
        title: String,
    ): DownloadOperationResult {
        if (sourceId.isBlank() || contentId.isBlank() || title.isBlank()) {
            return failure("INVALID_DOWNLOAD", "This item cannot be downloaded.")
        }
        if (!contentBelongsToSource(sourceId, mediaKind, contentId)) {
            return failure("MEDIA_UNAVAILABLE", "This item is no longer available for download.")
        }
        val existing = downloadDao.getForContent(sourceId, mediaKind.name, contentId)
        if (existing != null) {
            return DownloadOperationResult.Success(existing.toDomainOrNull())
        }
        if (!publicFileStore.canWrite()) {
            return failure(
                "STORAGE_PERMISSION_REQUIRED",
                "Allow storage access so OwnPlay can save media in Download/OwnPlay Downloads.",
            )
        }

        val now = nowMillis()
        val downloadId = DownloadIdentity.idFor(sourceId, mediaKind.name, contentId)
        val row = DownloadEntity(
            downloadId = downloadId,
            sourceId = sourceId,
            mediaKind = mediaKind.name,
            contentId = contentId,
            title = title,
            streamIdentity = "library:${mediaKind.name}:$contentId",
            state = DownloadState.QUEUED.name,
            bytesDownloaded = 0L,
            totalBytes = null,
            localReference = null,
            integrityMetadata = null,
            failureReason = null,
            createdAt = now,
            updatedAt = now,
        )
        return try {
            downloadDao.insert(row)
            schedule(downloadId)
            DownloadOperationResult.Success(row.toDomainOrNull())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            downloadDao.failIfRunnable(downloadId, "SCHEDULER", nowMillis())
            failure("DOWNLOAD_SCHEDULE_FAILED", "The download could not be scheduled.")
        }
    }

    override suspend fun pause(downloadId: String): DownloadOperationResult {
        if (downloadId.isBlank()) return failure("INVALID_DOWNLOAD", "This download cannot be paused.")
        val changed = downloadDao.pauseIfActive(downloadId, nowMillis())
        if (changed == 0) return invalidState(downloadId, "pause")
        workManager.cancelUniqueWork(workName(downloadId)).await()
        return DownloadOperationResult.Success(downloadDao.get(downloadId)?.toDomainOrNull())
    }

    override suspend fun resume(downloadId: String): DownloadOperationResult {
        if (downloadId.isBlank()) return failure("INVALID_DOWNLOAD", "This download cannot be resumed.")
        val changed = downloadDao.queueIfPaused(downloadId, nowMillis())
        if (changed == 0) return invalidState(downloadId, "resume")
        return scheduleAndReturn(downloadId)
    }

    override suspend fun retry(downloadId: String): DownloadOperationResult {
        if (downloadId.isBlank()) return failure("INVALID_DOWNLOAD", "This download cannot be retried.")
        val changed = downloadDao.queueIfFailed(downloadId, nowMillis())
        if (changed == 0) return invalidState(downloadId, "retry")
        return scheduleAndReturn(downloadId)
    }

    override suspend fun remove(downloadId: String): DownloadOperationResult {
        if (downloadId.isBlank()) return failure("INVALID_DOWNLOAD", "This download cannot be removed.")
        val row = downloadDao.get(downloadId)
            ?: return failure("DOWNLOAD_NOT_FOUND", "This download no longer exists.")
        workManager.cancelUniqueWork(workName(downloadId)).await()
        fileStore.delete(row)
        row.localReference
            ?.takeIf(publicFileStore::handles)
            ?.let(publicFileStore::delete)
        downloadDao.delete(downloadId)
        return DownloadOperationResult.Success()
    }

    override suspend fun resolveOfflinePlayback(
        downloadId: String,
        startMode: LibraryStartMode,
    ): LibraryPlaybackResolution {
        val row = downloadDao.get(downloadId)
            ?: return playbackFailure("DOWNLOAD_NOT_FOUND", "This download no longer exists.")
        val state = row.state.toDownloadStateOrNull()
        if (state != DownloadState.COMPLETED) {
            return playbackFailure("DOWNLOAD_INCOMPLETE", "Offline playback is available only after the download is complete.")
        }
        val localReference = row.localReference
            ?: return playbackFailure("OFFLINE_FILE_MISSING", "The offline file is unavailable.")
        val integrityMetadata = row.integrityMetadata
            ?: return playbackFailure("OFFLINE_INTEGRITY_MISSING", "The offline file cannot be verified.")

        val playbackUri = if (publicFileStore.handles(localReference)) {
            if (!publicFileStore.verify(localReference, integrityMetadata)) {
                publicFileStore.delete(localReference)
                downloadDao.markCompletedIntegrityFailure(downloadId, nowMillis())
                return playbackFailure(
                    "OFFLINE_INTEGRITY_FAILED",
                    "The offline file failed integrity verification. Retry the download.",
                )
            }
            localReference
        } else {
            val file = fileStore.resolve(localReference)
            if (file == null || !DownloadIntegrity.verify(file, integrityMetadata)) {
                if (file != null) file.delete()
                downloadDao.markCompletedIntegrityFailure(downloadId, nowMillis())
                return playbackFailure(
                    "OFFLINE_INTEGRITY_FAILED",
                    "The offline file failed integrity verification. Retry the download.",
                )
            }
            file.toURI().toString()
        }

        val mediaKind = runCatching {
            LibraryMediaKind.valueOf(row.mediaKind.uppercase(Locale.US))
        }.getOrNull() ?: return playbackFailure("INVALID_MEDIA_KIND", "This offline item cannot be played.")
        val progress = libraryDao.getProgress(row.sourceId, mediaKind.name, row.contentId)
            ?.takeIf { saved -> !saved.completed && saved.positionMs > 0L && saved.durationMs > 0L }
        return LibraryPlaybackResolution.Success(
            ResolvedLibraryPlayback(
                sourceId = row.sourceId,
                contentId = row.contentId,
                mediaKind = mediaKind,
                title = row.title,
                subtitle = "Downloaded • ${if (mediaKind == LibraryMediaKind.MOVIE) "Movie" else "Episode"}",
                uri = playbackUri,
                streamFormat = PlaybackStreamFormat.AUTO,
                start = LibraryStartPolicy.resolve(startMode, progress?.positionMs),
                knownDurationMs = progress?.durationMs,
                offline = true,
            ),
        )
    }

    override suspend fun executeWork(downloadId: String): DownloadWorkResult = transferMutex.withLock {
        val initial = downloadDao.get(downloadId) ?: return@withLock DownloadWorkResult.NO_OP
        val initialState = initial.state.toDownloadStateOrNull() ?: return@withLock DownloadWorkResult.FAILURE
        if (initialState == DownloadState.PAUSED || initialState == DownloadState.COMPLETED || initialState == DownloadState.FAILED) {
            return@withLock DownloadWorkResult.NO_OP
        }
        if (downloadDao.markDownloadingIfRunnable(downloadId, nowMillis()) == 0) {
            return@withLock DownloadWorkResult.NO_OP
        }
        val row = downloadDao.get(downloadId) ?: return@withLock DownloadWorkResult.NO_OP

        val completedFile = fileStore.finalFile(downloadId)
        if (completedFile.isFile && completedFile.length() > 0L) {
            val metadata = DownloadIntegrity.metadataFor(completedFile)
            if (DownloadIntegrity.verify(completedFile, metadata.encode())) {
                val changed = downloadDao.completeIfDownloading(
                    downloadId = downloadId,
                    bytesDownloaded = metadata.bytes,
                    totalBytes = metadata.bytes,
                    localReference = fileStore.finalReference(downloadId),
                    integrityMetadata = metadata.encode(),
                    updatedAt = nowMillis(),
                )
                return@withLock if (changed > 0) DownloadWorkResult.SUCCESS else DownloadWorkResult.NO_OP
            }
            completedFile.delete()
        }

        when (val source = streamResolver.resolve(row)) {
            is DownloadSourceResolution.Failure -> {
                if (source.retryable) {
                    DownloadWorkResult.RETRY
                } else {
                    downloadDao.failIfRunnable(downloadId, source.code, nowMillis())
                    DownloadWorkResult.FAILURE
                }
            }

            is DownloadSourceResolution.Success -> when (
                val transfer = transfer(row, source.value)
            ) {
                is TransferResult.Complete -> {
                    val changed = downloadDao.completeIfDownloading(
                        downloadId = downloadId,
                        bytesDownloaded = transfer.bytes,
                        totalBytes = transfer.bytes,
                        localReference = transfer.localReference,
                        integrityMetadata = transfer.integrityMetadata,
                        updatedAt = nowMillis(),
                    )
                    if (changed > 0) {
                        DownloadWorkResult.SUCCESS
                    } else {
                        publicFileStore.delete(transfer.localReference)
                        DownloadWorkResult.NO_OP
                    }
                }

                is TransferResult.FatalFailure -> {
                    downloadDao.failIfRunnable(downloadId, transfer.code, nowMillis())
                    DownloadWorkResult.FAILURE
                }

                TransferResult.TransientFailure -> DownloadWorkResult.RETRY
                TransferResult.Stopped -> DownloadWorkResult.NO_OP
            }
        }
    }

    private suspend fun scheduleAndReturn(downloadId: String): DownloadOperationResult = try {
        schedule(downloadId)
        DownloadOperationResult.Success(downloadDao.get(downloadId)?.toDomainOrNull())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        downloadDao.failIfRunnable(downloadId, "SCHEDULER", nowMillis())
        failure("DOWNLOAD_SCHEDULE_FAILED", "The download could not be scheduled.")
    }

    private suspend fun schedule(downloadId: String) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_DOWNLOAD_ID to downloadId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .addTag(WORK_TAG)
            .addTag("$WORK_TAG:$downloadId")
            .build()
        workManager.enqueueUniqueWork(
            workName(downloadId),
            ExistingWorkPolicy.REPLACE,
            request,
        ).await()
    }

    private suspend fun contentBelongsToSource(
        sourceId: String,
        mediaKind: LibraryMediaKind,
        contentId: String,
    ): Boolean = when (mediaKind) {
        LibraryMediaKind.MOVIE -> libraryDao.getMovie(contentId)?.let { movie ->
            movie.sourceId == sourceId && movie.available
        } ?: false

        LibraryMediaKind.EPISODE -> libraryDao.getEpisode(contentId)?.let { episode ->
            episode.sourceId == sourceId && episode.available
        } ?: false
    }

    private suspend fun invalidState(downloadId: String, verb: String): DownloadOperationResult {
        val item = downloadDao.get(downloadId)?.toDomainOrNull()
        return if (item == null) {
            failure("DOWNLOAD_NOT_FOUND", "This download no longer exists.")
        } else {
            failure("DOWNLOAD_STATE_CONFLICT", "This download cannot $verb from its current state.")
        }
    }

    private suspend fun transfer(
        row: DownloadEntity,
        source: ResolvedDownloadSource,
    ): TransferResult = withContext(Dispatchers.IO) {
        val partial = fileStore.partialFile(row.downloadId)
        partial.parentFile?.mkdirs()

        var resumeOffset = partial.takeIf { it.isFile }?.length()?.coerceAtLeast(0L) ?: 0L
        var attempt = 0
        while (attempt < 2) {
            coroutineContext.ensureActive()
            val requestBuilder = Request.Builder().url(source.uri)
            if (resumeOffset > 0L) {
                requestBuilder.header("Range", "bytes=$resumeOffset-")
            }
            val response = try {
                httpClient.newCall(requestBuilder.build()).execute()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                return@withContext TransferResult.TransientFailure
            } catch (_: Exception) {
                return@withContext TransferResult.FatalFailure("REQUEST")
            }

            response.use { httpResponse ->
                if (httpResponse.code == 416 && resumeOffset > 0L) {
                    partial.delete()
                    resumeOffset = 0L
                    attempt += 1
                    return@use
                }
                if (!httpResponse.isSuccessful) {
                    return@withContext if (httpResponse.code == 408 || httpResponse.code == 429 || httpResponse.code >= 500) {
                        TransferResult.TransientFailure
                    } else {
                        TransferResult.FatalFailure("HTTP_${httpResponse.code}")
                    }
                }
                val body = httpResponse.body
                    ?: return@withContext TransferResult.FatalFailure("EMPTY_RESPONSE")
                val append = resumeOffset > 0L && httpResponse.code == 206
                if (!append && resumeOffset > 0L) {
                    partial.delete()
                    resumeOffset = 0L
                }
                val contentLength = body.contentLength().takeIf { it >= 0L }
                val totalBytes = if (append) {
                    parseContentRangeTotal(httpResponse.header("Content-Range"))
                        ?: contentLength?.plus(resumeOffset)
                } else {
                    contentLength
                }

                var downloaded = if (append) resumeOffset else 0L
                var lastReported = downloaded
                FileOutputStream(partial, append).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = try {
                                input.read(buffer)
                            } catch (_: IOException) {
                                return@withContext TransferResult.TransientFailure
                            }
                            if (count < 0) break
                            if (count == 0) continue
                            output.write(buffer, 0, count)
                            downloaded += count
                            if (downloaded - lastReported >= PROGRESS_REPORT_BYTES) {
                                val changed = downloadDao.updateProgressIfDownloading(
                                    downloadId = row.downloadId,
                                    bytesDownloaded = downloaded,
                                    totalBytes = totalBytes,
                                    updatedAt = nowMillis(),
                                )
                                if (changed == 0) return@withContext TransferResult.Stopped
                                lastReported = downloaded
                            }
                        }
                        output.flush()
                    }
                }
                if (downloadDao.updateProgressIfDownloading(
                        downloadId = row.downloadId,
                        bytesDownloaded = downloaded,
                        totalBytes = totalBytes,
                        updatedAt = nowMillis(),
                    ) == 0
                ) {
                    return@withContext TransferResult.Stopped
                }
                if (downloaded <= 0L || !partial.isFile || partial.length() != downloaded) {
                    return@withContext TransferResult.FatalFailure("INCOMPLETE_FILE")
                }

                val metadata = DownloadIntegrity.metadataFor(partial)
                if (metadata.bytes != downloaded) {
                    return@withContext TransferResult.FatalFailure("INTEGRITY")
                }
                val destination = resolveDestination(row, source)
                    ?: return@withContext TransferResult.FatalFailure("DESTINATION_METADATA")
                val localReference = try {
                    publicFileStore.publish(
                        sourceFile = partial,
                        destination = destination,
                        integrityMetadata = metadata.encode(),
                    )
                } catch (_: SecurityException) {
                    return@withContext TransferResult.FatalFailure("STORAGE_PERMISSION")
                } catch (_: IOException) {
                    return@withContext TransferResult.FatalFailure("PUBLIC_STORAGE")
                } catch (_: Exception) {
                    return@withContext TransferResult.FatalFailure("PUBLIC_STORAGE")
                }
                if (!publicFileStore.verify(localReference, metadata.encode())) {
                    publicFileStore.delete(localReference)
                    return@withContext TransferResult.FatalFailure("INTEGRITY")
                }
                partial.delete()
                return@withContext TransferResult.Complete(
                    bytes = metadata.bytes,
                    localReference = localReference,
                    integrityMetadata = metadata.encode(),
                )
            }
        }
        TransferResult.TransientFailure
    }

    private suspend fun resolveDestination(
        row: DownloadEntity,
        source: ResolvedDownloadSource,
    ): DownloadDestination? {
        val mediaKind = runCatching {
            LibraryMediaKind.valueOf(row.mediaKind.uppercase(Locale.US))
        }.getOrNull() ?: return null
        return when (mediaKind) {
            LibraryMediaKind.MOVIE -> {
                val movie = libraryDao.getMovie(row.contentId) ?: return null
                if (movie.sourceId != row.sourceId) return null
                DownloadDestinationPolicy.movie(
                    title = movie.name,
                    extension = movie.extension.takeUnless { it.isNullOrBlank() }
                        ?: DownloadDestinationPolicy.extensionFromUri(source.uri),
                )
            }

            LibraryMediaKind.EPISODE -> {
                val episode = libraryDao.getEpisode(row.contentId) ?: return null
                if (episode.sourceId != row.sourceId) return null
                DownloadDestinationPolicy.episode(
                    seriesTitle = episode.seriesName,
                    seasonNumber = episode.seasonNumber,
                    episodeNumber = episode.episodeNumber,
                    episodeTitle = episode.title,
                    extension = episode.extension.takeUnless { it.isNullOrBlank() }
                        ?: DownloadDestinationPolicy.extensionFromUri(source.uri),
                )
            }
        }
    }

    private fun parseContentRangeTotal(value: String?): Long? {
        val raw = value?.substringAfter('/')?.trim().orEmpty()
        return raw.takeIf { it.isNotBlank() && it != "*" }?.toLongOrNull()?.takeIf { it >= 0L }
    }

    private fun DownloadEntity.toDomainOrNull(progress: PlaybackProgressEntity? = null): DownloadItem? {
        val mediaKind = runCatching { LibraryMediaKind.valueOf(mediaKind.uppercase(Locale.US)) }.getOrNull()
            ?: return null
        val parsedState = state.toDownloadStateOrNull() ?: return null
        val resumePosition = progress?.takeIf { saved ->
            !saved.completed && saved.positionMs > 0L && saved.durationMs > 0L
        }?.positionMs
        return DownloadItem(
            downloadId = downloadId,
            sourceId = sourceId,
            mediaKind = mediaKind,
            contentId = contentId,
            title = title,
            state = parsedState,
            bytesDownloaded = bytesDownloaded.coerceAtLeast(0L),
            totalBytes = totalBytes?.takeIf { it > 0L },
            createdAt = createdAt,
            updatedAt = updatedAt,
            resumePositionMs = resumePosition,
        )
    }

    private fun String.toDownloadStateOrNull(): DownloadState? = runCatching {
        DownloadState.valueOf(uppercase(Locale.US))
    }.getOrNull()

    private fun failure(code: String, message: String): DownloadOperationResult.Failure =
        DownloadOperationResult.Failure(code = code, safeMessage = message)

    private fun playbackFailure(code: String, message: String): LibraryPlaybackResolution.Failure =
        LibraryPlaybackResolution.Failure(code = code, safeMessage = message)

    private data class ProgressKey(val mediaKind: String, val contentId: String)

    private sealed interface TransferResult {
        data class Complete(
            val bytes: Long,
            val localReference: String,
            val integrityMetadata: String,
        ) : TransferResult

        data class FatalFailure(val code: String) : TransferResult
        data object TransientFailure : TransferResult
        data object Stopped : TransferResult
    }

    private class DownloadFileStore(private val filesDir: File) {
        private val root = File(filesDir, DIRECTORY_NAME)

        fun partialFile(downloadId: String): File = File(root, "${safeName(downloadId)}.part")

        fun finalFile(downloadId: String): File = File(root, "${safeName(downloadId)}.media")

        fun finalReference(downloadId: String): String = "$DIRECTORY_NAME/${safeName(downloadId)}.media"

        fun resolve(reference: String): File? {
            return try {
                val canonicalRoot = root.canonicalFile
                val candidate = File(filesDir, reference).canonicalFile
                if (candidate.parentFile == canonicalRoot) candidate else null
            } catch (_: IOException) {
                null
            }
        }

        fun delete(row: DownloadEntity) {
            partialFile(row.downloadId).delete()
            finalFile(row.downloadId).delete()
            row.localReference?.let { reference -> resolve(reference)?.delete() }
        }

        private fun safeName(downloadId: String): String = DownloadIdentity.idFor("file", "download", downloadId)
    }

    private companion object {
        const val WORK_TAG = "ownplay-download"
        const val DIRECTORY_NAME = "ownplay-downloads"
        const val PROGRESS_REPORT_BYTES = 512L * 1024L

        fun workName(downloadId: String): String = "ownplay-download-$downloadId"
    }
}
