package dev.uint.qrserv.net

import android.content.Context
import android.provider.Settings
import dev.uint.qrserv.data.AddressGroup
import dev.uint.qrserv.data.InterfaceAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket

enum class LinkKind { LOCAL, VPN, CELLULAR }

private val VpnInterfacePrefixes = listOf("tun", "ppp", "ipsec", "wg")
private val CellularInterfacePrefixes = listOf("rmnet", "ccmni", "wwan", "pdp")

/** A "v4-" interface is the IPv4 translation stacked on another interface, so it takes that one's kind. */
fun linkKindOf(interfaceName: String, reported: Map<String, LinkKind>): LinkKind {
    val base = interfaceName.removePrefix("v4-")
    return reported[base] ?: when {
        VpnInterfacePrefixes.any { base.startsWith(it) } -> LinkKind.VPN
        CellularInterfacePrefixes.any { base.startsWith(it) } -> LinkKind.CELLULAR
        else -> LinkKind.LOCAL
    }
}

object NetworkUtils {

    /**
     * Returns the local addresses this device could be reached at, ordered by how likely a client
     * on another device is to actually get through: a network this device joined first, then a
     * network it hosts itself, then link-local, then loopback. Nothing is dropped -- unlikely is
     * not impossible, and on a device where one of the lower groups is all there is, it is the
     * only thing that could work. Within a group, Wi-Fi or Ethernet comes before a VPN, then mobile
     * data; after that IPv4 comes before IPv6 and each sorts numerically.
     */
    suspend fun listInterfaces(reported: Map<String, LinkKind> = emptyMap()): Result<List<InterfaceAddress>> = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<Candidate>()
        var failure: Exception? = null

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val kind = linkKindOf(networkInterface.name, reported)
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    candidateOf(addresses.nextElement(), kind)?.let(candidates::add)
                }
            }
        } catch (error: Exception) {
            failure = error
        }

        val addresses = rank(candidates)
        // Whatever was collected before the failure still stands -- a partial list is a usable one.
        // Only a failure that produced nothing is worth reporting as a failure at all.
        if (addresses.isEmpty() && failure != null) Result.failure(failure) else Result.success(addresses)
    }

    internal class Candidate(
        val address: String,
        val bindHost: String,
        val group: AddressGroup,
        val kind: LinkKind,
        val isIpv6: Boolean,
        val key: String,
    )

    internal fun candidateOf(addr: InetAddress, kind: LinkKind): Candidate? {
        val host = addr.hostAddress ?: return null
        return when (addr) {
            is Inet4Address -> Candidate(host, host, groupOf(addr, selfHosted(addr)), kind, isIpv6 = false, key = sortKey(addr))
            // The scope id (e.g. "fe80::1%wlan0") names an interface on this device and carries no
            // meaning on another one, so it is dropped from what gets shown and put in the URL.
            is Inet6Address -> Candidate(host.substringBefore('%'), host, groupOf(addr, false), kind, isIpv6 = true, key = sortKey(addr))
            else -> null
        }
    }

    internal fun rank(candidates: List<Candidate>): List<InterfaceAddress> =
        candidates.sortedWith(ByReachability).map { InterfaceAddress(it.address, it.group, it.bindHost) }

    private val ByReachability =
        compareBy<Candidate>({ it.group }, { it.kind }, { it.isIpv6 })
            // IPv4 descending so a 192.168 address outranks a 10.x one, IPv6 ascending so a global
            // address outranks a unique-local one -- each as it was before grouping existed.
            .thenComparator { a, b -> if (a.isIpv6) a.key.compareTo(b.key) else b.key.compareTo(a.key) }

    /**
     * This device's own Wi-Fi hotspot / Wi-Fi Direct group-owner address (e.g. 192.168.43.1,
     * 192.168.49.1) rather than one-handed to it by another network's DHCP server.
     */
    private fun selfHosted(addr: Inet4Address): Boolean {
        val raw = addr.address
        return raw.size == 4 &&
            (raw[0].toInt() and 0xFF) == 192 &&
            (raw[1].toInt() and 0xFF) == 168 &&
            (raw[3].toInt() and 0xFF) < 2
    }

    private fun groupOf(addr: InetAddress, selfHosted: Boolean): AddressGroup = when {
        // Reaches nothing but this device.
        addr.isLoopbackAddress -> AddressGroup.LOOPBACK
        // IPv4 (169.254/16) needs the client to have fallen back on the same link; IPv6
        // (fe80::/10) needs that and a zone index, which has to be the client's own -- so there is
        // nothing this end can put in the URL to make one resolvable.
        addr.isLinkLocalAddress -> AddressGroup.LINK_LOCAL
        selfHosted -> AddressGroup.HOSTED
        else -> AddressGroup.ROUTABLE
    }

    /**
     * Every byte as fixed-width hex, so comparing two keys as strings compares the addresses
     * numerically. Comparing display forms would not: IPv4 is decimal, and hostAddress leaves IPv6
     * groups unpadded, putting "...:78:..." before "...:9:..." though 0x78 is the larger.
     */
    private fun sortKey(addr: InetAddress): String =
        addr.address.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    fun urlHost(ip: String): String = if (ip.contains(':')) "[$ip]" else ip

    fun isVpnLockdownEnabled(context: Context): Boolean = runCatching {
        Settings.Secure.getInt(context.contentResolver, "always_on_vpn_lockdown", 0) == 1
    }.getOrDefault(false)

    suspend fun isPortUsed(port: Int): Boolean = withContext(Dispatchers.IO) {
        if (port <= 0) return@withContext false
        try {
            ServerSocket(port).use { }
            false
        } catch (_: Exception) {
            true
        }
    }
}
