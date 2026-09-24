package dev.uint.qrserv.net

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.SoftApConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

data class HotspotInfo(
    val ssid: String,
    val passphrase: String,
    val security: HotspotSecurity,
    val securityName: String?,
    val address: String,
)

enum class HotspotFailure {
    PERMISSION_DENIED,

    WIFI_CONTROL_DENIED,

    INCOMPATIBLE_MODE,

    TETHERING_DISALLOWED,

    NO_CHANNEL,

    NO_ADDRESS,

    GENERIC,
}

sealed interface HotspotStartResult {
    data class Started(val info: HotspotInfo) : HotspotStartResult
    data class Failed(val reason: HotspotFailure) : HotspotStartResult
}

enum class HotspotLoss {
    STOPPED,
    WIFI_CONTROL_REVOKED,
}

class HotspotController(
    context: Context,
    private val onLost: (HotspotLoss) -> Unit,
) {
    private val context = context.applicationContext
    private val wifiManager = this.context.getSystemService(WifiManager::class.java)
    private val appOps = this.context.getSystemService(AppOpsManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var callback: WifiManager.LocalOnlyHotspotCallback? = null
    private var appOpWatcher: AppOpsManager.OnOpChangedListener? = null

    var info: HotspotInfo? = null
        private set

    val isActive: Boolean
        get() = reservation != null

    /** Must be called while QRServ is in the foreground; the system refuses otherwise. */
    suspend fun start(): HotspotStartResult {
        info?.let { if (reservation != null) return HotspotStartResult.Started(it) }
        if (Build.VERSION.SDK_INT < 33) return HotspotStartResult.Failed(HotspotFailure.GENERIC)

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return HotspotStartResult.Failed(HotspotFailure.PERMISSION_DENIED)
        }
        // Checked up front: the system would only report it as ERROR_GENERIC.
        if (!isWifiControlAllowed()) return HotspotStartResult.Failed(HotspotFailure.WIFI_CONTROL_DENIED)

        val before = withContext(Dispatchers.IO) { ipv4Candidates() }.mapTo(HashSet()) { it.address }

        val newReservation = when (val started = requestReservation()) {
            is Requested.Started -> started.reservation
            is Requested.Failed -> return HotspotStartResult.Failed(started.reason)
        }
        reservation = newReservation

        val address = awaitAddress(before)
        if (address == null) {
            stop()
            return HotspotStartResult.Failed(HotspotFailure.NO_ADDRESS)
        }

        val config = newReservation.softApConfiguration
        val newInfo = HotspotInfo(
            ssid = ssidOf(config),
            passphrase = config.passphrase.orEmpty(),
            security = securityOf(config.securityType),
            securityName = securityNameOf(config.securityType),
            address = address,
        )
        info = newInfo
        watchWifiControl()
        return HotspotStartResult.Started(newInfo)
    }

    fun stop() {
        appOpWatcher?.let { appOps.stopWatchingMode(it) }
        appOpWatcher = null
        // Cleared before closing, so an onStopped() the close triggers is recognized as ours.
        val closing = reservation
        reservation = null
        callback = null
        info = null
        // Only ever non-null on 33+; the check is for lint.
        if (Build.VERSION.SDK_INT >= 26) closing?.close()
    }

    fun recheck() {
        if (reservation != null && !isWifiControlAllowed()) loseTo(HotspotLoss.WIFI_CONTROL_REVOKED)
    }

    private sealed interface Requested {
        data class Started(val reservation: WifiManager.LocalOnlyHotspotReservation) : Requested
        data class Failed(val reason: HotspotFailure) : Requested
    }

    @RequiresApi(33)
    private suspend fun requestReservation(): Requested = suspendCancellableCoroutine { continuation ->
        val cb = object : WifiManager.LocalOnlyHotspotCallback() {
            override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                if (continuation.isActive) {
                    continuation.resume(Requested.Started(reservation))
                } else {
                    // The caller was canceled -- nothing else would close it.
                    reservation.close()
                }
            }

            override fun onFailed(reason: Int) {
                if (continuation.isActive) {
                    continuation.resume(Requested.Failed(failureOf(reason)))
                } else if (callback === this) {
                    loseTo(HotspotLoss.STOPPED)
                }
            }

            override fun onStopped() {
                if (continuation.isActive) {
                    continuation.resume(Requested.Failed(HotspotFailure.GENERIC))
                } else if (callback === this) {
                    loseTo(HotspotLoss.STOPPED)
                }
            }
        }
        callback = cb
        try {
            wifiManager.startLocalOnlyHotspot(cb, mainHandler)
        } catch (_: SecurityException) {
            callback = null
            continuation.resume(Requested.Failed(HotspotFailure.PERMISSION_DENIED))
        } catch (_: IllegalStateException) {
            callback = null
            continuation.resume(Requested.Failed(HotspotFailure.GENERIC))
        }
    }

    // The address can appear a moment after onStarted().
    private suspend fun awaitAddress(before: Set<String>): String? = withContext(Dispatchers.IO) {
        repeat(ADDRESS_POLL_ATTEMPTS) {
            chooseHotspotAddress(before, ipv4Candidates())?.let { return@withContext it }
            delay(ADDRESS_POLL_INTERVAL_MS.milliseconds)
        }
        null
    }

    private fun watchWifiControl() {
        val watcher = AppOpsManager.OnOpChangedListener { _, packageName ->
            if (packageName == context.packageName) {
                mainHandler.post { recheck() }
            }
        }
        appOps.startWatchingMode(OP_CHANGE_WIFI_STATE, context.packageName, watcher)
        appOpWatcher = watcher
    }

    fun isWifiControlAllowed(): Boolean {
        if (Build.VERSION.SDK_INT < 29) return true
        @Suppress("DEPRECATION") // Its replacement is flag-gated; this one is on every 33+ device.
        val mode = appOps.unsafeCheckOpNoThrow(OP_CHANGE_WIFI_STATE, Process.myUid(), context.packageName)
        return mode != AppOpsManager.MODE_IGNORED && mode != AppOpsManager.MODE_ERRORED
    }

    private fun loseTo(loss: HotspotLoss) {
        if (reservation == null) return
        stop()
        onLost(loss)
    }

    private fun ipv4Candidates(): List<Ipv4Candidate> = buildList {
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull() ?: return@buildList
        for (networkInterface in interfaces) {
            for (addr in networkInterface.inetAddresses) {
                if (addr !is Inet4Address) continue
                val host = addr.hostAddress ?: continue
                add(Ipv4Candidate(host, isPrivate = addr.isSiteLocalAddress, endsInOne = addr.address[3].toInt() == 1))
            }
        }
    }

    @RequiresApi(33)
    private fun ssidOf(config: SoftApConfiguration): String {
        // WifiSsid.toString() quotes a UTF-8 SSID.
        val raw = config.wifiSsid?.toString().orEmpty()
        return if (raw.length >= 2 && raw.startsWith('"') && raw.endsWith('"')) raw.substring(1, raw.length - 1) else raw
    }

    @RequiresApi(30)
    private fun securityOf(type: Int): HotspotSecurity = when (type) {
        SoftApConfiguration.SECURITY_TYPE_OPEN,
        SoftApConfiguration.SECURITY_TYPE_WPA3_OWE,
        SoftApConfiguration.SECURITY_TYPE_WPA3_OWE_TRANSITION,
        -> HotspotSecurity.OPEN
        SoftApConfiguration.SECURITY_TYPE_WPA3_SAE -> HotspotSecurity.SAE
        else -> HotspotSecurity.WPA
    }

    @RequiresApi(30)
    private fun securityNameOf(type: Int): String? = when (type) {
        SoftApConfiguration.SECURITY_TYPE_WPA2_PSK -> "WPA2"
        SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION -> "WPA2/WPA3"
        SoftApConfiguration.SECURITY_TYPE_WPA3_SAE -> "WPA3"
        SoftApConfiguration.SECURITY_TYPE_WPA3_OWE_TRANSITION,
        SoftApConfiguration.SECURITY_TYPE_WPA3_OWE,
        -> "OWE"
        else -> null
    }

    private fun failureOf(reason: Int): HotspotFailure = when (reason) {
        WifiManager.LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE -> HotspotFailure.INCOMPATIBLE_MODE
        WifiManager.LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED -> HotspotFailure.TETHERING_DISALLOWED
        WifiManager.LocalOnlyHotspotCallback.ERROR_NO_CHANNEL -> HotspotFailure.NO_CHANNEL
        else -> HotspotFailure.GENERIC
    }

    private companion object {
        /** "Wi-Fi control"; not a public constant. */
        const val OP_CHANGE_WIFI_STATE = "android:change_wifi_state"
        const val ADDRESS_POLL_ATTEMPTS = 25
        const val ADDRESS_POLL_INTERVAL_MS = 200L
    }
}
