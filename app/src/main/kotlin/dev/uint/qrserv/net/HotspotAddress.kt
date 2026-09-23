package dev.uint.qrserv.net

data class Ipv4Candidate(val address: String, val isPrivate: Boolean, val endsInOne: Boolean)

/**
 * The public API doesn't report the hotspot's own address, so this compares addresses before and
 * after starting. Prefers a new private .1, then any new private address (the prefix is randomized),
 * then an existing 192.168.x.1 -- another app's already-running hotspot, shared with us.
 */
fun chooseHotspotAddress(before: Set<String>, now: List<Ipv4Candidate>): String? {
    val appeared = now.filter { it.isPrivate && it.address !in before }
    return appeared.firstOrNull { it.endsInOne }?.address
        ?: appeared.firstOrNull()?.address
        ?: now.firstOrNull { it.isPrivate && it.endsInOne && it.address.startsWith("192.168.") }?.address
}
