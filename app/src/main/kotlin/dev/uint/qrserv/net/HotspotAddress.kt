package dev.uint.qrserv.net

data class Ipv4Candidate(
    val address: String,
    val isPrivate: Boolean,
    val endsInOne: Boolean,
    val interfaceName: String = "",
)

// On top of what linkKindOf counts as a VPN or mobile data: Wi-Fi Direct, 464XLAT and loopback.
private val NonHotspotInterfacePrefixes = listOf("p2p", "v4-", "lo")

// Where another app's hotspot can be; rules out Bluetooth and USB tethering.
private val WifiInterfacePrefixes = listOf("wl", "swlan", "ap", "softap", "uap", "ra", "wifi")

private val Ipv4Candidate.onWifiInterface: Boolean
    get() = WifiInterfacePrefixes.any { interfaceName.startsWith(it) }

// Unlike linkKindOf: an interface nothing reports is what a hotspot usually looks like.
private fun Ipv4Candidate.couldBeHotspot(reported: Map<String, LinkKind>): Boolean {
    val kind = reported[interfaceName]
    val hosted = if (kind != null) kind == LinkKind.HOSTED else linkKindOf(interfaceName, reported) == LinkKind.LOCAL
    return isPrivate && hosted && NonHotspotInterfacePrefixes.none { interfaceName.startsWith(it) }
}

/**
 * The public API doesn't report the hotspot's own address, so this compares addresses before and
 * after starting. Prefers a new private .1, then any new private address (the prefix is randomized).
 * A network [reported] as joined is never it, as a private share must not end up there. [lastChance]
 * also accepts an existing address on a Wi-Fi interface: another app's hotspot, shared with us.
 */
fun chooseHotspotAddress(
    before: Set<String>,
    now: List<Ipv4Candidate>,
    reported: Map<String, LinkKind> = emptyMap(),
    lastChance: Boolean = false,
): String? {
    val hosted = now.filter { it.couldBeHotspot(reported) }
    val appeared = hosted.filter { it.address !in before }
    return appeared.firstOrNull { it.endsInOne }?.address
        ?: appeared.firstOrNull()?.address
        ?: if (lastChance) sharedHotspot(hosted) else null
}

private fun sharedHotspot(hosted: List<Ipv4Candidate>): String? {
    val onWifi = hosted.filter { it.onWifiInterface }
    return (onWifi.firstOrNull { it.endsInOne } ?: onWifi.singleOrNull())?.address
}

/** An address can appear before its network is reported. */
fun hotspotTurnedOutJoined(interfaceName: String?, reported: Map<String, LinkKind>): Boolean =
    interfaceName != null && reported[interfaceName] == LinkKind.LOCAL
