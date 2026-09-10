package app.ownplay.mobile.feature.settings.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
