package dev.uint.qrserv.viewmodel

import android.app.Application
import android.content.pm.PackageManager
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
import dev.uint.qrserv.data.FileInfo
import dev.uint.qrserv.data.HotspotDialog
import dev.uint.qrserv.data.InterfaceAddress
import dev.uint.qrserv.data.ImportProgress
import dev.uint.qrserv.data.PageType
import dev.uint.qrserv.data.Preferences
import dev.uint.qrserv.data.ThemeMode
import dev.uint.qrserv.data.readPersistedThemeMode
import dev.uint.qrserv.files.CacheManager
import dev.uint.qrserv.files.FileRepository
import dev.uint.qrserv.files.ImportResult
import dev.uint.qrserv.net.HotspotAvailability
import dev.uint.qrserv.net.HotspotController
import dev.uint.qrserv.net.HotspotFailure
import dev.uint.qrserv.net.HotspotInfo
import dev.uint.qrserv.net.HotspotLoss
import dev.uint.qrserv.net.HotspotPreflight
import dev.uint.qrserv.net.HotspotStartResult
import dev.uint.qrserv.net.NetworkUtils
import dev.uint.qrserv.net.hotspotPreflight
import dev.uint.qrserv.net.hotspotUnavailableReason
import dev.uint.qrserv.server.ServerController
import dev.uint.qrserv.server.ServingNotice
import dev.uint.qrserv.server.ServingService
import dev.uint.qrserv.server.ServingState
import dev.uint.qrserv.util.ManifestUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface UiEvent {
    object OpenSafPicker : UiEvent
    object OpenDamBrowser : UiEvent
    object RequestDamPermission : UiEvent
    object PortSaved : UiEvent
    data class Toast(val event: ToastEvent) : UiEvent
}

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

    private val serverController = ServerController(
        downloadStartedCallback = { ip -> postToast(R.string.server_info_download_started, ip) },
        downloadFinishedCallback = { ip ->
            postToast(R.string.server_info_download_finished, ip)
        },
        fileMissingCallback = { onServerStopped() },
        permissionDeniedCallback = {
            postToast(R.string.page_info_permissiondenied_msg)
            onServerStopped()
        },
        serverGoneCallback = { message ->
            postToast(R.string.server_info_gone, message)
            onServerStopped()
        },
    )

    init {
        Preferences.init(application)
        fileRepo.directAccessMode = Preferences.readBool(Preferences.PREF_CLIENT_DAM)
        val fiu = Preferences.readBool(Preferences.PREF_CLIENT_FIU)
        val port = Preferences.readInt(Preferences.PREF_SERVER_PORT) ?: 0
        val themeMode = readPersistedThemeMode()
        _uiState.update {
            it.copy(
                damEnabled = fileRepo.directAccessMode,
                fiuEnabled = fiu,
                port = port,
                savedPort = port,
                damEligible = isDamEligible(),
                damBuildIneligible = !isDamBuildEligible(),
                themeMode = themeMode,
                hotspotAvailability = computeHotspotAvailability(),
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
    }

    private fun servingNoticeFor(state: AppUiState): ServingNotice? = when {
        state.hotspotStarting -> ServingNotice.StartingHotspot
        state.actionButtonLoading -> ServingNotice.Preparing
        state.serverRunning && !state.serverPoweringDown -> {
            val host = if (state.selectedIp.contains(':')) "[${state.selectedIp}]" else state.selectedIp
            ServingNotice.Sharing(state.fileInfo.name, "$host:${state.port}", state.hotspot?.ssid)
        }
        state.hotspot != null -> ServingNotice.StartingHotspot
        else -> null
    }

    // State, not a UiEvent: a share from another app starts before the UI collects events.
    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        if (Preferences.readBool(Preferences.PREF_NOTIFICATIONS_ASKED)) return
        val granted = ContextCompat.checkSelfPermission(
            getApplication(),
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) _uiState.update { it.copy(notificationPermissionPending = true) }
    }

    fun onNotificationPermissionRequested() {
        Preferences.writeBool(Preferences.PREF_NOTIFICATIONS_ASKED, true)
        _uiState.update { it.copy(notificationPermissionPending = false) }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        notificationsGranted = granted
        if (granted && ServingState.notice.value != null) ServingService.start(getApplication())
    }

    private fun areNotificationsGranted(): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(getApplication(), android.Manifest.permission.POST_NOTIFICATIONS) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

    private var notificationsGranted = areNotificationsGranted()

    fun purgeStaleCacheOnLaunch() {
        viewModelScope.launch(Dispatchers.IO) {
            CacheManager.deleteCache(fileRepo.pickerDir(ignoreDam = true), directAccessRoot = FileRepository.DIRECT_ACCESS_ROOT)
        }
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
        onFilesPicked(uris)
    }

    /** Handles a plain-text share (e.g. a URL) by writing it to a generated .txt file and importing that. */
    fun onSharedTextReceived(text: String) {
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
        val hotspot = _uiState.value.hotspot
        if (hotspot != null && hotspotController?.isActive != true) {
            onHotspotLost(HotspotLoss.STOPPED)
            return
        }

        val interfaces = if (hotspot != null) {
            listOf(InterfaceAddress(hotspot.address, AddressGroup.HOSTED))
        } else {
            val listed = withContext(Dispatchers.IO) { NetworkUtils.listInterfaces() }.getOrElse {
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

        if (!serverController.isRunning) {
            try {
                startServer(bindAddress = hotspot?.address)
            } catch (e: Exception) {
                postToast(R.string.info_exception_portinuse, e.toString())
                stopHotspotSession()
                _uiState.update { it.copy(pageType = PageType.PORT_IN_USE) }
                return
            }
        }

        val current = _uiState.value.selectedIp
        val selected = if (interfaces.any { it.address == current }) current else interfaces.first().address
        val openHotspotScreen = hotspot != null && !hotspotScreenShown
        if (openHotspotScreen) hotspotScreenShown = true

        _uiState.update {
            it.copy(
                interfaces = interfaces,
                selectedIp = selected,
                port = serverController.port,
                serverRunning = true,
                pageType = PageType.IMPORTED,
                hotspotScreenPending = it.hotspotScreenPending || openHotspotScreen,
            )
        }
        markSessionActive(hotspot != null)

        startFileObserver(fileInfo)
    }

    private suspend fun startServer(bindAddress: String?) = withContext(Dispatchers.IO) {
        serverController.start(
            Preferences.readInt(Preferences.PREF_SERVER_PORT) ?: 0,
            fileInfoProvider = { _uiState.value.fileInfo },
            hasStoragePermission = { path -> !fileRepo.directModeDetect(path) || hasDirectAccessPermission() },
            bindAddress = bindAddress,
        )
    }

    fun onIpSelected(ip: String) {
        _uiState.update { it.copy(selectedIp = ip) }
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
            withContext(Dispatchers.IO) { serverController.stop() }
            onServerStopped()
        }
    }

    private fun onServerStopped() {
        stopFileObserver()
        stopHotspotSession()
        clearSessionMarker()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                CacheManager.deleteCache(
                    fileRepo.pickerDir(ignoreDam = true),
                    directAccessRoot = FileRepository.DIRECT_ACCESS_ROOT,
                )
            }
            _uiState.update {
                it.copy(
                    serverRunning = false,
                    serverPoweringDown = false,
                    pageType = if (it.pageType == PageType.IMPORTED) PageType.LANDING else it.pageType,
                )
            }
        }
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

    private fun isNearbyGranted(): Boolean = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(getApplication(), android.Manifest.permission.NEARBY_WIFI_DEVICES) ==
        PackageManager.PERMISSION_GRANTED

    fun onAppResumed() {
        _uiState.update { it.copy(hotspotAvailability = computeHotspotAvailability()) }
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
            val result = controller.start()
            _uiState.update { it.copy(hotspotStarting = false) }
            when (result) {
                is HotspotStartResult.Failed -> {
                    setLoading(false)
                    onHotspotStartFailed(result.reason, intent)
                }
                is HotspotStartResult.Started -> {
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
            else -> _uiState.update { it.copy(pageType = PageType.HOTSPOT_FAILED, hotspotFailure = reason) }
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
            openPicker()
        }
    }

    private suspend fun switchToHotspot(info: HotspotInfo) {
        if (runCatching { startServer(bindAddress = info.address) }.isFailure) {
            stopHotspotSession()
            postToast(R.string.hotspot_failed_generic)
            if (runCatching { startServer(bindAddress = null) }.isSuccess) {
                _uiState.update { it.copy(port = serverController.port) }
            } else {
                stopServing()
                _uiState.update { it.copy(pageType = PageType.PORT_IN_USE) }
            }
            setLoading(false)
            return
        }
        hotspotScreenShown = true
        _uiState.update {
            it.copy(
                interfaces = listOf(InterfaceAddress(info.address, AddressGroup.HOSTED)),
                selectedIp = info.address,
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

    fun onHotspotScreenOpened() {
        _uiState.update { it.copy(hotspotScreenPending = false) }
    }

    private fun stopHotspotSession() {
        hotspotScreenShown = false
        _uiState.update { it.copy(hotspot = null, hotspotStarting = false, hotspotScreenPending = false) }
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
            if (port != null && NetworkUtils.isPortUsed(port)) {
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
                savedPort = 0,
                damEligible = isDamEligible(),
                themeMode = ThemeMode.SYSTEM,
            )
        }
        postToast(R.string.settings_general_defaults_success)
    }

    fun isDamEligible(): Boolean = hasDirectAccessPermission() || Build.VERSION.SDK_INT <= 29

    fun hasDirectAccessPermission(): Boolean {
        val context = getApplication<Application>()
        return if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
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
