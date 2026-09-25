package dev.uint.qrserv.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.UserManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.uint.qrserv.R
import dev.uint.qrserv.data.AddressGroup
import dev.uint.qrserv.data.AppUiState
import dev.uint.qrserv.data.DEFAULT_IDLE_STOP_MINUTES
import dev.uint.qrserv.data.CompatibleBandWarning
import dev.uint.qrserv.data.FileInfo
import dev.uint.qrserv.data.HotspotDialog
import dev.uint.qrserv.data.IdleStopOptions
import dev.uint.qrserv.data.ImportProgress
import dev.uint.qrserv.data.InterfaceAddress
import dev.uint.qrserv.data.PageType
import dev.uint.qrserv.data.Preferences
import dev.uint.qrserv.data.ThemeMode
import dev.uint.qrserv.data.readPersistedThemeMode
import dev.uint.qrserv.files.CacheManager
import dev.uint.qrserv.files.FileRepository
import dev.uint.qrserv.files.ImportResult
import dev.uint.qrserv.net.AddressChange
import dev.uint.qrserv.net.HotspotAvailability
import dev.uint.qrserv.net.HotspotBand
import dev.uint.qrserv.net.HotspotController
import dev.uint.qrserv.net.HotspotFailure
import dev.uint.qrserv.net.HotspotInfo
import dev.uint.qrserv.net.HotspotLoss
import dev.uint.qrserv.net.HotspotPreflight
import dev.uint.qrserv.net.HotspotStartResult
import dev.uint.qrserv.net.NetworkUtils
import dev.uint.qrserv.net.addressChange
import dev.uint.qrserv.net.hotspotPreflight
import dev.uint.qrserv.net.hotspotUnavailableReason
import dev.uint.qrserv.net.labelRes
import dev.uint.qrserv.server.ServerController
import dev.uint.qrserv.server.ServingNotice
import dev.uint.qrserv.server.ServingService
import dev.uint.qrserv.server.ServingState
import dev.uint.qrserv.util.ManifestUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

sealed interface UiEvent {
    object OpenSafPicker : UiEvent
    object OpenDamBrowser : UiEvent
    object RequestDamPermission : UiEvent
    object PortSaved : UiEvent
    data class Toast(val event: ToastEvent) : UiEvent
}

private val ADDRESS_SETTLE_TIME = 2.seconds

private data class IdleTimerKey(
    val sharing: Boolean,
    val network: String?,
    val minutes: Int,
    val downloading: Boolean = false,
    val requests: Int = 0,
)

class QRServViewModel(application: Application) : AndroidViewModel(application) {

    private val fileRepo = FileRepository(application)

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events

    private var fileObserver: FileObserver? = null

    private val hotspotController: HotspotController? =
        if (Build.VERSION.SDK_INT >= 33) HotspotController(application, ::onHotspotLost) else null

    private enum class HotspotIntent {
        FROM_IDLE,

        SWITCH,
    }
    private var pendingHotspotIntent: HotspotIntent? = null

    private var hotspotReuseFile: FileInfo? = null

    private var hotspotScreenShown = false

    // A one-off band from the hotspot screen's links, overriding the preference; kept through Try again.
    private var bandOnce: HotspotBand? = null

    // Tearing the hotspot down destroys sockets bound to its address, and the next one can reuse it.
    private var rebindAfterHotspotRestart = false

    // Devices that requested the file on this hotspot; the system doesn't let apps list its clients.
    // Written from Ktor's threads.
    private val hotspotClients: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // Bumped on every request, so the idle timer restarts even for a download too quick to show up in
    // activeTransfers (a StateFlow can skip its brief 1).
    private val serverRequests = MutableStateFlow(0)

    // Where onServerStopped() leaves the imported page; set for a stop that has something to explain.
    private var pageAfterStop = PageType.LANDING

    private var notificationPromptOpen = false

    private var pickerAfterNotificationPrompt = false

    private var rebindJob: Job? = null

    private var launchCacheHandled = false

    private var addressesAtManualPick = emptySet<String>()

