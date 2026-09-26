package dev.uint.qrserv.net

data class Ipv4Candidate(
    val address: String,
    val isPrivate: Boolean,
    val endsInOne: Boolean,
    val interfaceName: String = "",
)

// VPN tunnels, Wi-Fi Direct groups, mobile data and loopback; the hotspot's own name varies by device.
private val NonHotspotInterfacePrefixes = listOf("tun", "ppp", "ipsec", "p2p", "rmnet", "ccmni", "v4-", "lo")

private fun Ipv4Candidate.couldBeHotspot(): Boolean =
    isPrivate && NonHotspotInterfacePrefixes.none { interfaceName.startsWith(it) }

/**
 * The public API doesn't report the hotspot's own address, so this compares addresses before and
 * after starting. Prefers a new private .1, then any new private address (the prefix is randomized),
 * then an existing 192.168.x.1 -- another app's already-running hotspot, shared with us.
 */
fun chooseHotspotAddress(before: Set<String>, now: List<Ipv4Candidate>): String? {
    val possible = now.filter { it.couldBeHotspot() }
    val appeared = possible.filter { it.address !in before }
    return appeared.firstOrNull { it.endsInOne }?.address
        ?: appeared.firstOrNull()?.address
        ?: possible.firstOrNull { it.endsInOne && it.address.startsWith("192.168.") }?.address
}
