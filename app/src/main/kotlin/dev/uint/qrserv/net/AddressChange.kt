package dev.uint.qrserv.net

import dev.uint.qrserv.data.InterfaceAddress

sealed interface AddressChange {
    data class MoveTo(val address: InterfaceAddress) : AddressChange

    data class Stay(val suggestion: String?) : AddressChange
}

/** [listed] is in reachability order, so its first entry is the best address. */
fun addressChange(listed: List<InterfaceAddress>, selectedIp: String, addressesAtManualPick: Set<String>): AddressChange {
    val best = listed.firstOrNull() ?: return AddressChange.Stay(suggestion = null)
    if (listed.none { it.address == selectedIp }) return AddressChange.MoveTo(best)
    return AddressChange.Stay(best.address.takeIf { it != selectedIp && it !in addressesAtManualPick })
}
