package dev.uint.qrserv.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.uint.qrserv.R
import dev.uint.qrserv.data.AppUiState
import dev.uint.qrserv.data.FileInfo
import dev.uint.qrserv.data.ImportProgress
import dev.uint.qrserv.data.PageType
import dev.uint.qrserv.data.Preferences
import dev.uint.qrserv.data.ThemeMode
import dev.uint.qrserv.data.readPersistedThemeMode
import dev.uint.qrserv.files.CacheManager
import dev.uint.qrserv.files.FileRepository
import dev.uint.qrserv.files.ImportResult
import dev.uint.qrserv.net.NetworkUtils
import dev.uint.qrserv.server.ServerController
import dev.uint.qrserv.util.ManifestUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
            )
        }
    }

    fun onImportClicked() {
        if (rejectIfBusy()) return
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
    }

    fun onFilesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
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
            postToast(R.string.page_info_permissiondenied_msg)
        }
    }

    /** Called by the DAM browser when a permission check fails mid-browse (permission revoked). */
    fun onDamPermissionRevoked() {
        postToast(R.string.page_info_permissiondenied_msg)
    }

    fun onDirectAccessFileChosen(path: String) {
        setLoading(true)
        stopFileObserver()
        viewModelScope.launch {
            val result = fileRepo.importDirectAccessFile(path)
            handleImportResult(result)
        }
    }

    fun onSharedFilesReceived(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (rejectIfBusy()) return
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
            ImportResult.EmptySelection -> {}
            ImportResult.FileGone -> {
                _uiState.update { it.copy(pageType = PageType.FILE_REMOVED) }
                stopServing()
            }
            ImportResult.DirectAccessPathMissing -> {
                postToast(R.string.dam_path_not_found)
            }
            is ImportResult.SelectionFailed -> {
                _uiState.update { it.copy(pageType = PageType.UNHANDLED_ERROR, errorDetail = result.message) }
                stopServing()
            }
        }
        setLoading(false)
    }

    private suspend fun startServing(fileInfo: FileInfo) {
        val interfaces = withContext(Dispatchers.IO) { NetworkUtils.listInterfaces() }
        if (interfaces.isEmpty()) {
            _uiState.update { it.copy(pageType = PageType.NO_CONNECTION, interfaces = emptyList()) }
            stopServing()
            return
        }

        if (!serverController.isRunning) {
            val requestedPort = Preferences.readInt(Preferences.PREF_SERVER_PORT) ?: 0
            try {
                withContext(Dispatchers.IO) {
                    serverController.start(
                        requestedPort,
                        fileInfoProvider = { _uiState.value.fileInfo },
                        hasStoragePermission = { path -> !fileRepo.directModeDetect(path) || hasDirectAccessPermission() },
                    )
                }
            } catch (e: Exception) {
                postToast(R.string.info_exception_portinuse, e.toString())
                _uiState.update { it.copy(pageType = PageType.PORT_IN_USE) }
                return
            }
        }

        val selected = _uiState.value.selectedIp.takeIf { it in interfaces } ?: interfaces.first()

        _uiState.update {
            it.copy(
                interfaces = interfaces,
                selectedIp = selected,
                port = serverController.port,
                serverRunning = true,
                pageType = PageType.IMPORTED,
            )
        }

        startFileObserver(fileInfo)
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
        viewModelScope.launch(Dispatchers.IO) {
            serverController.stop()
            onServerStopped()
        }
    }

    private fun onServerStopped() {
        stopFileObserver()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                CacheManager.deleteCache(
                    fileRepo.pickerDir(),
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

    private suspend fun stopServing() = withContext(Dispatchers.IO) {
        serverController.stop()
        stopFileObserver()
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
    }
}
