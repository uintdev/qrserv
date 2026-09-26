package dev.uint.qrserv.server

import dev.uint.qrserv.data.FileInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

class FileServerTest {

    private lateinit var file: File
    private lateinit var server: FileServer
    private val downloadsStarted = AtomicInteger()

    @Before
    fun setUp() {
        file = File.createTempFile("qrserv", ".bin").apply { writeBytes(ByteArray(1234) { it.toByte() }) }
        val info = FileInfo(name = "report.pdf", path = file.path, pathPart = file.parent, length = file.length())
        server = FileServer(
            port = 0,
            bindAddress = "127.0.0.1",
            fileInfoProvider = { info },
            hasStoragePermission = { true },
            listener = object : FileServer.Listener {
                override fun onDownloadStarted(remoteIp: String) {
                    downloadsStarted.incrementAndGet()
                }
                override fun onDownloadFinished(remoteIp: String) = Unit
                override fun onTransferEnded() = Unit
                override fun onServerGone() = Unit
                override fun onFileMissing() = Unit
                override fun onFileUnreadable(error: IOException) = Unit
                override fun onPermissionDenied() = Unit
            },
        )
        server.start()
    }

    @After
    fun tearDown() {
        server.stop()
        file.delete()
    }

    private fun open(method: String, path: String = "/"): HttpURLConnection =
        (URL("http://127.0.0.1:${server.listeningPort}$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
        }

    @Test
    fun getSendsTheFile() {
        val connection = open("GET", "/report.pdf")
        assertEquals(200, connection.responseCode)
        assertEquals(file.readBytes().toList(), connection.inputStream.use { it.readBytes() }.toList())
        assertEquals(1, downloadsStarted.get())
    }

    @Test
    fun headSendsHeadersOnly() {
        val connection = open("HEAD")
        assertEquals(200, connection.responseCode)
        assertEquals(1234L, connection.getHeaderField("Content-Length").toLong())
        assertEquals("attachment; filename=\"report.pdf\"; filename*=UTF-8''report.pdf", connection.getHeaderField("Content-Disposition"))
        assertEquals(0, downloadsStarted.get())
    }

    @Test
    fun otherMethodsAreRefused() {
        val connection = open("POST").apply {
            doOutput = true
            outputStream.use { it.write(1) }
        }
        assertEquals(405, connection.responseCode)
        assertEquals(0, downloadsStarted.get())
    }
}
