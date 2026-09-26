package dev.uint.qrserv.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals("192.168.43.1", chooseHotspotAddress(before, listOf(wifi, shared)))
    }

    @Test
    fun aVpnReconnectingDuringStartIsNotTheHotspot() {
        val before = setOf(wifi.address)
        val now = listOf(wifi, ip("10.2.0.2", interfaceName = "tun0"), ip("10.100.9.218"))
        assertEquals("10.100.9.218", chooseHotspotAddress(before, now))
        assertNull(chooseHotspotAddress(before, listOf(wifi, ip("10.2.0.2", interfaceName = "tun0"))))
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
