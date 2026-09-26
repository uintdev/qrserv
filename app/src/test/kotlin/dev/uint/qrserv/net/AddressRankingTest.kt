package dev.uint.qrserv.net

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress

class AddressRankingTest {

    private fun ranked(vararg entries: Pair<String, LinkKind>): List<String> =
        NetworkUtils.rank(entries.mapNotNull { (ip, kind) -> NetworkUtils.candidateOf(InetAddress.getByName(ip), kind) })
            .map { it.address }

    @Test
    fun wifiOnATenNetworkOutranksAVpn() {
        assertEquals(
            listOf("10.0.0.5", "10.2.0.2"),
            ranked("10.2.0.2" to LinkKind.VPN, "10.0.0.5" to LinkKind.LOCAL),
        )
    }

    @Test
    fun wifiOutranksMobileData() {
        assertEquals(
            listOf("10.0.0.5", "100.64.3.9"),
            ranked("100.64.3.9" to LinkKind.CELLULAR, "10.0.0.5" to LinkKind.LOCAL),
        )
    }

    @Test
    fun vpnOutranksMobileData() {
        assertEquals(
            listOf("10.2.0.2", "100.64.3.9"),
            ranked("100.64.3.9" to LinkKind.CELLULAR, "10.2.0.2" to LinkKind.VPN),
        )
    }

    @Test
    fun wifiIpv6OutranksVpnIpv4() {
        assertEquals(
            listOf("192.168.2.95", "2a0e:cb01:78:9d00:19a8:a9dd:5a07:a5f3", "10.2.0.2"),
            ranked("10.2.0.2" to LinkKind.VPN, "2a0e:cb01:78:9d00:19a8:a9dd:5a07:a5f3" to LinkKind.LOCAL, "192.168.2.95" to LinkKind.LOCAL),
        )
    }

    @Test
    fun sameKindKeepsNumericOrder() {
        assertEquals(
            listOf("192.168.2.95", "172.16.0.4", "10.0.0.5"),
            ranked("10.0.0.5" to LinkKind.LOCAL, "172.16.0.4" to LinkKind.LOCAL, "192.168.2.95" to LinkKind.LOCAL),
        )
    }

    @Test
    fun groupStillComesBeforeKind() {
        assertEquals(
            listOf("10.2.0.2", "127.0.0.1"),
            ranked("127.0.0.1" to LinkKind.LOCAL, "10.2.0.2" to LinkKind.VPN),
        )
    }

    @Test
    fun reportedKindWinsOverTheName() {
        assertEquals(LinkKind.LOCAL, linkKindOf("tun0", mapOf("tun0" to LinkKind.LOCAL)))
        assertEquals(LinkKind.VPN, linkKindOf("wlan1", mapOf("wlan1" to LinkKind.VPN)))
    }

    @Test
    fun nameIsTheFallback() {
        assertEquals(LinkKind.VPN, linkKindOf("tun0", emptyMap()))
        assertEquals(LinkKind.VPN, linkKindOf("wg0", emptyMap()))
        assertEquals(LinkKind.CELLULAR, linkKindOf("rmnet_data2", emptyMap()))
        assertEquals(LinkKind.CELLULAR, linkKindOf("ccmni1", emptyMap()))
        assertEquals(LinkKind.LOCAL, linkKindOf("wlan0", emptyMap()))
        assertEquals(LinkKind.LOCAL, linkKindOf("eth0", emptyMap()))
    }

    @Test
    fun stackedIpv4TakesItsBaseInterfacesKind() {
        assertEquals(LinkKind.LOCAL, linkKindOf("v4-wlan0", emptyMap()))
        assertEquals(LinkKind.CELLULAR, linkKindOf("v4-rmnet_data0", emptyMap()))
        assertEquals(LinkKind.CELLULAR, linkKindOf("v4-wlan0", mapOf("wlan0" to LinkKind.CELLULAR)))
    }
}
