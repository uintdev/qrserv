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
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class FileServer(
    private val port: Int,
    private val bindAddress: String?,
    private val fileInfoProvider: () -> FileInfo?,
    private val hasStoragePermission: (String) -> Boolean,
    private val listener: Listener,
) {

    interface Listener {
        fun onDownloadStarted(remoteIp: String)
        fun onDownloadFinished(remoteIp: String)

        /** Paired with every [onDownloadStarted], whether the transfer completed or not. */
        fun onTransferEnded()
        fun onServerGone()
        fun onFileMissing()
        fun onPermissionDenied()
    }

    private var server: EmbeddedServer<*, *>? = null

    var listeningPort: Int = 0
        private set

    fun start() {
        val embedded = embeddedServer(CIO, port = port, host = bindAddress ?: "0.0.0.0") {
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

                        // Doubles as the readability probe this used to do with a throwaway open.
                        val (stream, length) = try {
                            openWithLength(file)
                        } catch (_: IOException) {
                            listener.onServerGone()
                            call.respondText("", status = HttpStatusCode.InternalServerError)
                            return@handle
                        }

                        call.response.header("Content-Disposition", contentDispositionHeader(info.name))
                        call.response.header("X-Content-Type-Options", "nosniff")

                        listener.onDownloadStarted(remoteIp)
                        try {
                            call.respond(object : OutgoingContent.ReadChannelContent() {
                                override val contentType = ContentType.Application.OctetStream
                                override val contentLength = length
                                override fun readFrom(): ByteReadChannel = stream.toByteReadChannel()
                            })
                            // Deliberately not in the finally below: respond() throws if the client
                            // disconnects part-way, if the write fails, or if Ktor's own
                            // Content-Length check rejects the body -- and announcing a finished
                            // download for a transfer the other end never received is worse than
                            // saying nothing at all.
                            listener.onDownloadFinished(remoteIp)
                        } finally {
                            // Ktor cancels the channel -- closing the stream with it -- once the body has
                            // been written, but not if it never got as far as asking for the channel at all.
                            // Closing an already-closed FileInputStream is a no-op.
                            runCatching { stream.close() }
                            listener.onTransferEnded()
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

/**
 * Opens [file] and measures it through that same descriptor, so the advertised Content-Length and
 * the bytes actually sent can't disagree. Taking the length from the path instead (File.length(),
 * as Ktor's own File.readChannel() also does internally) stats it separately from the open that
 * streams it -- and Ktor doesn't call readFrom() until after the response object is built, leaving
 * a window in which the file can be replaced or resized. Ktor then copies exactly Content-Length
 * bytes and rejects the mismatch, failing the transfer partway through.
 */
private fun openWithLength(file: File): Pair<FileInputStream, Long> {
    val stream = FileInputStream(file)
    return try {
        stream to stream.channel.size()
    } catch (error: IOException) {
        stream.close()
        throw error
    }
}

private const val Rfc5987AttrChars =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!#\$&+-.^_`|~"

private fun rfc5987Encode(name: String): String =
    name.toByteArray(Charsets.UTF_8).joinToString("") { byte ->
        val char = byte.toInt().toChar()
        if (char.code < 128 && Rfc5987AttrChars.contains(char)) char.toString() else "%%%02X".format(byte.toInt() and 0xFF)
    }

/**
 * Drops anything a client could read as a path rather than a name. RFC 6266 leaves it to the
 * recipient to ignore path information and browsers do, but separators (or a bare "..") are never
 * legitimate in a name we're handing out, so they shouldn't reach a downloader that takes the
 * header literally -- including one on Windows, where a backslash separates paths too.
 */
private fun sanitizeFileName(name: String): String {
    val flattened = name.map { if (it == '/' || it == '\\') '_' else it }.joinToString("")
    return if (flattened.isBlank() || flattened == "." || flattened == "..") "download" else flattened
}

private fun contentDispositionHeader(rawName: String): String {
    val name = sanitizeFileName(rawName)
    // Anything outside printable ASCII -- a header-injecting CR/LF included -- can't go in the
    // plain filename parameter; filename* below carries the real name for clients that read it.
    val asciiFallback = name
        .map { if (it.code in 0x20..0x7E) it else '_' }
        .joinToString("")
        .replace("\"", "\\\"")
    return "attachment; filename=\"$asciiFallback\"; filename*=UTF-8''${rfc5987Encode(name)}"
}
