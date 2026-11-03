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
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

class FileServerTest {

    private lateinit var file: File
    private lateinit var server: FileServer
    private val downloadsStarted = AtomicInteger()
    private val downloadsResumed = AtomicInteger()
    private val downloadsFinished = AtomicInteger()

    @Volatile
    private var lastRemoteIp: String? = null

    @Before
    fun setUp() {
        file = File.createTempFile("qrserv", ".bin").apply { writeBytes(ByteArray(1234) { it.toByte() }) }
        val info = FileInfo(name = "report.pdf", path = file.path, pathPart = file.parent!!, length = file.length())
        server = FileServer(
            port = 0,
            bindAddress = "127.0.0.1",
            fileInfoProvider = { info },
            hasStoragePermission = { true },
            listener = object : FileServer.Listener {
                override fun onDownloadStarted(remoteIp: String, resumed: Boolean) {
                    lastRemoteIp = remoteIp
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
    fun segmentEndingTheFileWaitsForTheOthersBeforeFinishing() {
        // Big enough that the first segment can't fit in the socket buffers and stays in flight until read.
        file.writeBytes(ByteArray(64 * 1024 * 1024))
        val entityTag = entityTag()
        val head = open("GET", headers = mapOf("Range" to "bytes=0-33554431", "If-Range" to entityTag))
        assertEquals(206, head.responseCode)
        val tail = open("GET", headers = mapOf("Range" to "bytes=33554432-", "If-Range" to entityTag))
        assertEquals(206, tail.responseCode)
        tail.body()
        assertEquals(0, awaitFinished())
        head.body()
        assertEquals(1, awaitFinished())
    }

    @Test
    fun segmentCanceledAfterTheEndWasSentStillFinishes() {
        // Chrome's parallel download: a whole-file request, then a range for the rest; the first is
        // canceled once it reaches that range, which can be after the range has completed.
        file.writeBytes(ByteArray(64 * 1024 * 1024))
        val whole = open("GET")
        assertEquals(200, whole.responseCode)
        val rest = open("GET", headers = mapOf("Range" to "bytes=33554432-", "If-Match" to entityTag()))
        assertEquals(206, rest.responseCode)
        rest.body()
        assertEquals(0, awaitFinished())
        whole.disconnect()
        assertEquals(1, awaitFinished())
    }

    @Test
    fun segmentCutShortByStoppingTheServerIsNotFinished() {
        file.writeBytes(ByteArray(64 * 1024 * 1024))
        val whole = open("GET")
        assertEquals(200, whole.responseCode)
        val rest = open("GET", headers = mapOf("Range" to "bytes=33554432-", "If-Match" to entityTag()))
        assertEquals(206, rest.responseCode)
        rest.body()
        server.stop()
        whole.disconnect()
        assertEquals(0, awaitFinished())
    }

    @Test
    fun downloadCompletedJustBeforeStoppingIsFinished() {
        file.writeBytes(ByteArray(8 * 1024 * 1024))
        val whole = open("GET")
        assertEquals(200, whole.responseCode)
        whole.body()
        server.stop()
        assertEquals(1, awaitFinished())
    }

    @Test
    fun canceledDownloadIsNotFinished() {
        file.writeBytes(ByteArray(64 * 1024 * 1024))
        val connection = open("GET")
        assertEquals(200, connection.responseCode)
        connection.inputStream.read(ByteArray(1024 * 1024))
        connection.disconnect()
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
    fun clientIsReportedByAddressNotHostName() {
        open("GET", "/report.pdf").body()
        assertEquals("127.0.0.1", lastRemoteIp)
    }

    @Test
    fun rootSendsTheFile() {
        assertEquals(200, open("GET", "/").responseCode)
    }

    @Test
    fun anotherFileNameIsNotFound() {
        val connection = open("GET", "/older-report.pdf")
        assertEquals(404, connection.responseCode)
        assertEquals(404, open("HEAD", "/older-report.pdf").responseCode)
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

    private class Counts : FileServer.Listener {
        val missing = AtomicInteger()
        val ended = AtomicInteger()
        val finished = AtomicInteger()
        override fun onDownloadStarted(remoteIp: String, resumed: Boolean) = Unit
        override fun onDownloadFinished(remoteIp: String) { finished.incrementAndGet() }
        override fun onTransferEnded() { ended.incrementAndGet() }
        override fun onServerGone() = Unit
        override fun onFileMissing() { missing.incrementAndGet() }
        override fun onFileUnreadable(error: IOException) = Unit
        override fun onPermissionDenied() = Unit
    }

    @Test
    fun aClientThatStopsReadingIsCutAfterTheStallTimeout() {
        val big = File.createTempFile("qrserv", ".big").apply { RandomAccessFile(this, "rw").use { it.setLength(64L * 1024 * 1024) } }
        val info = FileInfo(name = "big.bin", path = big.path, pathPart = big.parent!!, length = big.length())
        val counts = Counts()
        val stalling = FileServer(0, "127.0.0.1", { info }, { true }, counts, stallTimeout = 500.milliseconds)
        stalling.start()
        try {
            java.net.Socket().use { socket ->
                socket.receiveBufferSize = 4096
                socket.connect(java.net.InetSocketAddress("127.0.0.1", stalling.listeningPort))
                socket.getOutputStream().write("GET / HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
                socket.getInputStream().read(ByteArray(1024))
                val deadline = System.currentTimeMillis() + 5000
                while (counts.ended.get() == 0 && System.currentTimeMillis() < deadline) Thread.sleep(20)
                assertEquals(1, counts.ended.get())
                socket.soTimeout = 5000
                val drain = ByteArray(1024 * 1024)
                while (socket.getInputStream().read(drain) != -1) Unit
            }
        } finally {
            stalling.stop()
            big.delete()
        }
    }

    @Test
    fun aClientReadingAboveTheMinimumRateIsNotCut() {
        val big = File.createTempFile("qrserv", ".slow").apply { RandomAccessFile(this, "rw").use { it.setLength(32L * 1024 * 1024) } }
        val info = FileInfo(name = "slow.bin", path = big.path, pathPart = big.parent!!, length = big.length())
        val counts = Counts()
        val serving = FileServer(0, "127.0.0.1", { info }, { true }, counts, stallTimeout = 500.milliseconds)
        serving.start()
        try {
            var total = 0L
            val started = System.currentTimeMillis()
            (URL("http://127.0.0.1:${serving.listeningPort}/").openConnection() as HttpURLConnection).inputStream.use { input ->
                // About 5 MiB/s, over 1 MiB per timeout.
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    total += read
                    Thread.sleep(50)
                }
            }
            assertEquals(big.length(), total)
            assertTrue(System.currentTimeMillis() - started > 2000)
            val deadline = System.currentTimeMillis() + 1000
            while (counts.finished.get() == 0 && System.currentTimeMillis() < deadline) Thread.sleep(10)
            assertEquals(1, counts.finished.get())
        } finally {
            serving.stop()
            big.delete()
        }
    }

    @Test
    fun aDownloaderCutForStallingIsNotFinishedAndCanResume() {
        val big = File.createTempFile("qrserv", ".big").apply { RandomAccessFile(this, "rw").use { it.setLength(64L * 1024 * 1024) } }
        val info = FileInfo(name = "big.bin", path = big.path, pathPart = big.parent!!, length = big.length())
        val counts = Counts()
        val serving = FileServer(0, "127.0.0.1", { info }, { true }, counts, stallTimeout = 500.milliseconds)
        serving.start()
        fun get(range: String, tag: String): HttpURLConnection =
            (URL("http://127.0.0.1:${serving.listeningPort}/").openConnection() as HttpURLConnection).apply {
                setRequestProperty("Range", range)
                setRequestProperty("If-Range", tag)
            }
        try {
            val tag = (URL("http://127.0.0.1:${serving.listeningPort}/").openConnection() as HttpURLConnection).run {
                requestMethod = "HEAD"
                getHeaderField("ETag").also { disconnect() }
            }
            java.net.Socket().use { stalled ->
                stalled.receiveBufferSize = 4096
                stalled.connect(java.net.InetSocketAddress("127.0.0.1", serving.listeningPort))
                stalled.getOutputStream().write("GET / HTTP/1.1\r\nHost: x\r\nRange: bytes=0-\r\nIf-Range: $tag\r\n\r\n".toByteArray())
                stalled.getInputStream().read(ByteArray(1024))
                // The segment ending the file completes while the other is stuck.
                val tail = get("bytes=${big.length() - 1024}-", tag)
                assertEquals(206, tail.responseCode)
                tail.inputStream.use { it.readBytes() }
                val deadline = System.currentTimeMillis() + 5000
                while (counts.ended.get() < 2 && System.currentTimeMillis() < deadline) Thread.sleep(20)
                assertEquals(2, counts.ended.get())
                assertEquals(0, counts.finished.get())
            }
            val resumed = get("bytes=${big.length() - 4096}-", tag)
            assertEquals(206, resumed.responseCode)
            assertEquals(4096, resumed.inputStream.use { it.readBytes() }.size)
        } finally {
            serving.stop()
            big.delete()
        }
    }

    @Test
    fun aFileThatShrinksMidTransferIsNotAFinishedDownload() {
        val big = File.createTempFile("qrserv", ".shrink").apply { RandomAccessFile(this, "rw").use { it.setLength(32L * 1024 * 1024) } }
        val info = FileInfo(name = "big.bin", path = big.path, pathPart = big.parent!!, length = big.length())
        val counts = Counts()
        val serving = FileServer(0, "127.0.0.1", { info }, { true }, counts)
        serving.start()
        try {
            java.net.Socket().use { socket ->
                socket.receiveBufferSize = 4096
                socket.connect(java.net.InetSocketAddress("127.0.0.1", serving.listeningPort))
                socket.getOutputStream().write("GET / HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
                socket.getInputStream().read(ByteArray(1024))
                RandomAccessFile(big, "rw").use { it.setLength(1024L * 1024L) }
                socket.soTimeout = 10000
                val drain = ByteArray(1024 * 1024)
                while (socket.getInputStream().read(drain) != -1) Unit
            }
            val deadline = System.currentTimeMillis() + 5000
            while (counts.ended.get() == 0 && System.currentTimeMillis() < deadline) Thread.sleep(20)
            assertEquals(1, counts.ended.get())
            assertEquals(0, counts.finished.get())
        } finally {
            serving.stop()
            big.delete()
        }
    }

    @Test
    fun aDownloaderThatResumesAfterACutCanStillFinish() {
        val big = File.createTempFile("qrserv", ".resume").apply { RandomAccessFile(this, "rw").use { it.setLength(64L * 1024 * 1024) } }
        val info = FileInfo(name = "big.bin", path = big.path, pathPart = big.parent!!, length = big.length())
        val counts = Counts()
        val serving = FileServer(0, "127.0.0.1", { info }, { true }, counts, stallTimeout = 500.milliseconds)
        serving.start()
        fun rangeGet(start: Long, tag: String): HttpURLConnection =
            (URL("http://127.0.0.1:${serving.listeningPort}/").openConnection() as HttpURLConnection).apply {
                setRequestProperty("Range", "bytes=$start-")
                setRequestProperty("If-Range", tag)
            }
        try {
            val tag = (URL("http://127.0.0.1:${serving.listeningPort}/").openConnection() as HttpURLConnection).run {
                requestMethod = "HEAD"
                getHeaderField("ETag").also { disconnect() }
            }
            // A second segment that keeps moving holds the client's entry open across the cut.
            val moving = rangeGet(big.length() / 2, tag).inputStream
            val buffer = ByteArray(256 * 1024)
            java.net.Socket().use { stalled ->
                stalled.receiveBufferSize = 4096
                stalled.connect(java.net.InetSocketAddress("127.0.0.1", serving.listeningPort))
                stalled.getOutputStream().write("GET / HTTP/1.1\r\nHost: x\r\nRange: bytes=0-\r\nIf-Range: $tag\r\n\r\n".toByteArray())
                stalled.getInputStream().read(ByteArray(1024))
                val deadline = System.currentTimeMillis() + 5000
                while (counts.ended.get() == 0 && System.currentTimeMillis() < deadline) {
                    moving.read(buffer)
                    Thread.sleep(20)
                }
                assertEquals(1, counts.ended.get())
            }
            rangeGet(big.length() - 1024, tag).inputStream.use { it.readBytes() }
            while (moving.read(buffer) != -1) Unit
            moving.close()
            val deadline = System.currentTimeMillis() + 3000
            while (counts.finished.get() == 0 && System.currentTimeMillis() < deadline) Thread.sleep(20)
            assertEquals(1, counts.finished.get())
        } finally {
            serving.stop()
            big.delete()
        }
    }

    // A known limit: Ktor buffers about 1 MiB between file reads, so about 3.5 KB/s at the real 5 minutes.
    @Test
    fun aClientFarBelowTheMinimumRateCountsAsStalled() {
        val big = File.createTempFile("qrserv", ".limit").apply { RandomAccessFile(this, "rw").use { it.setLength(64L * 1024 * 1024) } }
        val info = FileInfo(name = "big.bin", path = big.path, pathPart = big.parent!!, length = big.length())
        val counts = Counts()
        val serving = FileServer(0, "127.0.0.1", { info }, { true }, counts, stallTimeout = 500.milliseconds)
        serving.start()
        try {
            java.net.Socket().use { socket ->
                socket.receiveBufferSize = 4096
                socket.connect(java.net.InetSocketAddress("127.0.0.1", serving.listeningPort))
                socket.getOutputStream().write("GET / HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
                val buffer = ByteArray(4 * 1024)
                val deadline = System.currentTimeMillis() + 10_000
                while (counts.ended.get() == 0 && System.currentTimeMillis() < deadline) {
                    socket.getInputStream().read(buffer)
                    Thread.sleep(40)
                }
                assertEquals(1, counts.ended.get())
                assertEquals(0, counts.finished.get())
            }
        } finally {
            serving.stop()
            big.delete()
        }
    }
}
