package app.ownplay.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class BootstrapContractTest {
    @Test
    fun applicationIdMatchesApprovedBaseline() {
        assertEquals("app.ownplay.mobile", BuildConfig.APPLICATION_ID)
    }
}
