package dev.uint.qrserv.server

import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom

internal sealed interface ByteRange {
    data object Full : ByteRange

    data object Unsatisfiable : ByteRange

    data class Partial(val start: Long, val end: Long) : ByteRange
}

internal fun byteRange(header: String?, length: Long): ByteRange {
    val spec = header?.trim()
        ?.takeIf { it.startsWith("bytes=", ignoreCase = true) }
        ?.substring("bytes=".length)?.trim()
        ?: return ByteRange.Full
    if (',' in spec) return ByteRange.Full
    val dash = spec.indexOf('-')
    if (dash < 0) return ByteRange.Full
    val first = spec.substring(0, dash).trim()
    val last = spec.substring(dash + 1).trim()

    if (first.isEmpty()) {
        val suffix = digits(last) ?: return ByteRange.Full
        if (suffix == 0L || length == 0L) return ByteRange.Unsatisfiable
        return ByteRange.Partial((length - suffix).coerceAtLeast(0), length - 1)
    }
    val start = digits(first) ?: return ByteRange.Full
    val end = if (last.isEmpty()) Long.MAX_VALUE else digits(last)?.takeIf { it >= start } ?: return ByteRange.Full
    if (start >= length) return ByteRange.Unsatisfiable
    return ByteRange.Partial(start, end.coerceAtMost(length - 1))
}

private fun digits(value: String): Long? = value.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toLongOrNull()

/**
 * Only a client naming this exact file gets part of it. Without that, the URL may be serving a
 * different file than the one it has half of, and the bytes would be spliced onto the wrong one.
 * A date never matches, as no Last-Modified is sent.
 */
internal fun rangeValidated(ifRange: String?, ifMatch: String?, entityTag: String): Boolean =
    ifRange?.trim() == entityTag || ifMatch?.split(',')?.any { it.trim() == entityTag } == true

internal fun ifMatchMatches(header: String?, entityTag: String): Boolean =
    header == null || header.split(',').any { it.trim() == "*" || it.trim() == entityTag }

private val entityTagSalt = ByteArray(16).also { SecureRandom().nextBytes(it) }

internal fun entityTag(file: File, length: Long): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(entityTagSalt)
    digest.update("${file.path}\u0000$length\u0000${file.lastModified()}".toByteArray())
    return "\"" + digest.digest().take(12).joinToString("") { "%02x".format(it) } + "\""
}

internal class LimitedInputStream(input: InputStream, private var remaining: Long) : FilterInputStream(input) {
    override fun read(): Int {
        if (remaining <= 0) return -1
        return super.read().also { if (it >= 0) remaining-- }
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (remaining <= 0) return -1
        return super.read(b, off, len.toLong().coerceAtMost(remaining).toInt()).also { if (it > 0) remaining -= it }
    }

    override fun skip(n: Long): Long = super.skip(n.coerceAtMost(remaining)).also { remaining -= it }

    override fun available(): Int = super.available().toLong().coerceAtMost(remaining).toInt()

    override fun markSupported(): Boolean = false
}
