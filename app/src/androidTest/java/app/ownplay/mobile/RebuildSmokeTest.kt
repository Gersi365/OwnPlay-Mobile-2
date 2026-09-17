package app.ownplay.mobile

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class RebuildSmokeTest {
    @Test
    fun applicationPackageIsStable() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("app.ownplay.mobile", context.packageName)
    }
}
