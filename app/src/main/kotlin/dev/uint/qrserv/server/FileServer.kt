package dev.uint.qrserv.server

import dev.uint.qrserv.data.FileInfo
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.origin
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.cio.readChannel
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class FileServer(
    private val port: Int,
    private val fileInfoProvider: () -> FileInfo?,
    private val hasStoragePermission: (String) -> Boolean,
    private val listener: Listener,
) {

    interface Listener {
        fun onDownloadStarted(remoteIp: String)
        fun onDownloadFinished(remoteIp: String)
        fun onServerGone(message: String)
        fun onFileMissing()
        fun onPermissionDenied()
    }

    private var server: EmbeddedServer<*, *>? = null

    var listeningPort: Int = 0
        private set

    fun start() {
        val embedded = embeddedServer(CIO, port = port) {
            routing {
                route("{...}") {
                    handle {
                        val remoteIp = call.request.origin.remoteHost

                        val info = fileInfoProvider()
                        val file = info?.path?.takeIf { it.isNotEmpty() }?.let { File(it) }

                        // Checked before file.exists(): on scoped storage, losing MANAGE_EXTERNAL_STORAGE
                        // makes exists() report false too, which would otherwise be indistinguishable from
                        // the file genuinely being gone and fall through to the wrong (silent) NOT_FOUND path.
                        if (info != null && file != null && !hasStoragePermission(info.path)) {
                            listener.onPermissionDenied()
                            call.respondText("", status = HttpStatusCode.Forbidden)
                            return@handle
                        }

                        if (info == null || file == null || !file.exists()) {
                            listener.onFileMissing()
                            call.respondText("", status = HttpStatusCode.NotFound)
                            return@handle
                        }

                        try {
                            FileInputStream(file).close()
                        } catch (error: IOException) {
                            listener.onServerGone(error.toString())
                            call.respondText("", status = HttpStatusCode.InternalServerError)
                            return@handle
                        }

                        call.response.header("Content-Disposition", contentDispositionHeader(info.name))
                        call.response.header("X-Content-Type-Options", "nosniff")

                        listener.onDownloadStarted(remoteIp)
                        try {
                            call.respond(object : OutgoingContent.ReadChannelContent() {
                                override val contentType = ContentType.Application.OctetStream
                                override val contentLength = file.length()
                                override fun readFrom(): ByteReadChannel = file.readChannel()
                            })
                        } finally {
                            listener.onDownloadFinished(remoteIp)
                        }
                    }
                }
            }
        }
        embedded.start(wait = false)
        listeningPort = runBlocking { embedded.engine.resolvedConnectors().first().port }
        server = embedded
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 0, timeoutMillis = 200)
        server = null
    }
}

private const val Rfc5987AttrChars =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!#\$&+-.^_`|~"

private fun rfc5987Encode(name: String): String =
    name.toByteArray(Charsets.UTF_8).joinToString("") { byte ->
        val char = byte.toInt().toChar()
        if (char.code < 128 && Rfc5987AttrChars.contains(char)) char.toString() else "%%%02X".format(byte.toInt() and 0xFF)
    }

private fun contentDispositionHeader(name: String): String {
    val asciiFallback = name
        .map { if (it.code in 0x20..0x7E) it else '_' }
        .joinToString("")
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .ifBlank { "download" }
    return "attachment; filename=\"$asciiFallback\"; filename*=UTF-8''${rfc5987Encode(name)}"
}
