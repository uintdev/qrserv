package dev.uint.qrserv.server

import dev.uint.qrserv.data.FileInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

class ServerControllerTest {

    private lateinit var file: File
    private lateinit var info: FileInfo
    private val downloadsResumed = AtomicInteger()
    private val filesMissing = AtomicInteger()
    private val controller = ServerController(
        downloadStartedCallback = { _, resumed -> if (resumed) downloadsResumed.incrementAndGet() },
        downloadFinishedCallback = {},
        fileMissingCallback = { filesMissing.incrementAndGet() },
        fileUnreadableCallback = {},
        permissionDeniedCallback = {},
        serverGoneCallback = {},
    )

    @Before
    fun setUp() {
        file = File.createTempFile("qrserv", ".bin").apply { writeBytes(ByteArray(1234) { it.toByte() }) }
        info = FileInfo(name = "report.pdf", path = file.path, pathPart = file.parent, length = file.length())
    }

    @After
    fun tearDown() {
        controller.stop()
        file.delete()
    }

    private fun start() = controller.start(0, { info }, { true }, bindAddress = "127.0.0.1")

    /** Starts a new server and returns the listener of the one it replaced. */
    private fun restart(): FileServer.Listener {
        val replaced = controller.currentListener!!
        start()
        return replaced
    }

    private fun open(method: String, headers: Map<String, String> = emptyMap()): HttpURLConnection =
        (URL("http://127.0.0.1:${controller.port}/").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            headers.forEach(::setRequestProperty)
        }

    private fun awaitTransfers(expected: Int): Int {
        val deadline = System.currentTimeMillis() + 1000
        while (ServingState.activeTransfers.value != expected && System.currentTimeMillis() < deadline) Thread.sleep(10)
        return ServingState.activeTransfers.value
    }

    @Test
    fun replacedServerEndingADownloadLeavesTheNewOneCounted() {
        start()
        controller.currentListener!!.onDownloadStarted("127.0.0.2", resumed = false)
        val replaced = restart()
        assertEquals(0, ServingState.activeTransfers.value)
        controller.currentListener!!.onDownloadStarted("127.0.0.2", resumed = true)
        replaced.onTransferEnded()
        assertEquals(1, ServingState.activeTransfers.value)
    }

    @Test
    fun replacedServerErrorLeavesTheNewOneRunning() {
        start()
        val replaced = restart()
        replaced.onFileMissing()
        Thread.sleep(400)
        assertTrue(controller.isRunning)
        assertEquals(0, filesMissing.get())
    }

    @Test
    fun currentServerErrorStopsIt() {
        start()
        controller.currentListener!!.onFileMissing()
        Thread.sleep(400)
        assertFalse(controller.isRunning)
        assertEquals(1, filesMissing.get())
    }

    @Test
    fun stoppingMidDownloadClearsTheCount() {
        start()
        val stopped = controller.currentListener!!
        stopped.onDownloadStarted("127.0.0.2", resumed = false)
        controller.stop()
        assertEquals(0, ServingState.activeTransfers.value)
        stopped.onTransferEnded()
        assertEquals(0, ServingState.activeTransfers.value)
    }

    @Test
    fun downloadResumesOnTheRestartedServer() {
        start()
        val entityTag = open("HEAD").getHeaderField("ETag")
        restart()
        val connection = open("GET", mapOf("Range" to "bytes=1000-", "If-Range" to entityTag))
        assertEquals(206, connection.responseCode)
        assertEquals(file.readBytes().drop(1000), connection.inputStream.use { it.readBytes() }.toList())
        assertEquals(1, downloadsResumed.get())
        assertEquals(0, awaitTransfers(0))
    }
}
