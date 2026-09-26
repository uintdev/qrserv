package dev.uint.qrserv.server

import dev.uint.qrserv.net.NetworkUtils
import io.ktor.http.decodeURLPart
import java.net.URLEncoder

internal fun shareUrl(ip: String, port: Int, fileName: String?): String {
    val path = fileName?.let { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }.orEmpty()
    return "http://${NetworkUtils.urlHost(ip)}:$port/$path"
}

internal fun pathMatchesFile(rawPath: String, fileName: String): Boolean {
    if (rawPath.isEmpty() || rawPath == "/") return true
    val requested = runCatching { rawPath.removePrefix("/").decodeURLPart() }.getOrNull()
    return requested == fileName
}
