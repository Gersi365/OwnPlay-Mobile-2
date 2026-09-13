package app.ownplay.mobile.sources.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderPayloadValidationTest {
    @Test fun malformedOnlyM3uIsRejectedButCleanEmptyIsAllowed() {
        assertEquals("M3U_PARSE_CONTENT", ProviderPayloadValidation.m3uFailureCode(0, 2, 0))
        assertNull(ProviderPayloadValidation.m3uFailureCode(0, 0, 0))
    }

    @Test fun m3uEntriesWithoutAnyResolvableLocatorAreRejected() {
        assertEquals("M3U_LOCATOR_CONTENT", ProviderPayloadValidation.m3uFailureCode(3, 0, 0))
        assertNull(ProviderPayloadValidation.m3uFailureCode(3, 1, 2))
    }

    @Test fun nonEmptyXtreamArrayWithNoUsableRowsIsRejected() {
        assertEquals("XTREAM_ARRAY_CONTENT", ProviderPayloadValidation.xtreamArrayFailureCode(4, 0))
        assertNull(ProviderPayloadValidation.xtreamArrayFailureCode(0, 0))
        assertNull(ProviderPayloadValidation.xtreamArrayFailureCode(4, 3))
    }

    @Test fun duplicateM3uLocatorsKeepFirstDeterministicRow() {
        val rows = listOf(
            "first" to "https://stream.test/a",
            "duplicate" to "https://stream.test/a",
            "second" to "https://stream.test/b",
        )
        assertEquals(
            listOf("first" to "https://stream.test/a", "second" to "https://stream.test/b"),
            ProviderPayloadValidation.distinctM3uByLocator(rows),
        )
    }
}
