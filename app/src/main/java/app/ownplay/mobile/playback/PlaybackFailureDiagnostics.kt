package app.ownplay.mobile.playback

import androidx.media3.common.PlaybackException

internal object PlaybackFailureDiagnostics {
    fun safeMessage(errorCode: Int): String = when (errorCode) {
        PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW ->
            "Live stream moved ahead. Retry to reconnect."
        PlaybackException.ERROR_CODE_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> "Playback timed out. Check the connection and retry."
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
            "Network connection failed. Check connectivity and retry."
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            "The media source rejected the playback request. Retry or refresh the source."
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ->
            "The media source returned an unexpected response. Retry or refresh the source."
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED ->
            "Android blocked this HTTP connection."
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
            "The media file is no longer available."
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
            "Android denied access to the media file."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        -> "The media stream is malformed and could not be read."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        -> "This stream format is not supported."
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED,
        -> "This device could not decode the media format."
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED,
        -> "Audio output failed. Retry playback."
        in PlaybackException.ERROR_CODE_DRM_UNSPECIFIED..PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED ->
            "Protected media could not be played on this device."
        else -> "Playback unavailable. Retry playback."
    }
}
