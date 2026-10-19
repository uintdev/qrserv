package dev.uint.qrserv.server

import dev.uint.qrserv.data.FileInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

class ServerController(
    private val downloadStartedCallback: (String, Boolean) -> Unit,
    private val downloadFinishedCallback: (String) -> Unit,
    private val fileMissingCallback: () -> Unit,
    private val fileUnreadableCallback: (String) -> Unit,
    private val permissionDeniedCallback: () -> Unit,
    private val serverGoneCallback: () -> Unit,
) {

    @Volatile
    private var server: FileServer? = null

    val isRunning: Boolean
        get() = server != null

    val port: Int
        get() = server?.listeningPort ?: 0

    var bindAddress: String? = null
        private set

    @Volatile
    var sessionId = 0L
        private set

    // A replaced server can still report from requests it was serving -- a late transfer-ended
    // would undercount the new session, and a late error would stop it -- so only this one counts.
    @Volatile
    private var activeListener: InstanceListener? = null

    // Not the controller's own lock: stop() holds that while the engine shuts down (up to 200ms).
    private val transferLock = Any()

    internal val currentListener: FileServer.Listener?
        get() = activeListener

    /** Starts the server on [requestedPort] (0 = OS-assigned ephemeral port). Throws on bind failure. */
    @Synchronized
    @Throws(IOException::class)
    fun start(
        requestedPort: Int,
        fileInfoProvider: () -> FileInfo?,
        hasStoragePermission: (String) -> Boolean,
        bindAddress: String? = null,
    ) {
        stop()
        val listener = InstanceListener()
        activeListener = listener
        val instance = FileServer(requestedPort, bindAddress, fileInfoProvider, hasStoragePermission, listener)
        instance.start()
        server = instance
        this.bindAddress = bindAddress
        sessionId++
    }

    @Synchronized
    fun stop() {
        synchronized(transferLock) {
            activeListener = null
            ServingState.resetTransfers()
        }
        server?.stop()
        server = null
        bindAddress = null
        sessionId++
    }

    private inner class InstanceListener : FileServer.Listener {
        private val isCurrent: Boolean
            get() = activeListener === this

        override fun onDownloadStarted(remoteIp: String, resumed: Boolean) {
            synchronized(transferLock) {
                if (!isCurrent) return
                ServingState.transferStarted()
            }
            downloadStartedCallback(remoteIp, resumed)
        }

        // Passed on regardless: the whole file was sent, even if the server has since been replaced.
        override fun onDownloadFinished(remoteIp: String) {
            downloadFinishedCallback(remoteIp)
        }

        override fun onTransferEnded() {
            synchronized(transferLock) {
                if (isCurrent) ServingState.transferEnded()
            }
        }

        override fun onServerGone() {
            if (!isCurrent) return
            serverGoneCallback()
            stopDeferred()
        }

        override fun onFileMissing() {
            if (!isCurrent) return
            fileMissingCallback()
            stopDeferred()
        }

        override fun onFileUnreadable(error: IOException) {
            if (!isCurrent) return
            fileUnreadableCallback(error.toString())
            stopDeferred()
        }

        override fun onPermissionDenied() {
            if (!isCurrent) return
            permissionDeniedCallback()
            stopDeferred()
        }

        /**
         * Stops the server slightly after the current request finishes, since these callbacks
         * fire from inside the route handler before Ktor has flushed the (small, bodiless) response.
         */
        private fun stopDeferred() {
            CoroutineScope(Dispatchers.IO).launch {
                delay(200.milliseconds)
                synchronized(this@ServerController) {
                    if (isCurrent) stop()
                }
            }
        }
    }
}
