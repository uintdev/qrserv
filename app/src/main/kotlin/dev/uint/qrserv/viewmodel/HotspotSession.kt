package dev.uint.qrserv.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.UserManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import dev.uint.qrserv.R
import dev.uint.qrserv.data.AppUiState
import dev.uint.qrserv.data.CompatibleBandWarning
import dev.uint.qrserv.data.FileInfo
import dev.uint.qrserv.data.HotspotDialog
import dev.uint.qrserv.data.PageType
import dev.uint.qrserv.data.Preferences
import dev.uint.qrserv.net.HotspotAvailability
import dev.uint.qrserv.net.HotspotBand
import dev.uint.qrserv.net.HotspotController
import dev.uint.qrserv.net.HotspotFailure
import dev.uint.qrserv.net.HotspotInfo
import dev.uint.qrserv.net.HotspotLoss
import dev.uint.qrserv.net.HotspotPreflight
import dev.uint.qrserv.net.HotspotStartResult
import dev.uint.qrserv.net.HotspotUnavailable
import dev.uint.qrserv.net.hotspotPreflight
import dev.uint.qrserv.net.hotspotUnavailableReason
import dev.uint.qrserv.net.labelRes
import dev.uint.qrserv.server.ServingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class HotspotSession internal constructor(
    private val app: Application,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<AppUiState>,
    private val host: Host,
) {
    internal interface Host {
        fun postToast(@StringRes resId: Int, vararg args: String)
        fun setLoading(loading: Boolean)
        fun rejectIfBusy(): Boolean
        fun isServerRunning(): Boolean
        fun openPickerWhenReady()
        suspend fun startServing(fileInfo: FileInfo)
        suspend fun stopServing()
        suspend fun switchToHotspot(info: HotspotInfo)
        fun endShare()
    }

    private enum class Intent {
        FROM_IDLE,

        SWITCH,
    }

    private val controller: HotspotController? =
        if (Build.VERSION.SDK_INT >= 33) HotspotController(app, ::onLost) else null

    private var pendingIntent: Intent? = null

    private var reuseFile: FileInfo? = null

    private var screenShown = false

    // A one-off band from the hotspot screen's links, overriding the preference; kept through Try again.
    private var bandOnce: HotspotBand? = null

    // Tearing the hotspot down destroys sockets bound to its address, and the next one can reuse it.
    private var rebindAfterRestart = false

    // Devices that requested the file on this hotspot; the system doesn't let apps list its clients.
    // Written from Ktor's threads.
    private val clients: MutableSet<String> = ConcurrentHashMap.newKeySet()

    internal val isActive: Boolean
        get() = controller?.isActive == true

    internal fun initialState(state: AppUiState): AppUiState {
        val bandOptions = controller?.selectableBands.orEmpty()
        return state.copy(
            hotspotBand = readBand(bandOptions),
            hotspotBandOptions = bandOptions,
            hotspotBandNeedsNewerAndroid = controller?.bandChoiceNeedsNewerAndroid
                ?: (capableBeforeAndroid13() && isFiveGhzSupported()),
            hotspotAvailability = computeAvailability(),
        )
    }

    internal fun claimScreenOpening(): Boolean {
        if (screenShown) return false
        screenShown = true
        return true
    }

    internal fun markScreenShown() {
        screenShown = true
    }

    internal fun consumeRebindRequired(): Boolean = rebindAfterRestart.also { rebindAfterRestart = false }

    internal fun recordClient(ip: String) {
        // The phone itself can reach the hotspot address too; that's not a client.
        uiState.value.hotspot?.takeIf { it.address != ip }?.let { clients.add(ip) }
    }

    internal fun onAppResumed() {
        uiState.update { it.copy(hotspotAvailability = computeAvailability()) }
        controller?.recheck()
        val fixed = when (uiState.value.hotspotDialog) {
            HotspotDialog.NEARBY_SETTINGS -> isNearbyGranted()
            HotspotDialog.WIFI_CONTROL_SETTINGS -> controller?.isWifiControlAllowed() == true
            else -> false
        }
        if (fixed) {
            uiState.update { it.copy(hotspotDialog = null) }
            pendingIntent?.let { request(it) }
        }
    }

    private fun unavailableOn(sdkInt: Int): HotspotUnavailable? = hotspotUnavailableReason(
        sdkInt = sdkInt,
        hasWifi = app.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI),
        tetheringRestricted = app.getSystemService(UserManager::class.java)
            .hasUserRestriction(UserManager.DISALLOW_CONFIG_TETHERING),
    )

    private fun computeAvailability(): HotspotAvailability {
        val unavailable = unavailableOn(Build.VERSION.SDK_INT)
        val disconnectsWifi = unavailable == null && Build.VERSION.SDK_INT >= 33 &&
            runCatching { app.getSystemService(WifiManager::class.java)?.isStaApConcurrencySupported }.getOrNull() == false
        return HotspotAvailability(unavailable, disconnectsWifi)
    }

    internal fun isNearbyGranted(): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED

    fun onHotspotClicked() {
        val state = uiState.value
        if (state.pageType != PageType.HOTSPOT_FAILED) bandOnce = null
        reuseFile = when (state.pageType) {
            in ReusableFilePages -> state.fileInfo.takeIf { it.path.isNotEmpty() }
            PageType.HOTSPOT_FAILED -> reuseFile
            else -> null
        }
        request(Intent.FROM_IDLE)
    }

    fun onSwitchToHotspotClicked() {
        if (!uiState.value.serverRunning || uiState.value.hotspot != null) return
        bandOnce = null
        request(Intent.SWITCH)
    }

    private fun request(intent: Intent) {
        if (host.rejectIfBusy()) return
        val controller = controller ?: return
        val availability = computeAvailability()
        uiState.update { it.copy(hotspotAvailability = availability) }
        pendingIntent = intent
        when (hotspotPreflight(availability.unavailable, controller.isWifiControlAllowed(), isNearbyGranted())) {
            HotspotPreflight.Ready -> start()
            HotspotPreflight.NeedsWifiControl ->
                uiState.update { it.copy(hotspotDialog = HotspotDialog.WIFI_CONTROL_SETTINGS) }
            HotspotPreflight.NeedsNearby -> uiState.update { it.copy(nearbyPermissionRequest = true) }
            is HotspotPreflight.Unavailable -> abandonRequest()
        }
    }

    private fun abandonRequest() {
        pendingIntent = null
        if (rebindAfterRestart && uiState.value.serverRunning && uiState.value.hotspot == null) {
            rebindAfterRestart = false
            reuseFile = null
            onLost(HotspotLoss.STOPPED)
        }
    }

    fun onNearbyPermissionRequestTaken() {
        uiState.update { it.copy(nearbyPermissionRequest = false) }
    }

    fun onNearbyPermissionResult(granted: Boolean) {
        val intent = pendingIntent
        if (granted && intent != null) request(intent) else abandonRequest()
    }

    fun showHotspotDialog(dialog: HotspotDialog) {
        uiState.update { it.copy(hotspotDialog = dialog) }
    }

    fun onHotspotDialogContinue() {
        uiState.update { it.copy(hotspotDialog = null) }
    }

    fun onHotspotDialogDismissed() {
        abandonRequest()
        uiState.update { it.copy(hotspotDialog = null) }
    }

    private fun start() {
        val intent = pendingIntent ?: return
        pendingIntent = null
        val controller = controller ?: return
        host.setLoading(true)
        uiState.update { it.copy(hotspotStarting = true) }
        scope.launch {
            // Only the links' restarts report a band that didn't take; elsewhere the Band row shows it.
            val compatibleRequested = bandOnce == HotspotBand.TWO_GHZ
            val fasterRequested = bandOnce == HotspotBand.DUAL || bandOnce == HotspotBand.FIVE_GHZ
            when (val result = controller.start(band = bandOnce ?: uiState.value.hotspotBand)) {
                is HotspotStartResult.Failed -> {
                    uiState.update { it.copy(hotspotStarting = false) }
                    host.setLoading(false)
                    onStartFailed(result.reason, intent)
                }
                is HotspotStartResult.Started -> {
                    bandOnce = null
                    // When 2.4 GHz was refused, the default can come back on another band.
                    val band = result.info.band
                    if (compatibleRequested && band != null && band != HotspotBand.TWO_GHZ) {
                        host.postToast(R.string.hotspot_restart_sameband_toast, app.getString(band.labelRes))
                    } else if (fasterRequested && band == HotspotBand.TWO_GHZ) {
                        host.postToast(R.string.hotspot_restart_faster_failed_toast)
                    }
                    if (intent == Intent.SWITCH) {
                        uiState.update { it.copy(hotspotStarting = false, hotspotFailure = null) }
                        host.switchToHotspot(result.info)
                    } else {
                        uiState.update { it.copy(hotspot = result.info, hotspotStarting = false, hotspotFailure = null) }
                        continueFromIdle()
                    }
                }
            }
        }
    }

    private fun onStartFailed(reason: HotspotFailure, intent: Intent) {
        when {
            // Still granted means the system refused for its own reasons; asking again would loop.
            reason == HotspotFailure.PERMISSION_DENIED && !isNearbyGranted() -> {
                pendingIntent = intent
                uiState.update { it.copy(nearbyPermissionRequest = true) }
            }
            reason == HotspotFailure.WIFI_CONTROL_DENIED -> {
                pendingIntent = intent
                uiState.update { it.copy(hotspotDialog = HotspotDialog.WIFI_CONTROL_SETTINGS) }
            }
            intent == Intent.SWITCH -> host.postToast(hotspotFailureMessageRes(reason))
            else -> {
                // Only the compatible-band restart gets here with the server still up.
                if (host.isServerRunning()) scope.launch { host.stopServing() }
                uiState.update { it.copy(pageType = PageType.HOTSPOT_FAILED, hotspotFailure = reason) }
            }
        }
    }

    private suspend fun continueFromIdle() {
        val reuse = reuseFile?.takeIf { File(it.path).exists() }
        reuseFile = null
        if (reuse != null) {
            uiState.update { it.copy(fileInfo = reuse) }
            host.startServing(reuse)
            host.setLoading(false)
        } else {
            // Not busy while picking, or the picker's result would be refused as a second import.
            host.setLoading(false)
            host.openPickerWhenReady()
        }
    }

    /** Restarts the hotspot on 2.4 GHz for clients that can't see a 5 GHz-only one, keeping the file shared. */
    fun onUseCompatibleBandClicked() = requestBandRestart(faster = false)

    fun onUseFasterBandClicked() = requestBandRestart(faster = true)

    private fun requestBandRestart(faster: Boolean) {
        if (host.rejectIfBusy()) return
        if (uiState.value.hotspot == null || !uiState.value.serverRunning) return
        val warning = when {
            ServingState.activeTransfers.value > 0 -> CompatibleBandWarning.DOWNLOAD_ACTIVE
            clients.isNotEmpty() -> CompatibleBandWarning.CLIENT_SEEN
            else -> CompatibleBandWarning.NONE_SEEN
        }
        uiState.update { it.copy(compatibleBandWarning = warning, hotspotRestartFaster = faster) }
    }

    fun onCompatibleBandWarningConfirmed() {
        uiState.update { it.copy(compatibleBandWarning = null) }
        if (host.rejectIfBusy()) return
        if (uiState.value.hotspot == null || !uiState.value.serverRunning) return
        restartOnBand(faster = uiState.value.hotspotRestartFaster)
    }

    fun onCompatibleBandWarningDismissed() {
        uiState.update { it.copy(compatibleBandWarning = null) }
    }

    private fun restartOnBand(faster: Boolean) {
        bandOnce = if (faster) uiState.value.hotspot?.fasterBand else HotspotBand.TWO_GHZ
        rebindAfterRestart = true
        reuseFile = uiState.value.fileInfo
        // The server stays up so the port is kept; startServing() rebinds it to the new address.
        stopSession()
        request(Intent.FROM_IDLE)
    }

    // A band restored from another device's backup may not be supported here; treat it as unset.
    private fun readBand(options: List<HotspotBand>): HotspotBand? =
        Preferences.readString(Preferences.PREF_HOTSPOT_BAND)
            ?.let { name -> HotspotBand.entries.firstOrNull { it.name == name } }
            ?.takeIf { it in options }

    fun setHotspotBand(band: HotspotBand) {
        Preferences.writeString(Preferences.PREF_HOTSPOT_BAND, band.name)
        uiState.update { it.copy(hotspotBand = band) }
    }

    private fun isFiveGhzSupported(): Boolean =
        app.getSystemService(WifiManager::class.java)?.is5GHzBandSupported == true

    // Private hotspot mode needs 33; below that, only offer what an update would unlock.
    private fun capableBeforeAndroid13(): Boolean =
        Build.VERSION.SDK_INT < 33 && unavailableOn(sdkInt = 33) == null

    fun onHotspotScreenOpened() {
        uiState.update { it.copy(hotspotScreenPending = false) }
    }

    internal fun stopSession() {
        uiState.update(::withoutHotspot)
        stopHotspot()
    }

    internal fun stopHotspot() {
        screenShown = false
        clients.clear()
        // Called from Ktor's threads; the controller is only touched on main.
        scope.launch(Dispatchers.Main.immediate) { controller?.stop() }
    }

    internal fun withoutHotspot(state: AppUiState): AppUiState =
        state.copy(
            hotspot = null,
            hotspotStarting = false,
            hotspotStopping = false,
            hotspotScreenPending = false,
            compatibleBandWarning = null,
        )

    internal fun abandonIdle() {
        if (uiState.value.hotspot != null && !uiState.value.serverRunning) stopSession()
    }

    internal fun onLost(loss: HotspotLoss) {
        host.postToast(
            when (loss) {
                HotspotLoss.STOPPED -> R.string.hotspot_lost_stopped
                HotspotLoss.WIFI_CONTROL_REVOKED -> R.string.hotspot_lost_wificontrol
            },
        )
        if (uiState.value.serverRunning) host.endShare() else stopSession()
    }

    internal fun onCleared() {
        controller?.stop()
    }

    private companion object {
        val ReusableFilePages = setOf(PageType.NO_CONNECTION, PageType.INTERFACE_LOOKUP_ERROR, PageType.PORT_IN_USE)
    }
}

@StringRes
fun hotspotFailureMessageRes(reason: HotspotFailure): Int = when (reason) {
    HotspotFailure.INCOMPATIBLE_MODE -> R.string.hotspot_failed_incompatible
    HotspotFailure.NO_CHANNEL -> R.string.hotspot_failed_nochannel
    HotspotFailure.TETHERING_DISALLOWED -> R.string.hotspot_unavailable_blocked
    HotspotFailure.PERMISSION_DENIED,
    HotspotFailure.WIFI_CONTROL_DENIED,
    HotspotFailure.NO_ADDRESS,
    HotspotFailure.GENERIC,
    -> R.string.hotspot_failed_generic
}
