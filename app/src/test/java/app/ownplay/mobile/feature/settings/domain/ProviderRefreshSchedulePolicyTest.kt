package app.ownplay.mobile.feature.settings.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRefreshSchedulePolicyTest {
    @Test
    fun `default settings schedule six hour provider refresh`() {
        assertEquals(6L, ProviderRefreshSchedulePolicy.intervalHours(SettingsSnapshot()))
    }

    @Test
    fun `disabled auto refresh cancels periodic work`() {
        assertNull(
            ProviderRefreshSchedulePolicy.intervalHours(
                SettingsSnapshot(autoRefreshProviders = false),
            ),
        )
    }

    @Test
    fun `selected interval controls periodic cadence`() {
        assertEquals(
            24L,
            ProviderRefreshSchedulePolicy.intervalHours(
                SettingsSnapshot(providerRefreshInterval = ProviderRefreshInterval.DAILY),
            ),
        )
    }

    @Test
    fun `transient provider refresh failures request work retry`() {
        assertTrue(ProviderRefreshRetryPolicy.shouldRetry("REFRESH_NETWORK"))
        assertTrue(ProviderRefreshRetryPolicy.shouldRetry("REFRESH_TIMEOUT"))
        assertTrue(ProviderRefreshRetryPolicy.shouldRetry("REFRESH_HTTP_429"))
        assertTrue(ProviderRefreshRetryPolicy.shouldRetry("REFRESH_PROVIDER_HTTP"))
    }

    @Test
    fun `configuration refresh failures do not loop work retry`() {
        assertFalse(ProviderRefreshRetryPolicy.shouldRetry("REFRESH_AUTH"))
        assertFalse(ProviderRefreshRetryPolicy.shouldRetry("REFRESH_INVALID_URL"))
        assertFalse(ProviderRefreshRetryPolicy.shouldRetry("REFRESH_M3U_FORMAT"))
    }
}
