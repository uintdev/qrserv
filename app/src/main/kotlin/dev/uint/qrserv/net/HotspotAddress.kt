package dev.uint.qrserv.net

data class Ipv4Candidate(
    val address: String,
    val isPrivate: Boolean,
    val endsInOne: Boolean,
    val interfaceName: String = "",
)

// On top of what linkKindOf counts as a VPN or mobile data: Wi-Fi Direct, 464XLAT and loopback.
private val NonHotspotInterfacePrefixes = listOf("p2p", "v4-", "lo")

// Unlike linkKindOf: an interface nothing reports is what a hotspot usually looks like.
private fun Ipv4Candidate.couldBeHotspot(reported: Map<String, LinkKind>): Boolean {
    val kind = reported[interfaceName]
    val hosted = if (kind != null) kind == LinkKind.HOSTED else linkKindOf(interfaceName, reported) == LinkKind.LOCAL
    return isPrivate && hosted && NonHotspotInterfacePrefixes.none { interfaceName.startsWith(it) }
}

/**
 * The public API doesn't report the hotspot's own address, so this compares addresses before and
 * after starting. Prefers a new private .1, then any new private address (the prefix is randomized),
 * then an existing 192.168.x.1 -- another app's already-running hotspot, shared with us. A network
 * [reported] as joined is never it, as a private share must not end up there.
 */
fun chooseHotspotAddress(
    before: Set<String>,
    now: List<Ipv4Candidate>,
    reported: Map<String, LinkKind> = emptyMap(),
): String? {
    val hosted = now.filter { it.couldBeHotspot(reported) }
    val appeared = hosted.filter { it.address !in before }
    return appeared.firstOrNull { it.endsInOne }?.address
        ?: appeared.firstOrNull()?.address
        ?: hosted.firstOrNull { it.endsInOne && it.address.startsWith("192.168.") }?.address
}

/** An address can appear before its network is reported. */
fun hotspotTurnedOutJoined(interfaceName: String?, reported: Map<String, LinkKind>): Boolean =
    interfaceName != null && reported[interfaceName] == LinkKind.LOCAL
