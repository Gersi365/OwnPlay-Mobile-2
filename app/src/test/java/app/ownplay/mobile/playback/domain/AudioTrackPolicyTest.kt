package app.ownplay.mobile.playback.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioTrackPolicyTest {
    @Test
    fun selectionId_roundTripsGroupAndTrackIndexes() {
        val id = AudioTrackPolicy.selectionId(groupIndex = 2, trackIndex = 3)

        assertEquals("2:3", id)
        assertEquals(AudioTrackSelectionKey(2, 3), AudioTrackPolicy.parseSelectionId(id))
    }

    @Test
    fun parseSelectionId_rejectsMalformedOrNegativeValues() {
        assertNull(AudioTrackPolicy.parseSelectionId(""))
        assertNull(AudioTrackPolicy.parseSelectionId("1"))
        assertNull(AudioTrackPolicy.parseSelectionId("1:x"))
        assertNull(AudioTrackPolicy.parseSelectionId("-1:0"))
        assertNull(AudioTrackPolicy.parseSelectionId("0:-1"))
    }

    @Test
    fun primaryLabel_prefersProviderLabelThenLanguageThenOrdinal() {
        assertEquals(
            "English Commentary",
            AudioTrackPolicy.primaryLabel(
                PlaybackAudioTrack(selectionId = "0:0", label = " English Commentary ", language = "eng"),
                ordinal = 1,
            ),
        )
        assertEquals(
            "SQI",
            AudioTrackPolicy.primaryLabel(
                PlaybackAudioTrack(selectionId = "0:1", language = "sqi"),
                ordinal = 2,
            ),
        )
        assertEquals(
            "Audio 3",
            AudioTrackPolicy.primaryLabel(PlaybackAudioTrack(selectionId = "0:2"), ordinal = 3),
        )
    }

    @Test
    fun detailLabel_describesFormatChannelsAndUnsupportedState() {
        val detail = AudioTrackPolicy.detailLabel(
            PlaybackAudioTrack(
                selectionId = "0:0",
                language = "eng",
                mimeType = "audio/aac",
                channelCount = 2,
                supported = false,
            ),
        )

        assertEquals("ENG • AAC • Stereo • Unsupported", detail)
    }
}
