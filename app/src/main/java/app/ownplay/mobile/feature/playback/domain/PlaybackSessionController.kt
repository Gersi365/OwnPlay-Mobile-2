package app.ownplay.mobile.feature.playback.domain

import app.ownplay.mobile.sources.domain.SourceId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PlaybackSessionController internal constructor(
    private val sourceResolver: LivePlaybackSourceResolver,
    private val mediaPreparer: LivePlaybackMediaPreparer,
    private val playbackEngine: PlaybackEngine,
    private val libraryMediaResolver: LibraryPlaybackMediaResolver? = null,
    private val playbackProgressEngine: PlaybackProgressEngine? = null,
    private val libraryProgressStore: LibraryPlaybackProgressStore? = null,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(PlaybackSessionState())

    @Volatile
    private var activeMediaRevision: Long? = null

    private var activeFallbackMedia: PreparedPlaybackMedia? = null
    private var fallbackAttempted: Boolean = false

    val state: StateFlow<PlaybackSessionState> = mutableState.asStateFlow()

    init {
        playbackEngine.setEventListener(::onPlaybackEngineEvent)
    }

    suspend fun activateLiveChannel(target: PlaybackTarget.LiveChannel) {
        mutex.withLock {
            val current = mutableState.value
            val targetChanged = current.target != target
            if (targetChanged) {
                checkpointActiveLibraryProgress()
            }
            mutableState.value = PlaybackSessionPolicy.activateLiveChannel(current, target)

            if (!targetChanged) return

            replaceTargetMedia(target) {
                sourceResolver.resolve(target)?.let(mediaPreparer::prepare)
            }
        }
    }

    suspend fun activateLibraryMedia(target: PlaybackTarget.Library) {
        mutex.withLock {
            val current = mutableState.value
            val targetChanged = current.target != target
            if (targetChanged) {
                checkpointActiveLibraryProgress()
            }
            mutableState.value = PlaybackSessionPolicy.activateLibraryMedia(current, target)

            if (!targetChanged) return

            val replaced = replaceTargetMedia(target) {
                libraryMediaResolver?.resolve(target)
            }
            if (replaced) {
                restoreLibraryProgress(target)
            }
        }
    }

    fun enterFullscreen() {
        mutableState.value = PlaybackSessionPolicy.present(
            current = mutableState.value,
            presentation = PlaybackPresentation.FULLSCREEN,
        )
    }

    fun enterPictureInPicture() {
        val current = mutableState.value
        if (!PlaybackPictureInPicturePolicy.isEligible(current)) return
        mutableState.value = PlaybackSessionPolicy.present(
            current = current,
            presentation = PlaybackPresentation.PICTURE_IN_PICTURE,
        )
    }

    fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        if (isInPictureInPictureMode) {
            enterPictureInPicture()
        } else if (mutableState.value.presentation == PlaybackPresentation.PICTURE_IN_PICTURE) {
            when (mutableState.value.target) {
                is PlaybackTarget.LiveChannel -> returnToPreview()
                is PlaybackTarget.Library -> enterFullscreen()
                null -> Unit
            }
        }
    }

    fun returnToPreview() {
        val current = mutableState.value
        if (current.target !is PlaybackTarget.LiveChannel) return
        mutableState.value = PlaybackSessionPolicy.present(
            current = current,
            presentation = PlaybackPresentation.PREVIEW,
        )
    }

    fun selectAudioTrack(trackId: String?): Boolean =
        applyTrackSelection(
            issueWhenUnsupported = PlaybackTrackSelectionIssue.AUDIO_UNSUPPORTED,
            result = playbackEngine.selectAudioTrack(trackId),
            fallbackReason = PlaybackFallbackReason.AUDIO_SELECTION,
        )

    fun selectSubtitleTrack(trackId: String?): Boolean =
        applyTrackSelection(
            issueWhenUnsupported = PlaybackTrackSelectionIssue.SUBTITLE_UNSUPPORTED,
            result = playbackEngine.selectSubtitleTrack(trackId),
            fallbackReason = null,
        )

    fun reconcileActiveSource(activeSourceId: SourceId?) {
        val target = mutableState.value.target ?: return
        if (activeSourceId != target.sourceId) {
            clear()
        }
    }

    suspend fun revalidateActiveTarget() {
        val target = mutableState.value.target ?: return
        val stillResolvable = try {
            when (target) {
                is PlaybackTarget.LiveChannel -> sourceResolver.resolve(target) != null
                is PlaybackTarget.Library -> libraryMediaResolver?.resolve(target) != null
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }

        if (mutableState.value.target == target && !stillResolvable) {
            clear()
        }
    }

    internal fun reportProlongedBuffering() {
        maybeAttemptFallback(PlaybackFallbackReason.PROLONGED_BUFFERING)
    }

    fun clear() {
        checkpointActiveLibraryProgress()
        resetActiveMedia()
        playbackEngine.clear()
        mutableState.value = PlaybackSessionState()
    }

    internal fun release() {
        checkpointActiveLibraryProgress()
        resetActiveMedia()
        playbackEngine.release()
        mutableState.value = PlaybackSessionState()
        libraryProgressStore?.close()
    }

    private suspend fun restoreLibraryProgress(target: PlaybackTarget.Library) {
        val progress = try {
            libraryProgressStore?.load(target)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        val resumePosition = LibraryPlaybackProgressPolicy.resumePosition(progress) ?: return
        if (mutableState.value.target == target) {
            playbackProgressEngine?.seekTo(resumePosition)
        }
    }

    private fun checkpointActiveLibraryProgress() {
        val target = mutableState.value.target as? PlaybackTarget.Library ?: return
        val snapshot = try {
            playbackProgressEngine?.positionSnapshot()
        } catch (_: Exception) {
            null
        }
        val progress = LibraryPlaybackProgressPolicy.checkpoint(snapshot) ?: return
        libraryProgressStore?.record(target, progress)
    }

    private suspend fun replaceTargetMedia(
        target: PlaybackTarget,
        resolve: suspend () -> PreparedPlaybackMedia?,
    ): Boolean {
        resetActiveMedia()
        playbackEngine.clear()
        val media = try {
            resolve()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }

        if (mutableState.value.target != target) return false

        if (media == null) {
            mutableState.value = mutableState.value.copy(readiness = PlaybackReadiness.UNAVAILABLE)
            return false
        }

        activeFallbackMedia = media.fallback?.let {
            PreparedPlaybackMedia(
                uri = it.uri,
                mimeType = it.mimeType,
            )
        }

        return try {
            activeMediaRevision = playbackEngine.replace(media.primaryOnly())
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            resetActiveMedia()
            playbackEngine.clear()
            mutableState.value = mutableState.value.copy(readiness = PlaybackReadiness.UNAVAILABLE)
            false
        }
    }

    private fun applyTrackSelection(
        issueWhenUnsupported: PlaybackTrackSelectionIssue,
        result: PlaybackEngineSelectionResult,
        fallbackReason: PlaybackFallbackReason?,
    ): Boolean {
        val current = mutableState.value
        if (current.target == null) return false

        return when (result) {
            PlaybackEngineSelectionResult.APPLIED -> {
                mutableState.value = current.copy(
                    tracks = current.tracks.copy(selectionIssue = null),
                )
                true
            }

            PlaybackEngineSelectionResult.UNSUPPORTED -> {
                mutableState.value = current.copy(
                    tracks = current.tracks.copy(selectionIssue = issueWhenUnsupported),
                )
                false
            }

            PlaybackEngineSelectionResult.FAILED -> {
                mutableState.value = current.copy(
                    tracks = current.tracks.copy(
                        selectionIssue = PlaybackTrackSelectionIssue.SELECTION_FAILED,
                    ),
                )
                if (fallbackReason != null) {
                    maybeAttemptFallback(fallbackReason)
                }
                false
            }
        }
    }

    private fun onPlaybackEngineEvent(event: PlaybackEngineEvent) {
        if (event.mediaRevision != activeMediaRevision) return
        val current = mutableState.value
        if (current.target == null) return

        event.tracks?.let { engineTracks ->
            mutableState.value = mutableState.value.copy(
                tracks = mapTracks(
                    engineTracks = engineTracks,
                    priorIssue = mutableState.value.tracks.selectionIssue,
                ),
            )
        }

        when (event.readiness) {
            null -> Unit
            PlaybackEngineReadiness.PREPARING -> {
                mutableState.value = mutableState.value.copy(
                    readiness = PlaybackReadiness.PREPARING,
                )
            }

            PlaybackEngineReadiness.READY -> {
                mutableState.value = mutableState.value.copy(
                    readiness = PlaybackReadiness.PREPARED,
                )
            }

            PlaybackEngineReadiness.FAILED -> {
                val endedLibraryPlayback = current.target is PlaybackTarget.Library &&
                    runCatching { playbackProgressEngine?.positionSnapshot()?.ended == true }
                        .getOrDefault(false)
                if (endedLibraryPlayback) {
                    checkpointActiveLibraryProgress()
                    mutableState.value = mutableState.value.copy(
                        readiness = PlaybackReadiness.PREPARED,
                        fallback = mutableState.value.fallback.copy(active = false),
                    )
                    return
                }

                val fallbackReason = when (event.failureClass) {
                    PlaybackEngineFailureClass.DECODER_OR_FORMAT ->
                        PlaybackFallbackReason.DECODER_OR_FORMAT
                    PlaybackEngineFailureClass.AUDIO ->
                        PlaybackFallbackReason.AUDIO_SELECTION
                    PlaybackEngineFailureClass.OTHER,
                    null,
                    -> PlaybackFallbackReason.OTHER
                }
                if (!maybeAttemptFallback(fallbackReason)) {
                    mutableState.value = mutableState.value.copy(
                        readiness = PlaybackReadiness.UNAVAILABLE,
                        fallback = mutableState.value.fallback.copy(active = false),
                    )
                }
            }
        }
    }

    private fun maybeAttemptFallback(reason: PlaybackFallbackReason): Boolean {
        val fallbackMedia = activeFallbackMedia
        if (
            !PlaybackFallbackPolicy.canAttempt(
                hasFallback = fallbackMedia != null,
                alreadyAttempted = fallbackAttempted,
                reason = reason,
            )
        ) {
            return false
        }

        requireNotNull(fallbackMedia)
        fallbackAttempted = true
        activeFallbackMedia = null
        activeMediaRevision = null
        mutableState.value = mutableState.value.copy(
            readiness = PlaybackReadiness.PREPARING,
            tracks = PlaybackTrackSnapshot(),
            fallback = PlaybackFallbackState(
                attempted = true,
                active = true,
            ),
        )

        return try {
            activeMediaRevision = playbackEngine.replace(fallbackMedia)
            true
        } catch (_: Exception) {
            activeMediaRevision = null
            playbackEngine.clear()
            mutableState.value = mutableState.value.copy(
                readiness = PlaybackReadiness.UNAVAILABLE,
                fallback = PlaybackFallbackState(
                    attempted = true,
                    active = false,
                ),
            )
            true
        }
    }

    private fun mapTracks(
        engineTracks: PlaybackEngineTracks,
        priorIssue: PlaybackTrackSelectionIssue?,
    ): PlaybackTrackSnapshot {
        var audioOrdinal = 0
        var subtitleOrdinal = 0

        val mapped = engineTracks.tracks.map { track ->
            val kind = when (track.kind) {
                PlaybackEngineTrackKind.AUDIO -> {
                    audioOrdinal += 1
                    PlaybackTrackKind.AUDIO
                }
                PlaybackEngineTrackKind.SUBTITLE -> {
                    subtitleOrdinal += 1
                    PlaybackTrackKind.SUBTITLE
                }
            }
            val ordinal = when (kind) {
                PlaybackTrackKind.AUDIO -> audioOrdinal
                PlaybackTrackKind.SUBTITLE -> subtitleOrdinal
            }

            PlaybackTrackOption(
                id = track.id,
                kind = kind,
                label = PlaybackTrackLabelPolicy.label(
                    kind = kind,
                    ordinal = ordinal,
                    labelHint = track.labelHint,
                    language = track.language,
                    codec = track.codec,
                    channelCount = track.channelCount,
                    role = track.role,
                ),
                language = track.language,
                codec = track.codec,
                channelCount = track.channelCount,
                role = track.role,
                supported = track.supported,
                selected = track.selected,
            )
        }

        val audioTracks = mapped.filter { it.kind == PlaybackTrackKind.AUDIO }
        val subtitleTracks = mapped.filter { it.kind == PlaybackTrackKind.SUBTITLE }
        return PlaybackTrackSnapshot(
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracks,
            selectedAudioTrackId = audioTracks.firstOrNull { it.selected }?.id,
            selectedSubtitleTrackId = subtitleTracks.firstOrNull { it.selected }?.id,
            selectionIssue = priorIssue,
        )
    }

    private fun resetActiveMedia() {
        activeMediaRevision = null
        activeFallbackMedia = null
        fallbackAttempted = false
    }
}