    private val addressCheckRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val connectivityManager = application.getSystemService(ConnectivityManager::class.java)

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            addressCheckRequests.tryEmit(Unit)
        }

        override fun onLost(network: Network) {
            addressCheckRequests.tryEmit(Unit)
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            addressCheckRequests.tryEmit(Unit)
        }
    }

    private val serverController = ServerController(
        downloadStartedCallback = { ip ->
            serverRequests.update { it + 1 }
            // The phone itself can reach the hotspot address too; that's not a client.
            _uiState.value.hotspot?.takeIf { it.address != ip }?.let { hotspotClients.add(ip) }
            postToast(R.string.server_info_download_started, ip)
        },
        downloadFinishedCallback = { ip ->
            postToast(R.string.server_info_download_finished, ip)
        },
        fileMissingCallback = { onServerStopped() },
        permissionDeniedCallback = {
            postToast(R.string.page_info_permissiondenied_msg)
            onServerStopped()
        },
        serverGoneCallback = {
            postToast(R.string.server_info_gone)
            onServerStopped()
        },
    )

    init {
        Preferences.init(application)
        fileRepo.directAccessMode = Preferences.readBool(Preferences.PREF_CLIENT_DAM)
        val fiu = Preferences.readBool(Preferences.PREF_CLIENT_FIU)
        val port = Preferences.readInt(Preferences.PREF_SERVER_PORT) ?: 0
        val themeMode = readPersistedThemeMode()
        val bandOptions = hotspotController?.selectableBands.orEmpty()
        _uiState.update {
            it.copy(
                damEnabled = fileRepo.directAccessMode,
                fiuEnabled = fiu,
                allInterfacesEnabled = Preferences.readBool(Preferences.PREF_SERVER_ALL_INTERFACES),
                hotspotBand = readHotspotBand(bandOptions),
                hotspotBandOptions = bandOptions,
                hotspotBandNeedsNewerAndroid = hotspotController?.bandChoiceNeedsNewerAndroid
                    ?: (hotspotCapableBeforeAndroid13() && isFiveGhzSupported()),
                idleStopMinutes = Preferences.readInt(Preferences.PREF_SERVER_IDLE_MINUTES)?.takeIf { it in IdleStopOptions }
                    ?: DEFAULT_IDLE_STOP_MINUTES,
                port = port,
                savedPort = port,
                damEligible = isDamEligible(),
                damBuildIneligible = !isDamBuildEligible(),
                themeMode = themeMode,
                hotspotAvailability = computeHotspotAvailability(),
                vpnLockdown = NetworkUtils.isVpnLockdownEnabled(application),
            )
        }
        reportAbruptSessionEnd()

        viewModelScope.launch {
            uiState.map(::servingNoticeFor).distinctUntilChanged().collect { notice ->
                val wasIdle = ServingState.notice.value == null
                ServingState.setNotice(notice)
                if (notice != null && wasIdle) {
                    ServingService.start(application)
                    maybeRequestNotificationPermission()
                }
            }
        }
        viewModelScope.launch {
            ServingState.stopRequests.collect { onShutdownClicked() }
        }
        // A forgotten share -- and a private hotspot, which Android never times out -- would otherwise keep
        // the file available long after it's needed. Any change -- a share starting or switching network, a
        // request, a download ending, the setting -- restarts the timer.
        viewModelScope.launch {
            combine(
                uiState.map { state ->
                    IdleTimerKey(
                        sharing = state.serverRunning && !state.serverPoweringDown,
                        network = state.hotspot?.ssid,
                        minutes = state.idleStopMinutes,
                    )
                },
                ServingState.activeTransfers,
                serverRequests,
            ) { key, transfers, requests -> key.copy(downloading = transfers > 0, requests = requests) }
                .distinctUntilChanged()
                .collectLatest { key ->
                    if (!key.sharing || key.minutes <= 0 || key.downloading) return@collectLatest
                    delay(key.minutes.minutes)
                    if (!_uiState.value.actionButtonLoading) stopForIdle(key.minutes)
                }
        }
        viewModelScope.launch {
            addressCheckRequests.collectLatest {
                delay(ADDRESS_SETTLE_TIME)
                checkAddresses()
            }
        }
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder()
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build(),
            networkCallback,
        )
    }

    private fun servingNoticeFor(state: AppUiState): ServingNotice? = when {
        state.hotspotStarting -> ServingNotice.StartingHotspot
        state.actionButtonLoading -> ServingNotice.Preparing
        state.serverRunning && !state.serverPoweringDown -> {
            ServingNotice.Sharing(state.fileInfo.name, "${NetworkUtils.urlHost(state.selectedIp)}:${state.port}", state.hotspot?.ssid)
        }
        state.hotspot != null -> ServingNotice.StartingHotspot
        else -> null
    }

    // State, not a UiEvent: a share from another app starts before the UI collects events.
    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        if (Preferences.readBool(Preferences.PREF_NOTIFICATIONS_ASKED)) return
        if (!isGranted(Manifest.permission.POST_NOTIFICATIONS)) {
            notificationPromptOpen = true
            _uiState.update { it.copy(notificationPermissionPending = true) }
        }
    }

    fun onNotificationPermissionRequested() {
        Preferences.writeBool(Preferences.PREF_NOTIFICATIONS_ASKED, true)
        _uiState.update { it.copy(notificationPermissionPending = false) }
    }

    fun onNotificationDialogDismissed() {
        onNotificationPermissionRequested()
        onNotificationPermissionResult(false)
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        notificationsGranted = granted
        notificationPromptOpen = false
        if (granted && ServingState.notice.value != null) ServingService.start(getApplication())
        if (pickerAfterNotificationPrompt) {
            pickerAfterNotificationPrompt = false
            if (_uiState.value.hotspot != null) openPicker()
        }
    }

    private fun areNotificationsGranted(): Boolean =
        Build.VERSION.SDK_INT < 33 || isGranted(Manifest.permission.POST_NOTIFICATIONS)

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(getApplication(), permission) == PackageManager.PERMISSION_GRANTED

    private var notificationsGranted = areNotificationsGranted()

    fun purgeStaleCacheOnLaunch() {
        if (launchCacheHandled) return
        launchCacheHandled = true
        viewModelScope.launch(Dispatchers.IO) { deletePickerCache() }
    }

    fun onImportClicked() {
        if (rejectIfBusy()) return
        openPicker()
    }

    private fun openPicker() {
        if (fileRepo.directAccessMode) {
            if (hasDirectAccessPermission()) {
                _events.tryEmit(UiEvent.OpenDamBrowser)
            } else {
                _events.tryEmit(UiEvent.RequestDamPermission)
            }
        } else {
            _events.tryEmit(UiEvent.OpenSafPicker)
        }
    }

    fun onImportCancelled() {
        setLoading(false)
        abandonIdleHotspot()
    }

    fun onFilesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) {
            abandonIdleHotspot()
            return
        }
        if (rejectIfBusy()) return
        setLoading(true)
        stopFileObserver()
        viewModelScope.launch {
            val result = fileRepo.importUris(uris) { completedFiles, totalFiles, bytesCopied, totalBytes ->
                _uiState.update {
                    it.copy(importProgress = ImportProgress(completedFiles, totalFiles, bytesCopied, totalBytes))
                }
            }
            handleImportResult(result)
        }
    }

    fun onDirectAccessPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(damEligible = isDamEligible()) }
        if (granted) {
            _events.tryEmit(UiEvent.OpenDamBrowser)
        } else {
            setLoading(false)
            abandonIdleHotspot()
            postToast(R.string.page_info_permissiondenied_msg)
        }
    }

    /** Called by the DAM browser when a permission check fails mid-browse (permission revoked). */
    fun onDamPermissionRevoked() {
        postToast(R.string.page_info_permissiondenied_msg)
    }

    fun onDirectAccessFileChosen(path: String) {
        if (rejectIfBusy()) return
        setLoading(true)
        stopFileObserver()
        viewModelScope.launch {
            val result = fileRepo.importDirectAccessFile(path)
            handleImportResult(result)
        }
    }

    fun onSharedFilesReceived(uris: List<Uri>) {
        launchCacheHandled = true
        onFilesPicked(uris)
    }

    /** Handles a plain-text share (e.g. a URL) by writing it to a generated .txt file and importing that. */
    fun onSharedTextReceived(text: String) {
        launchCacheHandled = true
        if (text.isBlank()) return
        if (rejectIfBusy()) return
        setLoading(true)
        stopFileObserver()
        viewModelScope.launch {
            val result = fileRepo.importSharedText(text)
            handleImportResult(result)
        }
    }

    private suspend fun handleImportResult(result: ImportResult) {
        when (result) {
            is ImportResult.Success -> {
                stopFileObserver()
                _uiState.update { it.copy(fileInfo = result.fileInfo) }
                startServing(result.fileInfo)
            }
            ImportResult.EmptySelection -> abandonIdleHotspot()
            ImportResult.FileGone -> {
                _uiState.update { it.copy(pageType = PageType.FILE_REMOVED) }
                stopServing()
            }
            ImportResult.DirectAccessPathMissing -> {
                postToast(R.string.dam_path_not_found)
                abandonIdleHotspot()
            }
            ImportResult.InsufficientStorage -> {
                _uiState.update { it.copy(pageType = PageType.INSUFFICIENT_STORAGE) }
                stopServing()
            }
            is ImportResult.SelectionFailed -> {
                _uiState.update { it.copy(pageType = PageType.UNHANDLED_ERROR, errorDetail = result.message) }
                stopServing()
            }
        }
        setLoading(false)
    }

    private suspend fun startServing(fileInfo: FileInfo) {
        rebindJob?.join()
        if (!serverController.isRunning) addressesAtManualPick = emptySet()
        val hotspot = _uiState.value.hotspot
        if (hotspot != null && hotspotController?.isActive != true) {
            onHotspotLost(HotspotLoss.STOPPED)
            return
        }

        val interfaces = if (hotspot != null) {
            listOf(InterfaceAddress(hotspot.address, AddressGroup.HOSTED))
        } else {
            val listed = NetworkUtils.listInterfaces().getOrElse {
                _uiState.update { it.copy(pageType = PageType.INTERFACE_LOOKUP_ERROR, interfaces = emptyList()) }
                stopServing()
                return
            }
            if (listed.isEmpty()) {
                _uiState.update { it.copy(pageType = PageType.NO_CONNECTION, interfaces = emptyList()) }
                stopServing()
                return
            }
            listed
        }

        val current = _uiState.value.selectedIp
        val selected = interfaces.firstOrNull { it.address == current } ?: interfaces.first()
        val bindAddress = hotspot?.address ?: selected.bindHost.takeUnless { listensOnAllInterfaces() }
        val rebindRequired = rebindAfterHotspotRestart
        rebindAfterHotspotRestart = false
        if (!serverController.isRunning || serverController.bindAddress != bindAddress || rebindRequired) {
            try {
                startServer(bindAddress, port = serverController.port.takeIf { serverController.isRunning })
            } catch (_: Exception) {
                postToast(R.string.info_exception_portinuse)
                stopHotspotSession()
                _uiState.update { it.copy(pageType = PageType.PORT_IN_USE) }
                return
            }
        }

        val openHotspotScreen = hotspot != null && !hotspotScreenShown
        if (openHotspotScreen) hotspotScreenShown = true

        _uiState.update {
            it.copy(
                interfaces = interfaces,
                selectedIp = selected.address,
                suggestedIp = null,
                port = serverController.port,
                serverRunning = true,
                pageType = PageType.IMPORTED,
                hotspotScreenPending = it.hotspotScreenPending || openHotspotScreen,
            )
        }
        markSessionActive(hotspot != null)

        startFileObserver(fileInfo)
    }

    private fun listensOnAllInterfaces(): Boolean =
        if (serverController.isRunning && _uiState.value.hotspot == null) {
            serverController.bindAddress == null
        } else {
            _uiState.value.allInterfacesEnabled
        }

    private suspend fun startServer(bindAddress: String?, port: Int? = null) = withContext(Dispatchers.IO) {
        serverController.start(
            port ?: Preferences.readInt(Preferences.PREF_SERVER_PORT) ?: 0,
            fileInfoProvider = { _uiState.value.fileInfo },
            hasStoragePermission = { path -> !fileRepo.directModeDetect(path) || hasDirectAccessPermission() },
            bindAddress = bindAddress,
        )
    }

    fun onIpSelected(ip: String) {
        val entry = _uiState.value.interfaces.firstOrNull { it.address == ip } ?: return
        val bound = serverController.bindAddress
        if (!serverController.isRunning || bound == null || _uiState.value.hotspot != null || bound == entry.bindHost) {
            applyManualPick(ip)
            return
        }
        if (rebindJob?.isActive == true || rejectIfBusy()) return
        if (ServingState.activeTransfers.value > 0) {
            postToast(R.string.page_imported_iface_switch_downloading)
            return
        }
        rebindJob = viewModelScope.launch {
            val port = serverController.port
            try {
                startServer(entry.bindHost, port)
                applyManualPick(ip)
            } catch (_: Exception) {
                postToast(R.string.page_imported_iface_switch_failed)
                if (runCatching { startServer(bound, port) }.isFailure) showPortInUse()
            }
        }
    }

    private suspend fun checkAddresses() {
        if (!canCheckAddresses()) return
        val listed = NetworkUtils.listInterfaces().getOrNull()?.takeIf { it.isNotEmpty() } ?: return
        if (!canCheckAddresses()) return
        val state = _uiState.value
        val best = when (val change = addressChange(listed, state.selectedIp, addressesAtManualPick)) {
            is AddressChange.MoveTo -> change.address
            is AddressChange.Stay -> {
                if (listed != state.interfaces || change.suggestion != state.suggestedIp) {
                    _uiState.update { it.copy(interfaces = listed, suggestedIp = change.suggestion) }
                }
                return
            }
        }
        rebindJob = viewModelScope.launch {
            val port = serverController.port
            if (serverController.bindAddress != null) {
                try {
                    startServer(best.bindHost, port)
                } catch (_: Exception) {
                    postToast(R.string.info_exception_portinuse)
                    showPortInUse()
                    return@launch
                }
            }
            _uiState.update { it.copy(interfaces = listed, selectedIp = best.address, suggestedIp = null) }
            postToast(R.string.page_imported_iface_address_changed)
        }
    }

    private fun canCheckAddresses(): Boolean {
        val state = _uiState.value
        return state.serverRunning && !state.serverPoweringDown && state.hotspot == null &&
            !state.actionButtonLoading && rebindJob?.isActive != true
    }

    private fun applyManualPick(ip: String) {
        addressesAtManualPick = _uiState.value.interfaces.mapTo(HashSet()) { it.address }
        _uiState.update { it.copy(selectedIp = ip, suggestedIp = null) }
    }

    fun toggleAllInterfaces() {
        val newValue = !_uiState.value.allInterfacesEnabled
        Preferences.writeBool(Preferences.PREF_SERVER_ALL_INTERFACES, newValue)
        _uiState.update { it.copy(allInterfacesEnabled = newValue) }
        if (_uiState.value.serverRunning) postToast(R.string.settings_server_port_dialog_serveractive)
    }

    fun onShutdownClicked() {
        if (_uiState.value.actionButtonLoading) {
            postToast(R.string.info_pending_fileprocessing_shutdown)
            return
        }
        if (!_uiState.value.serverRunning || _uiState.value.serverPoweringDown) {
            postToast(R.string.info_pending_servershutdown)
            return
        }
        _uiState.update { it.copy(serverPoweringDown = true) }
        viewModelScope.launch {
            rebindJob?.join()
            withContext(Dispatchers.IO) { serverController.stop() }
            onServerStopped()
        }
    }

    private fun onServerStopped() {
        stopFileObserver()
        stopHotspotSession()
        clearSessionMarker()
        val stoppedPage = pageAfterStop
        pageAfterStop = PageType.LANDING
        viewModelScope.launch {
            withContext(Dispatchers.IO) { deletePickerCache() }
            _uiState.update {
                it.copy(
                    serverRunning = false,
                    serverPoweringDown = false,
                    pageType = if (it.pageType == PageType.IMPORTED) stoppedPage else it.pageType,
                )
            }
        }
    }

    private suspend fun deletePickerCache() {
        CacheManager.deleteCache(fileRepo.pickerDir(ignoreDam = true), directAccessRoot = FileRepository.DIRECT_ACCESS_ROOT)
    }

    private suspend fun showPortInUse() {
        stopServing()
        _uiState.update { it.copy(pageType = PageType.PORT_IN_USE) }
    }

    private suspend fun stopServing() {
        withContext(Dispatchers.IO) {
            serverController.stop()
            stopFileObserver()
        }
        stopHotspotSession()
        clearSessionMarker()
        _uiState.update { it.copy(serverRunning = false, serverPoweringDown = false) }
    }

    private fun startFileObserver(fileInfo: FileInfo) {
        stopFileObserver()
        val file = File(fileInfo.path)
        val parent = file.parentFile ?: return
        val mask = FileObserver.DELETE or FileObserver.MOVED_FROM or FileObserver.MODIFY
        val isDirectAccessFile = fileRepo.directModeDetect(fileInfo.path)

        // The File-based constructor (API 29+) avoids path-resolution edge cases with
        // symlinks/renames that the deprecated String-path constructor is prone to.
        fileObserver = if (Build.VERSION.SDK_INT >= 29) {
            object : FileObserver(parent, mask) {
                override fun onEvent(event: Int, path: String?) =
                    handleFileObserverEvent(event, path, file, isDirectAccessFile)
            }
        } else {
            @Suppress("DEPRECATION")
            object : FileObserver(parent.path, mask) {
                override fun onEvent(event: Int, path: String?) =
                    handleFileObserverEvent(event, path, file, isDirectAccessFile)
            }
        }.also { it.startWatching() }
    }

    private fun handleFileObserverEvent(event: Int, path: String?, file: File, isDirectAccessFile: Boolean) {
        if (path != file.name) return
        when (event and FileObserver.ALL_EVENTS) {
            FileObserver.DELETE, FileObserver.MOVED_FROM -> {
                if (!file.exists()) {
                    val session = serverController.currentSession()
                    viewModelScope.launch {
                        if (serverController.currentSession() !== session) return@launch
                        if (isDirectAccessFile && !hasDirectAccessPermission()) {
                            // The file didn't actually go anywhere -- losing MANAGE_EXTERNAL_STORAGE
                            // makes scoped storage report it as gone, which looks identical to a
                            // real delete/move at this level.
                            postToast(R.string.page_info_permissiondenied_msg)
                            onServerStopped()
                        } else {
                            _uiState.update { it.copy(pageType = PageType.FILE_REMOVED) }
                            stopServing()
                        }
                    }
                }
            }
            FileObserver.MODIFY -> {
                if (isDirectAccessFile && file.exists()) {
                    val session = serverController.currentSession()
                    viewModelScope.launch {
                        if (serverController.currentSession() !== session) return@launch
                        _uiState.update { it.copy(pageType = PageType.FILE_MODIFIED) }
                        stopServing()
                    }
                }
            }
        }
    }

    private fun stopFileObserver() {
        fileObserver?.stopWatching()
        fileObserver = null
    }

    private fun computeHotspotAvailability(): HotspotAvailability {
        val app = getApplication<Application>()
        val unavailable = hotspotUnavailableReason(
            sdkInt = Build.VERSION.SDK_INT,
            hasWifi = app.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI),
            tetheringRestricted = app.getSystemService(UserManager::class.java)
                .hasUserRestriction(UserManager.DISALLOW_CONFIG_TETHERING),
        )
        val disconnectsWifi = unavailable == null && Build.VERSION.SDK_INT >= 33 &&
            runCatching { app.getSystemService(WifiManager::class.java)?.isStaApConcurrencySupported }.getOrNull() == false
        return HotspotAvailability(unavailable, disconnectsWifi)
    }

    private fun isNearbyGranted(): Boolean =
        Build.VERSION.SDK_INT >= 33 && isGranted(Manifest.permission.NEARBY_WIFI_DEVICES)

    fun onAppResumed() {
        addressCheckRequests.tryEmit(Unit)
        _uiState.update {
            it.copy(
                hotspotAvailability = computeHotspotAvailability(),
                vpnLockdown = NetworkUtils.isVpnLockdownEnabled(getApplication()),
            )
        }
        hotspotController?.recheck()
        val granted = areNotificationsGranted()
        if (granted && !notificationsGranted && ServingState.notice.value != null) ServingService.start(getApplication())
        notificationsGranted = granted
        val fixed = when (_uiState.value.hotspotDialog) {
            HotspotDialog.NEARBY_SETTINGS -> isNearbyGranted()
            HotspotDialog.WIFI_CONTROL_SETTINGS -> hotspotController?.isWifiControlAllowed() == true
            else -> false
        }
        if (fixed) {
            _uiState.update { it.copy(hotspotDialog = null) }
            pendingHotspotIntent?.let { requestHotspot(it) }
        }
    }

    fun onHotspotClicked() {
        if (_uiState.value.pageType != PageType.HOTSPOT_FAILED) bandOnce = null
        hotspotReuseFile = when (_uiState.value.pageType) {
            in ReusableFilePages -> _uiState.value.fileInfo.takeIf { it.path.isNotEmpty() }
            PageType.HOTSPOT_FAILED -> hotspotReuseFile
            else -> null
        }
        requestHotspot(HotspotIntent.FROM_IDLE)
    }

    fun onSwitchToHotspotClicked() {
        if (!_uiState.value.serverRunning || _uiState.value.hotspot != null) return
        requestHotspot(HotspotIntent.SWITCH)
    }

    private fun requestHotspot(intent: HotspotIntent) {
        if (rejectIfBusy()) return
        val controller = hotspotController ?: return
        val availability = computeHotspotAvailability()
        _uiState.update { it.copy(hotspotAvailability = availability) }
        pendingHotspotIntent = intent
        when (hotspotPreflight(availability.unavailable, controller.isWifiControlAllowed(), isNearbyGranted())) {
            HotspotPreflight.Ready -> startHotspot()
            HotspotPreflight.NeedsWifiControl ->
                _uiState.update { it.copy(hotspotDialog = HotspotDialog.WIFI_CONTROL_SETTINGS) }
            HotspotPreflight.NeedsNearby -> _uiState.update { it.copy(nearbyPermissionRequest = true) }
            is HotspotPreflight.Unavailable -> pendingHotspotIntent = null
        }
    }

    fun onNearbyPermissionRequestTaken() {
        _uiState.update { it.copy(nearbyPermissionRequest = false) }
    }

    fun onNearbyPermissionResult(granted: Boolean) {
        val intent = pendingHotspotIntent
        if (granted && intent != null) requestHotspot(intent) else pendingHotspotIntent = null
    }

    fun showHotspotDialog(dialog: HotspotDialog) {
        _uiState.update { it.copy(hotspotDialog = dialog) }
    }

    fun onHotspotDialogContinue() {
        _uiState.update { it.copy(hotspotDialog = null) }
    }

    fun onHotspotDialogDismissed() {
        pendingHotspotIntent = null
        _uiState.update { it.copy(hotspotDialog = null) }
    }

    private fun startHotspot() {
        val intent = pendingHotspotIntent ?: return
        pendingHotspotIntent = null
        val controller = hotspotController ?: return
        setLoading(true)
        _uiState.update { it.copy(hotspotStarting = true) }
        viewModelScope.launch {
            val requested = bandOnce ?: _uiState.value.hotspotBand ?: controller.selectableBands.firstOrNull()
            // Only the links' restarts report a band that didn't take; elsewhere the Band row shows it.
            val compatibleRequested = bandOnce == HotspotBand.TWO_GHZ
            val fasterRequested = bandOnce == HotspotBand.DUAL || bandOnce == HotspotBand.FIVE_GHZ
            val result = controller.start(band = requested)
            _uiState.update { it.copy(hotspotStarting = false) }
            when (result) {
                is HotspotStartResult.Failed -> {
                    setLoading(false)
                    onHotspotStartFailed(result.reason, intent)
                }
                is HotspotStartResult.Started -> {
                    bandOnce = null
                    // Before 36, or when 2.4 GHz was refused, the default can come back on another band.
                    if (compatibleRequested && result.info.band != HotspotBand.TWO_GHZ) {
                        postToast(
                            R.string.hotspot_restart_sameband_toast,
                            getApplication<Application>().getString(result.info.band.labelRes),
                        )
                    } else if (fasterRequested && result.info.band == HotspotBand.TWO_GHZ) {
                        postToast(R.string.hotspot_restart_faster_failed_toast)
                    }
                    _uiState.update { it.copy(hotspot = result.info, hotspotFailure = null) }
                    if (intent == HotspotIntent.SWITCH) switchToHotspot(result.info) else continueFromIdle()
                }
            }
        }
    }

    private fun onHotspotStartFailed(reason: HotspotFailure, intent: HotspotIntent) {
        when {
            // Still granted means the system refused for its own reasons; asking again would loop.
            reason == HotspotFailure.PERMISSION_DENIED && !isNearbyGranted() -> {
                pendingHotspotIntent = intent
                _uiState.update { it.copy(nearbyPermissionRequest = true) }
            }
            reason == HotspotFailure.WIFI_CONTROL_DENIED -> {
                pendingHotspotIntent = intent
                _uiState.update { it.copy(hotspotDialog = HotspotDialog.WIFI_CONTROL_SETTINGS) }
            }
            intent == HotspotIntent.SWITCH -> postToast(hotspotFailureMessageRes(reason))
            else -> {
                // Only the compatible-band restart gets here with the server still up.
                if (serverController.isRunning) viewModelScope.launch { stopServing() }
                _uiState.update { it.copy(pageType = PageType.HOTSPOT_FAILED, hotspotFailure = reason) }
            }
        }
    }

    private suspend fun continueFromIdle() {
        val reuse = hotspotReuseFile?.takeIf { File(it.path).exists() }
        hotspotReuseFile = null
        if (reuse != null) {
            _uiState.update { it.copy(fileInfo = reuse) }
            startServing(reuse)
            setLoading(false)
        } else {
            // Not busy while picking, or the picker's result would be refused as a second import.
            setLoading(false)
            if (notificationPromptOpen) pickerAfterNotificationPrompt = true else openPicker()
        }
    }

    private suspend fun switchToHotspot(info: HotspotInfo) {
        rebindJob?.join()
        val previousBind = serverController.bindAddress
        if (runCatching { startServer(bindAddress = info.address) }.isFailure) {
            stopHotspotSession()
            postToast(R.string.hotspot_failed_generic)
            if (runCatching { startServer(bindAddress = previousBind) }.isSuccess) {
                _uiState.update { it.copy(port = serverController.port) }
            } else {
                showPortInUse()
            }
            setLoading(false)
            return
        }
        hotspotScreenShown = true
        _uiState.update {
            it.copy(
                interfaces = listOf(InterfaceAddress(info.address, AddressGroup.HOSTED)),
                selectedIp = info.address,
                suggestedIp = null,
                port = serverController.port,
                hotspotScreenPending = true,
            )
        }
        markSessionActive(hotspot = true)
        setLoading(false)
    }

    fun onStopHotspotClicked() {
        if (rejectIfBusy()) return
        if (_uiState.value.hotspot == null || !_uiState.value.serverRunning) return
        setLoading(true)
        viewModelScope.launch {
            stopHotspotSession()
            withContext(Dispatchers.IO) { serverController.stop() }
            startServing(_uiState.value.fileInfo)
            setLoading(false)
        }
    }

    /** Restarts the hotspot on 2.4 GHz for clients that can't see a 5 GHz-only one, keeping the file shared. */
    fun onUseCompatibleBandClicked() = requestBandRestart(faster = false)

    fun onUseFasterBandClicked() = requestBandRestart(faster = true)

    private fun requestBandRestart(faster: Boolean) {
        if (rejectIfBusy()) return
        if (_uiState.value.hotspot == null || !_uiState.value.serverRunning) return
        val warning = when {
            ServingState.activeTransfers.value > 0 -> CompatibleBandWarning.DOWNLOAD_ACTIVE
            hotspotClients.isNotEmpty() -> CompatibleBandWarning.CLIENT_SEEN
            else -> CompatibleBandWarning.NONE_SEEN
        }
        _uiState.update { it.copy(compatibleBandWarning = warning, hotspotRestartFaster = faster) }
    }

    fun onCompatibleBandWarningConfirmed() {
        _uiState.update { it.copy(compatibleBandWarning = null) }
        if (rejectIfBusy()) return
        if (_uiState.value.hotspot == null || !_uiState.value.serverRunning) return
        restartOnBand(faster = _uiState.value.hotspotRestartFaster)
    }

    fun onCompatibleBandWarningDismissed() {
        _uiState.update { it.copy(compatibleBandWarning = null) }
    }

    private fun restartOnBand(faster: Boolean) {
        bandOnce = if (faster) _uiState.value.hotspot?.fasterBand else HotspotBand.TWO_GHZ
        rebindAfterHotspotRestart = true
        hotspotReuseFile = _uiState.value.fileInfo
        // The server stays up so the port is kept; startServing() rebinds it to the new address.
        stopHotspotSession()
        requestHotspot(HotspotIntent.FROM_IDLE)
    }

    // A band restored from another device's backup may not be supported here; treat it as unset.
    private fun readHotspotBand(options: List<HotspotBand>): HotspotBand? =
        Preferences.readString(Preferences.PREF_HOTSPOT_BAND)
            ?.let { name -> HotspotBand.entries.firstOrNull { it.name == name } }
            ?.takeIf { it in options }

    fun setIdleStopMinutes(minutes: Int) {
        // Off is stored as 0, not cleared, or it would read back as the default.
        Preferences.writeInt(Preferences.PREF_SERVER_IDLE_MINUTES, minutes)
        _uiState.update { it.copy(idleStopMinutes = minutes) }
    }

    private fun stopForIdle(minutes: Int) {
        if (!_uiState.value.serverRunning || _uiState.value.serverPoweringDown) return
        // A notification reaches someone who isn't looking at the app; otherwise a toast will have to do.
        if (!ServingService.notifyIdleStopped(getApplication(), minutes)) postToast(R.string.info_idle_stopped)
        _uiState.update { it.copy(idleStoppedMinutes = minutes) }
        pageAfterStop = PageType.IDLE_STOPPED
        onShutdownClicked()
    }

    private fun isFiveGhzSupported(): Boolean =
        getApplication<Application>().getSystemService(WifiManager::class.java)?.is5GHzBandSupported == true

    // Private hotspot mode needs 33; below that, only offer what an update would unlock.
    private fun hotspotCapableBeforeAndroid13(): Boolean {
        if (Build.VERSION.SDK_INT >= 33) return false
        val app = getApplication<Application>()
        return app.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI) &&
            !app.getSystemService(UserManager::class.java).hasUserRestriction(UserManager.DISALLOW_CONFIG_TETHERING)
    }

    fun setHotspotBand(band: HotspotBand) {
        Preferences.writeString(Preferences.PREF_HOTSPOT_BAND, band.name)
        _uiState.update { it.copy(hotspotBand = band) }
    }

    fun onHotspotScreenOpened() {
        _uiState.update { it.copy(hotspotScreenPending = false) }
    }

    private fun stopHotspotSession() {
        hotspotScreenShown = false
        hotspotClients.clear()
        _uiState.update {
            it.copy(hotspot = null, hotspotStarting = false, hotspotScreenPending = false, compatibleBandWarning = null)
        }
        // Called from Ktor's threads; the controller is only touched on main.
        viewModelScope.launch(Dispatchers.Main.immediate) { hotspotController?.stop() }
    }

    private fun abandonIdleHotspot() {
        if (_uiState.value.hotspot != null && !_uiState.value.serverRunning) stopHotspotSession()
    }

    private fun onHotspotLost(loss: HotspotLoss) {
        postToast(
            when (loss) {
                HotspotLoss.STOPPED -> R.string.hotspot_lost_stopped
                HotspotLoss.WIFI_CONTROL_REVOKED -> R.string.hotspot_lost_wificontrol
            },
        )
        if (_uiState.value.serverRunning) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) { serverController.stop() }
                onServerStopped()
            }
        } else {
            stopHotspotSession()
        }
    }

    private fun markSessionActive(hotspot: Boolean) {
        Preferences.writeBool(Preferences.PREF_SESSION_ACTIVE, true)
        Preferences.writeBool(Preferences.PREF_SESSION_HOTSPOT, hotspot)
    }

    private fun clearSessionMarker() {
        Preferences.writeBool(Preferences.PREF_SESSION_ACTIVE, false)
    }

    // Revoking a runtime permission kills the process, so the next launch reports it instead.
    private fun reportAbruptSessionEnd() {
        if (!Preferences.readBool(Preferences.PREF_SESSION_ACTIVE)) return
        val wasHotspot = Preferences.readBool(Preferences.PREF_SESSION_HOTSPOT)
        clearSessionMarker()
        if (wasHotspot && Build.VERSION.SDK_INT >= 33 && !isNearbyGranted()) {
            viewModelScope.launch {
                // Nothing collects events this early in launch; an earlier toast would be dropped.
                _events.subscriptionCount.first { it > 0 }
                postToast(R.string.hotspot_lost_nearby)
            }
        }
    }

    fun trySavePort(port: Int?) {
        viewModelScope.launch {
            val ownPort = serverController.isRunning && port == serverController.port
            if (port != null && !ownPort && NetworkUtils.isPortUsed(port)) {
                postToast(R.string.settings_server_port_dialog_portinuse)
                return@launch
            }
            Preferences.writeInt(Preferences.PREF_SERVER_PORT, port)
            // Don't touch `port` here -- it must keep reflecting whatever the server is
            // actually bound to until it's restarted, not the newly saved preference.
            _uiState.update { it.copy(savedPort = port ?: 0) }
            postToast(R.string.settings_server_port_dialog_saved)
            _events.tryEmit(UiEvent.PortSaved)
        }
    }

    fun toggleDam() {
        fileRepo.directAccessMode = !fileRepo.directAccessMode
        Preferences.writeBool(Preferences.PREF_CLIENT_DAM, fileRepo.directAccessMode)
        _uiState.update { it.copy(damEnabled = fileRepo.directAccessMode, damEligible = isDamEligible()) }
    }

    private fun isDamBuildEligible(): Boolean =
        ManifestUtils.isDirectAccessModeEligible(getApplication())

    fun toggleFiu() {
        val newValue = !_uiState.value.fiuEnabled
        Preferences.writeBool(Preferences.PREF_CLIENT_FIU, newValue)
        _uiState.update { it.copy(fiuEnabled = newValue) }
    }

    fun setThemeMode(mode: ThemeMode) {
        Preferences.writeString(Preferences.PREF_THEME_MODE, mode.name)
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun restoreDefaults() {
        Preferences.clear()
        fileRepo.directAccessMode = false
        _uiState.update {
            it.copy(
                damEnabled = false,
                fiuEnabled = false,
                allInterfacesEnabled = false,
                hotspotBand = null,
                idleStopMinutes = DEFAULT_IDLE_STOP_MINUTES,
                savedPort = 0,
                damEligible = isDamEligible(),
                themeMode = ThemeMode.SYSTEM,
            )
        }
        postToast(R.string.settings_general_defaults_success)
    }

    fun isDamEligible(): Boolean = hasDirectAccessPermission() || Build.VERSION.SDK_INT <= 29

    fun hasDirectAccessPermission(): Boolean = if (Build.VERSION.SDK_INT >= 30) {
        Environment.isExternalStorageManager()
    } else {
        isGranted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun directoryLister(): FileRepository = fileRepo

    private fun setLoading(loading: Boolean) {
        // importProgress is only cleared when a new operation STARTS (loading = true), not when
        // one finishes -- clearing it on completion too raced with MainScreen's own
        // snapshotFlow-based collection of this value: snapshotFlow's channel is conflated, so if
        // this reset to null landed before the collector had consumed the prior (real, final)
        // progress value, that terminal value could be silently dropped in favor of the null,
        // leaving the progress bar permanently stuck just short of 100%.
        _uiState.update {
            it.copy(actionButtonLoading = loading, importProgress = if (loading) null else it.importProgress)
        }
    }

    /** True (after toasting) if an import/action is already in progress and this call should bail. */
    private fun rejectIfBusy(): Boolean {
        if (!_uiState.value.actionButtonLoading) return false
        postToast(R.string.info_pending_fileprocessing)
        return true
    }

    private fun postToast(resId: Int, vararg args: String) {
        _events.tryEmit(UiEvent.Toast(ToastEvent(resId, args.toList())))
    }

    override fun onCleared() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
        stopFileObserver()
        serverController.stop()
        hotspotController?.stop()
        clearSessionMarker()
        // viewModelScope's collector is already cancelled.
        ServingState.setNotice(null)
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
