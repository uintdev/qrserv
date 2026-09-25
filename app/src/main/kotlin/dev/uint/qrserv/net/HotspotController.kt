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
import android.util.SparseIntArray
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import dev.uint.qrserv.R
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
    val band: HotspotBand,
    /** What a restart on the fast bands would get; null where the band can't be requested. */
    val fasterBand: HotspotBand?,
)

enum class HotspotBand {
    /** 2.4 and 5 GHz at once, under one network. */
    DUAL,

    FIVE_GHZ,

    /** Also the answer when the band can't be told. */
    TWO_GHZ,
}

val HotspotBand.labelRes: Int
    get() = when (this) {
        HotspotBand.DUAL -> R.string.hotspot_band_dual
        HotspotBand.FIVE_GHZ -> R.string.hotspot_band_5ghz
        HotspotBand.TWO_GHZ -> R.string.hotspot_band_2ghz
    }

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

    /** Bands the hotspot can be asked for, fastest first; empty where the band can't be requested. */
    val selectableBands: List<HotspotBand>
        get() = when {
            Build.VERSION.SDK_INT < 36 || !wifiManager.is5GHzBandSupported -> emptyList()
            wifiManager.isBridgedApConcurrencySupported -> listOf(HotspotBand.DUAL, HotspotBand.FIVE_GHZ, HotspotBand.TWO_GHZ)
            else -> listOf(HotspotBand.FIVE_GHZ, HotspotBand.TWO_GHZ)
        }

    /** True where [selectableBands] is empty only because this Android version can't request a band. */
    val bandChoiceNeedsNewerAndroid: Boolean
        get() = Build.VERSION.SDK_INT < 36 && wifiManager.is5GHzBandSupported

    /**
     * Must be called while QRServ is in the foreground; the system refuses otherwise.
     * [band] null asks for the fastest one available.
     */
    suspend fun start(band: HotspotBand? = null): HotspotStartResult {
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

        val newReservation = when (val started = requestReservationFor(band ?: selectableBands.firstOrNull())) {
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
            band = bandOf(config),
            fasterBand = selectableBands.firstOrNull(),
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

    // The default local-only hotspot is 2.4 GHz at 20 MHz. From 36, apps may pick the band, and 5 GHz is
    // several times faster -- but 2.4 GHz-only clients can't see it, and it can be refused (no usable
    // channel, regulatory, a concurrent Wi-Fi connection). So: both bands where the device can run them
    // together, else 5 GHz, else the default. A preferred band that's refused falls back the same way.
    @RequiresApi(33)
    private suspend fun requestReservationFor(band: HotspotBand?): Requested {
        if (Build.VERSION.SDK_INT >= 36 && band != null && band in selectableBands) {
            val attempts = when (band) {
                HotspotBand.DUAL -> listOf(
                    bands(SoftApConfiguration.BAND_2GHZ, SoftApConfiguration.BAND_5GHZ),
                    bands(SoftApConfiguration.BAND_5GHZ),
                )
                HotspotBand.FIVE_GHZ -> listOf(bands(SoftApConfiguration.BAND_5GHZ))
                HotspotBand.TWO_GHZ -> listOf(bands(SoftApConfiguration.BAND_2GHZ))
            }
            for (channels in attempts) {
                requestConfigured(channels)?.let { return it }
            }
        }
        return requestReservation { cb -> wifiManager.startLocalOnlyHotspot(cb, mainHandler) }
    }

    @RequiresApi(36)
    private suspend fun requestConfigured(channels: SparseIntArray): Requested.Started? {
        val config = SoftApConfiguration.Builder().setChannels(channels).build()
        val started = requestReservation { cb ->
            wifiManager.startLocalOnlyHotspotWithConfiguration(config, context.mainExecutor, cb)
        } as? Requested.Started ?: return null
        // Apps can't set a passphrase before 37, so make sure the framework still generated one.
        if (isPassphraseProtected(started.reservation.softApConfiguration)) return started
        callback = null
        started.reservation.close()
        return null
    }

    // Channel 0 lets the framework pick within each band.
    private fun bands(vararg bands: Int) = SparseIntArray().apply { bands.forEach { put(it, 0) } }

    @RequiresApi(33)
    private fun bandOf(config: SoftApConfiguration): HotspotBand {
        val channels = config.channels
        val keys = (0 until channels.size()).map(channels::keyAt)
        val has5 = keys.any { it and SoftApConfiguration.BAND_5GHZ != 0 }
        val has2 = keys.any { it and SoftApConfiguration.BAND_2GHZ != 0 }
        return when {
            has5 && has2 && keys.size > 1 -> HotspotBand.DUAL
            has5 && !has2 -> HotspotBand.FIVE_GHZ
            else -> HotspotBand.TWO_GHZ
        }
    }

    @RequiresApi(33)
    private fun isPassphraseProtected(config: SoftApConfiguration): Boolean =
        !config.passphrase.isNullOrEmpty() && securityOf(config.securityType) != HotspotSecurity.OPEN

    @RequiresApi(33)
    private suspend fun requestReservation(
        start: (WifiManager.LocalOnlyHotspotCallback) -> Unit,
    ): Requested = suspendCancellableCoroutine { continuation ->
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
            start(cb)
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
