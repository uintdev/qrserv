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
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.uint.qrserv.R
import dev.uint.qrserv.data.AddressGroup
import dev.uint.qrserv.data.AppUiState
import dev.uint.qrserv.data.DEFAULT_IDLE_STOP_MINUTES
import dev.uint.qrserv.data.FileInfo
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
import dev.uint.qrserv.net.HotspotInfo
import dev.uint.qrserv.net.HotspotLoss
import dev.uint.qrserv.net.NetworkUtils
import dev.uint.qrserv.net.addressChange
import dev.uint.qrserv.server.ServerController
import dev.uint.qrserv.server.ServingNotice
import dev.uint.qrserv.server.ServingService
import dev.uint.qrserv.server.ServingState
import dev.uint.qrserv.util.ManifestUtils
import kotlinx.coroutines.CoroutineScope
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
    val busy: Boolean,
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

    private val hotspotHost: HotspotSession.Host = object : HotspotSession.Host {
        override fun postToast(resId: Int, vararg args: String) = this@QRServViewModel.postToast(resId, *args)
        override fun setLoading(loading: Boolean) = this@QRServViewModel.setLoading(loading)
        override fun rejectIfBusy(): Boolean = this@QRServViewModel.rejectIfBusy()
        override fun isServerRunning(): Boolean = serverController.isRunning
        override fun openPickerWhenReady() {
            if (notificationPromptOpen) pickerAfterNotificationPrompt = true else openPicker()
        }
        override suspend fun startServing(fileInfo: FileInfo) = this@QRServViewModel.startServing(fileInfo)
        override suspend fun stopServing() = this@QRServViewModel.stopServing()
        override suspend fun switchToHotspot(info: HotspotInfo) = this@QRServViewModel.switchToHotspot(info)
        override fun endShare() {
            viewModelScope.launch {
                withContext(Dispatchers.IO) { serverController.stop() }
                onServerStopped()
            }
        }
    }

    val hotspot: HotspotSession = HotspotSession(application, viewModelScope, _uiState, hotspotHost)

    private val serverController = ServerController(
        downloadStartedCallback = { ip, resumed ->
            serverRequests.update { it + 1 }
            hotspot.recordClient(ip)
            if (!resumed) postToast(R.string.server_info_download_started, ip)
        },
        downloadFinishedCallback = { ip ->
            postToast(R.string.server_info_download_finished, ip)
        },
        fileMissingCallback = { onServerStopped() },
        fileUnreadableCallback = { detail ->
            _uiState.update { it.copy(errorDetail = detail) }
            onServerStopped(PageType.UNHANDLED_ERROR)
        },
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
        val fiu = Preferences.readBool(Preferences.PREF_CLIENT_FIU)
        val port = Preferences.readInt(Preferences.PREF_SERVER_PORT) ?: 0
        val themeMode = readPersistedThemeMode()
        _uiState.update {
            hotspot.initialState(it).copy(
                damEnabled = Preferences.readBool(Preferences.PREF_CLIENT_DAM),
                fiuEnabled = fiu,
                allInterfacesEnabled = Preferences.readBool(Preferences.PREF_SERVER_ALL_INTERFACES),
                idleStopMinutes = Preferences.readInt(Preferences.PREF_SERVER_IDLE_MINUTES)?.takeIf { it in IdleStopOptions }
                    ?: DEFAULT_IDLE_STOP_MINUTES,
                port = port,
                savedPort = port,
                damEligible = isDamEligible(),
                damBuildIneligible = !isDamBuildEligible(),
                themeMode = themeMode,
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
                        busy = state.actionButtonLoading,
                    )
                },
                ServingState.activeTransfers,
                serverRequests,
            ) { key, transfers, requests -> key.copy(downloading = transfers > 0, requests = requests) }
                .distinctUntilChanged()
                .collectLatest { key ->
                    if (!key.sharing || key.minutes <= 0 || key.downloading || key.busy) return@collectLatest
                    delay(key.minutes.minutes)
                    stopForIdle(key.minutes)
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
        if (Preferences.wasAskedOnThisInstall(Preferences.PREF_NOTIFICATIONS_ASKED)) return
        if (!isGranted(Manifest.permission.POST_NOTIFICATIONS)) {
            notificationPromptOpen = true
            _uiState.update { it.copy(notificationPermissionPending = true) }
        }
    }

    fun onNotificationPermissionRequested() {
        Preferences.markAskedOnThisInstall(Preferences.PREF_NOTIFICATIONS_ASKED)
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
        if (_uiState.value.damEnabled) {
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
        hotspot.abandonIdle()
    }

    fun onFilesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) {
            hotspot.abandonIdle()
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
            hotspot.abandonIdle()
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
        val sharing = _uiState.value.let { it.serverRunning && !it.serverPoweringDown }
        when (result) {
            is ImportResult.Success -> {
                stopFileObserver()
                _uiState.update { it.copy(fileInfo = result.fileInfo) }
                fileRepo.pruneCacheKeeping(result.fileInfo.path)
                startServing(result.fileInfo)
            }
            ImportResult.EmptySelection -> hotspot.abandonIdle()
            ImportResult.FileGone -> importFailed(sharing, PageType.FILE_REMOVED, R.string.page_info_fileremoved_msg)
            ImportResult.DirectAccessPathMissing -> {
                postToast(R.string.dam_path_not_found)
                hotspot.abandonIdle()
            }
            ImportResult.InsufficientStorage ->
                importFailed(sharing, PageType.INSUFFICIENT_STORAGE, R.string.page_info_insufficientstorage_msg)
            is ImportResult.SelectionFailed ->
                importFailed(sharing, PageType.UNHANDLED_ERROR, R.string.page_info_unhandlederror_msg, result.message)
        }
        if (result !is ImportResult.Success && sharing) startFileObserver(_uiState.value.fileInfo)
        setLoading(false)
    }

    private suspend fun importFailed(sharing: Boolean, page: PageType, @StringRes toastRes: Int, detail: String = "") {
        if (sharing) {
            postToast(toastRes)
            return
        }
        _uiState.update { it.copy(pageType = page, errorDetail = detail) }
        stopServing()
    }

    private suspend fun startServing(fileInfo: FileInfo) {
        rebindJob?.join()
        if (!serverController.isRunning) addressesAtManualPick = emptySet()
        val hotspot = _uiState.value.hotspot
        if (hotspot != null && !this.hotspot.isActive) {
            this.hotspot.onLost(HotspotLoss.STOPPED)
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
        val rebindRequired = this.hotspot.consumeRebindRequired()
        if (!serverController.isRunning || serverController.bindAddress != bindAddress || rebindRequired) {
            try {
                startServer(bindAddress, port = serverController.port.takeIf { serverController.isRunning })
            } catch (_: Exception) {
                postToast(R.string.info_exception_portinuse)
                stopServing()
                _uiState.update { it.copy(pageType = PageType.PORT_IN_USE) }
                return
            }
        }

        val openHotspotScreen = hotspot != null && this.hotspot.claimScreenOpening()

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

    private fun onServerStopped(page: PageType? = null) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            stopFileObserver()
            clearSessionMarker()
            val stoppedPage = page ?: pageAfterStop
            pageAfterStop = PageType.LANDING
            withContext(Dispatchers.IO) { deletePickerCache() }
            hotspot.stopSession()
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
        CacheManager.deleteCache(fileRepo.pickerDir())
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
        hotspot.stopSession()
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
                    val session = serverController.sessionId
                    viewModelScope.launch {
                        if (serverController.sessionId != session) return@launch
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
                    val session = serverController.sessionId
                    viewModelScope.launch {
                        if (serverController.sessionId != session) return@launch
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

    fun onAppResumed() {
        addressCheckRequests.tryEmit(Unit)
        _uiState.update { it.copy(vpnLockdown = NetworkUtils.isVpnLockdownEnabled(getApplication())) }
        hotspot.onAppResumed()
        val granted = areNotificationsGranted()
        if (granted && !notificationsGranted && ServingState.notice.value != null) ServingService.start(getApplication())
        notificationsGranted = granted
    }

    private suspend fun switchToHotspot(info: HotspotInfo) {
        rebindJob?.join()
        val previousBind = serverController.bindAddress
        if (runCatching { startServer(bindAddress = info.address) }.isFailure) {
            hotspot.stopSession()
            postToast(R.string.hotspot_failed_generic)
            if (runCatching { startServer(bindAddress = previousBind) }.isSuccess) {
                _uiState.update { it.copy(port = serverController.port) }
            } else {
                showPortInUse()
            }
            setLoading(false)
            return
        }
        hotspot.markScreenShown()
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
            hotspot.stopSession()
            withContext(Dispatchers.IO) { serverController.stop() }
            startServing(_uiState.value.fileInfo)
            setLoading(false)
        }
    }

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
        if (wasHotspot && Build.VERSION.SDK_INT >= 33 && !hotspot.isNearbyGranted()) {
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
        val enabled = !_uiState.value.damEnabled
        Preferences.writeBool(Preferences.PREF_CLIENT_DAM, enabled)
        _uiState.update { it.copy(damEnabled = enabled, damEligible = isDamEligible()) }
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
        hotspot.onCleared()
        clearSessionMarker()
        // viewModelScope's collector is already cancelled.
        ServingState.setNotice(null)
        CoroutineScope(Dispatchers.IO).launch { deletePickerCache() }
    }
}

