package dev.uint.qrserv.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HotspotAddressTest {

    private fun ip(address: String, isPrivate: Boolean = true, interfaceName: String = "wlan2") =
        Ipv4Candidate(address, isPrivate, endsInOne = address.endsWith(".1"), interfaceName = interfaceName)

    private val wifi = ip("192.168.2.163", interfaceName = "wlan0")
    private val loopback = ip("127.0.0.1", isPrivate = false, interfaceName = "lo")

    @Test
    fun prefersNewGatewayAddress() {
        val before = setOf(wifi.address, loopback.address)
        val now = listOf(wifi, loopback, ip("10.114.7.23"), ip("192.168.61.1"))
        assertEquals("192.168.61.1", chooseHotspotAddress(before, now))
    }

    @Test
    fun acceptsNewAddressNotEndingInOne() {
        val before = setOf(wifi.address)
        val now = listOf(wifi, ip("172.20.14.77"))
        assertEquals("172.20.14.77", chooseHotspotAddress(before, now))
    }

    @Test
    fun ignoresExistingAddressesThatMerelyLookLikeGateways() {
        val before = setOf("10.0.0.1")
        val now = listOf(ip("10.0.0.1"), ip("192.168.44.1"))
        assertEquals("192.168.44.1", chooseHotspotAddress(before, now))
    }

    @Test
    fun fallsBackToSharedHotspotWhenNothingAppeared() {
        val shared = ip("192.168.43.1")
        val before = setOf(wifi.address, shared.address)
        assertNull(chooseHotspotAddress(before, listOf(wifi, shared)))
        assertEquals("192.168.43.1", chooseHotspotAddress(before, listOf(wifi, shared), lastChance = true))
    }

    @Test
    fun aVpnReconnectingDuringStartIsNotTheHotspot() {
        val before = setOf(wifi.address)
        val now = listOf(wifi, ip("10.2.0.2", interfaceName = "tun0"), ip("10.100.9.218"))
        assertEquals("10.100.9.218", chooseHotspotAddress(before, now))
        assertNull(chooseHotspotAddress(before, listOf(wifi, ip("10.2.0.2", interfaceName = "tun0"))))
    }

    @Test
    fun wireGuardIsNotTheHotspot() {
        val before = setOf(wifi.address)
        assertNull(chooseHotspotAddress(before, listOf(wifi, ip("10.66.66.2", interfaceName = "wg0"))))
    }

    @Test
    fun reportedTransportCatchesUnusuallyNamedInterfaces() {
        val before = setOf(wifi.address)
        val now = listOf(wifi, ip("10.40.2.17", interfaceName = "seth_lte0"))
        // Unknown by name, so only the system's report of the network gives it away.
        assertEquals("10.40.2.17", chooseHotspotAddress(before, now))
        assertNull(chooseHotspotAddress(before, now, reported = mapOf("seth_lte0" to LinkKind.CELLULAR)))
        assertNull(chooseHotspotAddress(before, now, reported = mapOf("seth_lte0" to LinkKind.VPN)))
        val hotspot = ip("192.168.61.1", interfaceName = "wlan2")
        assertEquals("192.168.61.1", chooseHotspotAddress(before, now + hotspot, reported = mapOf("seth_lte0" to LinkKind.CELLULAR)))
    }

    @Test
    fun wifiDirectGroupIsNotTheHotspot() {
        val direct = ip("192.168.49.1", interfaceName = "p2p-wlan0-0")
        assertNull(chooseHotspotAddress(setOf(wifi.address, direct.address), listOf(wifi, direct)))
        assertNull(chooseHotspotAddress(setOf(wifi.address), listOf(wifi, direct)))
    }

    @Test
    fun mobileDataIsNotTheHotspot() {
        val before = setOf(wifi.address)
        val now = listOf(wifi, ip("10.54.3.1", interfaceName = "rmnet_data0"), ip("192.0.0.4", interfaceName = "v4-rmnet_data0"))
        assertNull(chooseHotspotAddress(before, now))
        assertNull(chooseHotspotAddress(before, listOf(wifi, ip("10.71.4.9", interfaceName = "wwan0"), ip("10.12.0.5", interfaceName = "pdp0"))))
    }

    @Test
    fun aNewAddressOnAJoinedNetworkIsNotTheHotspot() {
        val before = setOf(wifi.address)
        val renewed = ip("192.168.2.200", interfaceName = "wlan0")
        val joined = mapOf("wlan0" to LinkKind.LOCAL)
        assertNull(chooseHotspotAddress(before, listOf(wifi, renewed), reported = joined))
        assertEquals("10.100.9.218", chooseHotspotAddress(before, listOf(renewed, ip("10.100.9.218")), reported = joined))
    }

    @Test
    fun anotherAppsHotspotIsFoundWhateverItsAddress() {
        val shared = ip("10.204.52.32", interfaceName = "wlan2")
        val before = setOf(wifi.address, shared.address)
        val reported = mapOf("wlan0" to LinkKind.LOCAL)
        assertNull(chooseHotspotAddress(before, listOf(wifi, shared), reported))
        assertEquals("10.204.52.32", chooseHotspotAddress(before, listOf(wifi, shared), reported, lastChance = true))
        val softAp = ip("172.18.3.9", interfaceName = "swlan0")
        assertEquals("172.18.3.9", chooseHotspotAddress(setOf(softAp.address), listOf(softAp), lastChance = true))
    }

    @Test
    fun otherTetheringIsNeverTakenForTheSharedHotspot() {
        val bluetooth = ip("192.168.44.1", interfaceName = "bt-pan")
        val usb = ip("192.168.42.1", interfaceName = "rndis0")
        val before = setOf(wifi.address, bluetooth.address, usb.address)
        val reported = mapOf("wlan0" to LinkKind.LOCAL)
        assertNull(chooseHotspotAddress(before, listOf(wifi, bluetooth, usb), reported, lastChance = true))
    }

    @Test
    fun aHotspotTheSystemReportsAsHostedIsAcceptedRightAway() {
        val now = listOf(wifi, ip("10.100.9.218", interfaceName = "wlan2"))
        val reported = mapOf("wlan0" to LinkKind.LOCAL, "wlan2" to LinkKind.HOSTED)
        assertEquals("10.100.9.218", chooseHotspotAddress(setOf(wifi.address), now, reported))
    }

    @Test
    fun aJoinedNetworkIsNeverTheHotspotEvenOnTheLastChance() {
        val renewed = ip("192.168.2.200", interfaceName = "wlan0")
        val reported = mapOf("wlan0" to LinkKind.LOCAL)
        assertNull(chooseHotspotAddress(setOf(wifi.address), listOf(renewed), reported))
        assertNull(chooseHotspotAddress(setOf(wifi.address), listOf(renewed), reported, lastChance = true))
    }

    @Test
    fun aRenewedLanAddressNeverBeatsTheSharedHotspot() {
        val shared = ip("10.204.52.32", interfaceName = "wlan2")
        val renewed = ip("192.168.2.200", interfaceName = "wlan0")
        val reported = mapOf("wlan0" to LinkKind.LOCAL)
        assertEquals("10.204.52.32", chooseHotspotAddress(setOf(wifi.address, shared.address), listOf(renewed, shared), reported, lastChance = true))
    }

    @Test
    fun aHotspotTakingOverTheWifiInterfaceIsAcceptedOnceTheNetworkIsGone() {
        val taken = ip("192.168.43.1", interfaceName = "wlan0")
        assertEquals("192.168.43.1", chooseHotspotAddress(setOf(wifi.address), listOf(taken), emptyMap()))
    }

    @Test
    fun anotherAppsHotspotIsFoundOnOtherInterfaceNames() {
        for (name in listOf("uap0", "ra0", "wl0.1", "wifi_ap", "ap0", "softap0")) {
            val shared = ip("10.1.2.3", interfaceName = name)
            assertEquals(name, "10.1.2.3", chooseHotspotAddress(setOf(shared.address), listOf(shared), lastChance = true))
        }
    }

    @Test
    fun aHotspotLaterReportedAsJoinedIsCaught() {
        assertTrue(hotspotTurnedOutJoined("wlan0", mapOf("wlan0" to LinkKind.LOCAL)))
    }

    @Test
    fun aHotspotStaysValidOtherwise() {
        assertFalse(hotspotTurnedOutJoined("wlan2", mapOf("wlan0" to LinkKind.LOCAL)))
        assertFalse(hotspotTurnedOutJoined("wlan2", mapOf("wlan2" to LinkKind.HOSTED)))
        assertFalse(hotspotTurnedOutJoined(null, mapOf("wlan0" to LinkKind.LOCAL)))
    }

    @Test
    fun nothingSuitable() {
        val before = setOf(wifi.address, loopback.address)
        assertNull(chooseHotspotAddress(before, listOf(wifi, loopback)))
    }

    @Test
    fun ignoresNewPublicAddresses() {
        val before = setOf(wifi.address)
        assertNull(chooseHotspotAddress(before, listOf(wifi, ip("203.0.113.1", isPrivate = false))))
    }
}
