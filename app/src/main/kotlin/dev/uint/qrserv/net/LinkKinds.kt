package dev.uint.qrserv.net

import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import java.util.concurrent.ConcurrentHashMap

/** Each network's interface and kind, as a NetworkCallback reports them. */
class LinkKinds {
    private val names = ConcurrentHashMap<Network, String>()
    private val kinds = ConcurrentHashMap<Network, LinkKind>()

    fun update(network: Network, capabilities: NetworkCapabilities) {
        kinds[network] = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> LinkKind.VPN
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> LinkKind.CELLULAR
            else -> LinkKind.LOCAL
        }
    }

    fun update(network: Network, linkProperties: LinkProperties) {
        val name = linkProperties.interfaceName
        if (name != null) names[network] = name else names.remove(network)
    }

    fun remove(network: Network) {
        names.remove(network)
        kinds.remove(network)
    }

    fun byInterface(): Map<String, LinkKind> =
        names.entries.mapNotNull { (network, name) -> kinds[network]?.let { name to it } }.toMap()
}
