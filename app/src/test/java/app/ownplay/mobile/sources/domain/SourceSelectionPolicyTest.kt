package app.ownplay.mobile.sources.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceSelectionPolicyTest {
    @Test
    fun keepsPersistedEnabledSource() {
        val sources = listOf(
            source("a", enabled = true, updatedAt = 10),
            source("b", enabled = true, updatedAt = 20),
        )
        assertEquals("a", SourceSelectionPolicy.resolve("a", sources)?.sourceId)
    }

    @Test
    fun fallsBackDeterministicallyWhenPersistedSourceIsDisabled() {
        val sources = listOf(
            source("a", enabled = false, updatedAt = 30),
            source("b", enabled = true, updatedAt = 20),
            source("c", enabled = true, updatedAt = 10),
        )
        assertEquals("b", SourceSelectionPolicy.resolve("a", sources)?.sourceId)
    }

    @Test
    fun excludesRestoredSourceThatRequiresCredentials() {
        val sources = listOf(
            source("restored", enabled = true, updatedAt = 30, requiresCredentials = true),
            source("ready", enabled = true, updatedAt = 20),
        )

        assertEquals("ready", SourceSelectionPolicy.resolve("restored", sources)?.sourceId)
    }

    @Test
    fun returnsNullWhenNoSourceIsEnabled() {
        assertNull(SourceSelectionPolicy.resolve(null, listOf(source("a", false, 1))))
    }

    private fun source(
        id: String,
        enabled: Boolean,
        updatedAt: Long,
        requiresCredentials: Boolean = false,
    ) = Source(
        sourceId = id,
        displayName = id,
        type = SourceType.M3U,
        baseLocator = "https://example.test/list.m3u",
        enabled = enabled,
        createdAt = 1,
        updatedAt = updatedAt,
        requiresCredentials = requiresCredentials,
    )
}
