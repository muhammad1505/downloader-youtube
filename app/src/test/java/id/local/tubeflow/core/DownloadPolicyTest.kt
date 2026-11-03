package id.local.tubeflow.core

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPolicyTest {

    @Test
    fun defaultKeepsConnectedNetworkAndNoChargingRequirement() {
        val constraints = DownloadPolicy.constraints(wifiOnly = false, chargingOnly = false)

        assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)
        assertFalse(constraints.requiresCharging())
    }

    @Test
    fun wifiOnlyUsesUnmeteredNetwork() {
        assertEquals(NetworkType.UNMETERED, DownloadPolicy.networkType(wifiOnly = true))
        assertEquals(NetworkType.UNMETERED, DownloadPolicy.constraints(wifiOnly = true, chargingOnly = false).requiredNetworkType)
    }

    @Test
    fun chargingRequirementIsIndependentFromNetworkChoice() {
        val constraints = DownloadPolicy.constraints(wifiOnly = false, chargingOnly = true)

        assertTrue(constraints.requiresCharging())
        assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)
    }
}
