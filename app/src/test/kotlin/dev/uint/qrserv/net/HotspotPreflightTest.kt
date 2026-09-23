package dev.uint.qrserv.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HotspotPreflightTest {

    @Test
    fun unavailableReasonsInPriorityOrder() {
        assertEquals(HotspotUnavailable.REQUIRES_ANDROID_13, hotspotUnavailableReason(30, hasWifi = false, tetheringRestricted = true))
        assertEquals(HotspotUnavailable.NO_WIFI, hotspotUnavailableReason(33, hasWifi = false, tetheringRestricted = true))
        assertEquals(HotspotUnavailable.BLOCKED, hotspotUnavailableReason(36, hasWifi = true, tetheringRestricted = true))
        assertNull(hotspotUnavailableReason(33, hasWifi = true, tetheringRestricted = false))
    }

    @Test
    fun unavailableWinsOverEverything() {
        assertEquals(
            HotspotPreflight.Unavailable(HotspotUnavailable.BLOCKED),
            hotspotPreflight(HotspotUnavailable.BLOCKED, wifiControlAllowed = false, nearbyGranted = false),
        )
    }

    @Test
    fun wifiControlCheckedBeforeNearby() {
        assertEquals(HotspotPreflight.NeedsWifiControl, hotspotPreflight(null, wifiControlAllowed = false, nearbyGranted = false))
        assertEquals(HotspotPreflight.NeedsNearby, hotspotPreflight(null, wifiControlAllowed = true, nearbyGranted = false))
    }

    @Test
    fun readyWhenEverythingIsInPlace() {
        assertEquals(HotspotPreflight.Ready, hotspotPreflight(null, wifiControlAllowed = true, nearbyGranted = true))
    }
}
