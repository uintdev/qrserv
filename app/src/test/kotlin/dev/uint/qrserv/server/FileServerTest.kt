package dev.uint.qrserv.server

import dev.uint.qrserv.data.FileInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
    private val downloadsResumed = AtomicInteger()
    private val downloadsFinished = AtomicInteger()

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
                override fun onDownloadStarted(remoteIp: String, resumed: Boolean) {
                    (if (resumed) downloadsResumed else downloadsStarted).incrementAndGet()
                }
                override fun onDownloadFinished(remoteIp: String) {
                    downloadsFinished.incrementAndGet()
                }
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

    private fun open(method: String, path: String = "/", headers: Map<String, String> = emptyMap()): HttpURLConnection =
        (URL("http://127.0.0.1:${server.listeningPort}$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            headers.forEach(::setRequestProperty)
        }

    private fun HttpURLConnection.body(): ByteArray = inputStream.use { it.readBytes() }

    private fun awaitFinished(): Int {
        val deadline = System.currentTimeMillis() + 1000
        while (downloadsFinished.get() == 0 && System.currentTimeMillis() < deadline) Thread.sleep(10)
        return downloadsFinished.get()
    }

    private fun entityTag(): String = open("HEAD").getHeaderField("ETag")

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
        assertEquals("bytes", connection.getHeaderField("Accept-Ranges"))
        assertTrue(connection.getHeaderField("ETag").matches(Regex("\"[0-9a-f]{24}\"")))
        assertEquals(0, downloadsStarted.get())
    }

    @Test
    fun rangeFromTheMiddleResumes() {
        val connection = open("GET", headers = mapOf("Range" to "bytes=1000-", "If-Range" to entityTag()))
        assertEquals(206, connection.responseCode)
        assertEquals("bytes 1000-1233/1234", connection.getHeaderField("Content-Range"))
        assertEquals(234L, connection.getHeaderField("Content-Length").toLong())
        assertEquals(file.readBytes().drop(1000), connection.body().toList())
        assertEquals(0, downloadsStarted.get())
        assertEquals(1, downloadsResumed.get())
        assertEquals(1, awaitFinished())
    }

    @Test
    fun rangeThatStopsShortIsNotAFinishedDownload() {
        val connection = open("GET", headers = mapOf("Range" to "bytes=0-99", "If-Range" to entityTag()))
        assertEquals(206, connection.responseCode)
        assertEquals("bytes 0-99/1234", connection.getHeaderField("Content-Range"))
        assertEquals(file.readBytes().take(100), connection.body().toList())
        assertEquals(1, downloadsStarted.get())
        assertEquals(0, awaitFinished())
    }

    @Test
    fun matchingIfRangeHonorsTheRange() {
        val connection = open("GET", headers = mapOf("Range" to "bytes=-4", "If-Range" to entityTag()))
        assertEquals(206, connection.responseCode)
        assertEquals(file.readBytes().takeLast(4), connection.body().toList())
    }

    @Test
    fun staleIfRangeSendsTheWholeFile() {
        val connection = open("GET", headers = mapOf("Range" to "bytes=1000-", "If-Range" to "\"0123456789abcdef01234567\""))
        assertEquals(200, connection.responseCode)
        assertEquals(file.readBytes().toList(), connection.body().toList())
    }

    @Test
    fun entityTagChangesWithTheFile() {
        val before = entityTag()
        file.appendBytes(byteArrayOf(1))
        assertNotEquals(before, entityTag())
    }

    @Test
    fun rangeWithoutAValidatorSendsTheWholeFile() {
        val connection = open("GET", headers = mapOf("Range" to "bytes=1000-"))
        assertEquals(200, connection.responseCode)
        assertEquals(null, connection.getHeaderField("Content-Range"))
        assertEquals(file.readBytes().toList(), connection.body().toList())
        assertEquals(1, downloadsStarted.get())
    }

    @Test
    fun staleIfMatchIsRefused() {
        val connection = open("GET", headers = mapOf("Range" to "bytes=1000-", "If-Match" to "\"0123456789abcdef01234567\""))
        assertEquals(412, connection.responseCode)
        assertEquals(0, downloadsStarted.get() + downloadsResumed.get())
    }

    @Test
    fun matchingIfMatchResumes() {
        val connection = open("GET", headers = mapOf("Range" to "bytes=1000-", "If-Match" to entityTag()))
        assertEquals(206, connection.responseCode)
        assertEquals(file.readBytes().drop(1000), connection.body().toList())
    }

    @Test
    fun rangePastTheEndIsUnsatisfiable() {
        val connection = open("GET", headers = mapOf("Range" to "bytes=1234-", "If-Range" to entityTag()))
        assertEquals(416, connection.responseCode)
        assertEquals("bytes */1234", connection.getHeaderField("Content-Range"))
        assertEquals(0, downloadsStarted.get() + downloadsResumed.get())
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
