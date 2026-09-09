package app.ownplay.mobile.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AppDestinationTest {
    @Test
    fun primaryDestinationsStayInApprovedOrder() {
        assertEquals(
            listOf("Live", "Library", "Settings"),
            primaryDestinations.map(AppDestination::label),
        )
    }
}
