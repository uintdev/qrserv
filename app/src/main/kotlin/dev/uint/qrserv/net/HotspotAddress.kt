package dev.uint.qrserv.net

data class Ipv4Candidate(
    val address: String,
    val isPrivate: Boolean,
    val endsInOne: Boolean,
    val interfaceName: String = "",
)

// On top of what linkKindOf counts as a VPN or mobile data: Wi-Fi Direct, 464XLAT and loopback.
private val NonHotspotInterfacePrefixes = listOf("p2p", "v4-", "lo")

private fun Ipv4Candidate.couldBeHotspot(reported: Map<String, LinkKind>): Boolean =
    isPrivate && linkKindOf(interfaceName, reported) == LinkKind.LOCAL &&
        NonHotspotInterfacePrefixes.none { interfaceName.startsWith(it) }

/**
 * The public API doesn't report the hotspot's own address, so this compares addresses before and
 * after starting. Prefers a new private .1, then any new private address (the prefix is randomized),
 * then an existing 192.168.x.1 -- another app's already-running hotspot, shared with us. [reported]
 * is each interface's transport as the system reports it, to rule out VPNs and mobile data whatever
 * they're called.
 */
fun chooseHotspotAddress(
    before: Set<String>,
    now: List<Ipv4Candidate>,
    reported: Map<String, LinkKind> = emptyMap(),
): String? {
    val possible = now.filter { it.couldBeHotspot(reported) }
    val appeared = possible.filter { it.address !in before }
    return appeared.firstOrNull { it.endsInOne }?.address
        ?: appeared.firstOrNull()?.address
        ?: possible.firstOrNull { it.endsInOne && it.address.startsWith("192.168.") }?.address
}
