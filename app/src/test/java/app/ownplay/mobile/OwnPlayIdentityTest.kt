package app.ownplay.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class OwnPlayIdentityTest {
    @Test
    fun productIdentityMatchesProjectBaseline() {
        assertEquals("OwnPlay", OwnPlayIdentity.PRODUCT_NAME)
        assertEquals("app.ownplay.mobile", OwnPlayIdentity.APPLICATION_ID)
    }
}
