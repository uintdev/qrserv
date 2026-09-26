package dev.uint.qrserv.server

import dev.uint.qrserv.data.FileInfo
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.origin
import io.ktor.server.request.path
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
        fun onDownloadStarted(remoteIp: String, resumed: Boolean)
        fun onDownloadFinished(remoteIp: String)

        /** Paired with every [onDownloadStarted], whether the transfer completed or not. */
        fun onTransferEnded()
        fun onServerGone()
        fun onFileMissing()
        fun onFileUnreadable(error: IOException)
        fun onPermissionDenied()
    }

    private var server: EmbeddedServer<*, *>? = null

    private val scope = CoroutineScope(SupervisorJob())

    @Volatile
    private var running = false

    private val engineFailureHandler = CoroutineExceptionHandler { _, _ ->
        if (running) listener.onServerGone()
    }

    var listeningPort: Int = 0
        private set

    fun start() {
        val embedded = scope.embeddedServer(
            CIO,
            port = port,
            host = bindAddress ?: "0.0.0.0",
            parentCoroutineContext = engineFailureHandler,
        ) {
            routing {
                route("{...}") {
                    get { serve(call, headOnly = false) }
                    head { serve(call, headOnly = true) }
                    handle {
                        call.response.header(HttpHeaders.Allow, "GET, HEAD")
                        call.respondText("", status = HttpStatusCode.MethodNotAllowed)
                    }
                }
            }
        }
        try {
            embedded.start(wait = false)
            listeningPort = runBlocking { embedded.engine.resolvedConnectors().first().port }
        } catch (error: Exception) {
            embedded.stop(gracePeriodMillis = 0, timeoutMillis = 200)
            scope.cancel()
            throw error
        }
        server = embedded
        running = true
    }

    private suspend fun serve(call: ApplicationCall, headOnly: Boolean) {
        val remoteIp = call.request.origin.remoteHost

        val info = fileInfoProvider()
        if (info != null && !pathMatchesFile(call.request.path(), info.name)) {
            call.respondText("", status = HttpStatusCode.NotFound)
            return
        }
        val file = info?.path?.takeIf { it.isNotEmpty() }?.let { File(it) }

        // Checked before file.exists(): on scoped storage, losing MANAGE_EXTERNAL_STORAGE
        // makes exists() report false too, which would otherwise be indistinguishable from
        // the file genuinely being gone and fall through to the wrong (silent) NOT_FOUND path.
        if (info != null && file != null && !hasStoragePermission(info.path)) {
            listener.onPermissionDenied()
            call.respondText("", status = HttpStatusCode.Forbidden)
            return
        }

        if (info == null || file == null || !file.exists()) {
            listener.onFileMissing()
            call.respondText("", status = HttpStatusCode.NotFound)
            return
        }

        // Doubles as the readability probe this used to do with a throwaway open.
        val (stream, length) = try {
            openWithLength(file)
        } catch (error: IOException) {
            listener.onFileUnreadable(error)
            call.respondText("", status = HttpStatusCode.InternalServerError)
            return
        }

        val entityTag = entityTag(file, length)
        call.response.header("Content-Disposition", contentDispositionHeader(info.name))
        call.response.header("X-Content-Type-Options", "nosniff")
        call.response.header(HttpHeaders.AcceptRanges, "bytes")
        call.response.header(HttpHeaders.ETag, entityTag)

        if (!ifMatchMatches(call.request.headers[HttpHeaders.IfMatch], entityTag)) {
            stream.close()
            call.respondText("", status = HttpStatusCode.PreconditionFailed)
            return
        }

        if (headOnly) {
            stream.close()
            call.respond(object : OutgoingContent.NoContent() {
                override val contentType = ContentType.Application.OctetStream
                override val contentLength: Long = length
            })
            return
        }

        val requested = call.request.headers[HttpHeaders.Range]?.takeIf {
            rangeValidated(call.request.headers[HttpHeaders.IfRange], call.request.headers[HttpHeaders.IfMatch], entityTag)
        }
        val range = when (val parsed = byteRange(requested, length)) {
            ByteRange.Full -> null
            is ByteRange.Partial -> parsed
            ByteRange.Unsatisfiable -> {
                stream.close()
                call.response.header(HttpHeaders.ContentRange, "bytes */$length")
                call.respondText("", status = HttpStatusCode.RequestedRangeNotSatisfiable)
                return
            }
        }
        val start = range?.start ?: 0L
        val end = range?.end ?: (length - 1)
        if (range != null) {
            call.response.header(HttpHeaders.ContentRange, "bytes $start-$end/$length")
            try {
                stream.channel.position(start)
            } catch (error: IOException) {
                stream.close()
                listener.onFileUnreadable(error)
                call.respondText("", status = HttpStatusCode.InternalServerError)
                return
            }
        }

        listener.onDownloadStarted(remoteIp, resumed = start > 0)
        try {
            call.respond(object : OutgoingContent.ReadChannelContent() {
                override val status = if (range != null) HttpStatusCode.PartialContent else HttpStatusCode.OK
                override val contentType = ContentType.Application.OctetStream
                override val contentLength = end - start + 1
                override fun readFrom(): ByteReadChannel = LimitedInputStream(stream, end - start + 1).toByteReadChannel()
            })
            // Deliberately not in the finally below: respond() throws if the client
            // disconnects part-way, if the write fails, or if Ktor's own
            // Content-Length check rejects the body -- and announcing a finished
            // download for a transfer the other end never received is worse than
            // saying nothing at all.
            if (end == length - 1) listener.onDownloadFinished(remoteIp)
        } finally {
            // Ktor cancels the channel -- closing the stream with it -- once the body has
            // been written, but not if it never got as far as asking for the channel at all.
            // Closing an already-closed FileInputStream is a no-op.
            runCatching { stream.close() }
            listener.onTransferEnded()
        }
    }

    fun stop() {
        running = false
        server?.stop(gracePeriodMillis = 0, timeoutMillis = 200)
        server = null
        scope.cancel()
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
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!#$&+-.^_`|~"

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

internal fun contentDispositionHeader(rawName: String): String {
    val name = sanitizeFileName(rawName)
    // Anything outside printable ASCII -- a header-injecting CR/LF included -- can't go in the
    // plain filename parameter; filename* below carries the real name for clients that read it.
    val asciiFallback = name
        .map { if (it.code in 0x20..0x7E) it else '_' }
        .joinToString("")
        .replace("\"", "\\\"")
    return "attachment; filename=\"$asciiFallback\"; filename*=UTF-8''${rfc5987Encode(name)}"
}
