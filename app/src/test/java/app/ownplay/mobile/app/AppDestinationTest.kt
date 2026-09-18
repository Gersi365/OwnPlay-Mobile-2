package app.ownplay.mobile.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AppDestinationTest {
    @Test
    fun `primary navigation is live library settings`() {
        assertEquals(
            listOf("Live", "Library", "Settings"),
            AppDestination.entries.map { it.label },
        )
    }
}
