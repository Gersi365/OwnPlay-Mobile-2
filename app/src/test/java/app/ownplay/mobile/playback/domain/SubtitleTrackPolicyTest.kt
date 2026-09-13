package app.ownplay.mobile.playback.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleTrackPolicyTest {
    @Test
    fun `selection id round trips valid indexes`() {
        val id = SubtitleTrackPolicy.selectionId(2, 4)
        assertEquals(SubtitleTrackSelectionKey(2, 4), SubtitleTrackPolicy.parseSelectionId(id))
    }

    @Test
    fun `invalid selection id is rejected`() {
        assertNull(SubtitleTrackPolicy.parseSelectionId("bad"))
        assertNull(SubtitleTrackPolicy.parseSelectionId("-1:0"))
    }

    @Test
    fun `language is used when provider label is absent`() {
        val label = SubtitleTrackPolicy.primaryLabel(
            PlaybackSubtitleTrack(selectionId = "0:0", language = "en"),
            ordinal = 1,
        )
        assertEquals("EN", label)
    }

    @Test
    fun `detail describes common caption format`() {
        val detail = SubtitleTrackPolicy.detailLabel(
            PlaybackSubtitleTrack(selectionId = "0:0", mimeType = "text/vtt"),
        )
        assertTrue(detail.contains("WebVTT"))
    }
}
