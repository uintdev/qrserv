package dev.uint.qrserv.server

import dev.uint.qrserv.data.FileInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

class ServerController(
    private val downloadStartedCallback: (String) -> Unit,
    private val downloadFinishedCallback: (String) -> Unit,
    private val fileMissingCallback: () -> Unit,
    private val permissionDeniedCallback: () -> Unit,
    private val serverGoneCallback: (String) -> Unit,
) : FileServer.Listener {

    @Volatile
    private var server: FileServer? = null

    val isRunning: Boolean
        get() = server != null

    val port: Int
        get() = server?.listeningPort ?: 0

    /** Starts the server on [requestedPort] (0 = OS-assigned ephemeral port). Throws on bind failure. */
    @Throws(IOException::class)
    fun start(requestedPort: Int, fileInfoProvider: () -> FileInfo?, hasStoragePermission: (String) -> Boolean) {
        stop()
        val instance = FileServer(requestedPort, fileInfoProvider, hasStoragePermission, this)
        instance.start()
        server = instance
    }

    fun stop() {
        server?.stop()
        server = null
    }

    override fun onDownloadStarted(remoteIp: String) {
        downloadStartedCallback(remoteIp)
    }

    override fun onDownloadFinished(remoteIp: String) {
        downloadFinishedCallback(remoteIp)
    }

    override fun onServerGone(message: String) {
        serverGoneCallback(message)
        stopDeferred()
    }

    override fun onFileMissing() {
        fileMissingCallback()
        stopDeferred()
    }

    override fun onPermissionDenied() {
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
            stop()
        }
    }
}
