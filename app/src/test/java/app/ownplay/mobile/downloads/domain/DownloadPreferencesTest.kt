package app.ownplay.mobile.downloads.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPreferencesTest {
    @Test
    fun networkPolicyMirrorsUnmeteredPreference() {
        assertFalse(DownloadNetworkPreferencePolicy.requiresUnmeteredNetwork(DownloadPreferences()))
        assertTrue(
            DownloadNetworkPreferencePolicy.requiresUnmeteredNetwork(
                DownloadPreferences(unmeteredNetworkOnly = true),
            ),
        )
    }
}
