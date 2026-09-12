package dev.uint.qrserv.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface
import java.net.ServerSocket

object NetworkUtils {

    /**
     * Returns the list of local addresses this device could be reached at,
     * IPv4 addresses first (reverse-sorted), then IPv6.
     */
    suspend fun listInterfaces(): List<String> = withContext(Dispatchers.IO) {
        val ipv4 = mutableListOf<String>()
        val ipv6 = mutableListOf<String>()

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    when (val addr = addresses.nextElement()) {
                        is Inet4Address -> {
                            val raw = addr.address
                            val isFilteredLocal =
                                raw.size == 4 &&
                                    (raw[0].toInt() and 0xFF) == 192 &&
                                    (raw[1].toInt() and 0xFF) == 168 &&
                                    (raw[3].toInt() and 0xFF) < 2
                            if (!isFilteredLocal) ipv4.add(addr.hostAddress ?: continue)
                        }
                        is Inet6Address -> {
                            val host = addr.hostAddress ?: continue
                            // Strip the scope id (e.g. "fe80::1%wlan0" -> "fe80::1")
                            ipv6.add(host.substringBefore('%'))
                        }
                        else -> Unit
                    }
                }
            }
        } catch (_: Exception) {
            // Leave whatever was collected so far; caller treats empty list as "no connection".
        }

        ipv4.sort()
        ipv6.sort()

        buildList {
            addAll(ipv4.asReversed())
            addAll(ipv6)
        }
    }

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
