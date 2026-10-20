package dev.uint.qrserv.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

const val RELEASES_URL = "https://github.com/uintdev/qrserv/releases"

// GitHub's "latest" release is never a pre-release or a draft.
private const val LATEST_RELEASE_API = "https://api.github.com/repos/uintdev/qrserv/releases/latest"

// The whole check, DNS lookup included -- which the connection's own timeouts don't cover.
private val CHECK_TIMEOUT = 15.seconds
private val CONNECT_TIMEOUT = 10.seconds
private val READ_TIMEOUT = 10.seconds

// A release's JSON is a few KB; anything far larger isn't what was asked for.
private const val MAX_RESPONSE_BYTES = 1024 * 1024

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val version: String, val releaseUrl: String) : UpdateCheckResult

    data object UpToDate : UpdateCheckResult

    data class NewerThanLatest(val latestVersion: String) : UpdateCheckResult

    data object Failed : UpdateCheckResult
}

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState

    data object Checking : UpdateCheckState

    data class Done(val result: UpdateCheckResult) : UpdateCheckState
}

/** "v4.2.1" or "4.2.1" as its numbers; null for anything else, such as "4.3.0-beta". */
internal fun parseVersion(tag: String): List<Int>? {
    val parts = tag.removePrefix("v").split('.')
    if (parts.any { part -> part.isEmpty() || !part.all { it in '0'..'9' } }) return null
    return parts.map { it.toIntOrNull() ?: return null }
}

/** Numerically, part by part, with missing trailing parts as 0 (so 4.2 == 4.2.0, and 4.10 > 4.9). */
internal fun compareVersions(a: List<Int>, b: List<Int>): Int {
    for (i in 0 until maxOf(a.size, b.size)) {
        val order = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
        if (order != 0) return order
    }
    return 0
}

/** What GitHub's latest-release response means for [installedVersion]; null [body] for a failed request. */
internal fun updateCheckResult(installedVersion: String, statusCode: Int, body: String?): UpdateCheckResult {
    if (statusCode != HttpURLConnection.HTTP_OK || body == null) return UpdateCheckResult.Failed
    val release = runCatching { JSONObject(body) }.getOrNull() ?: return UpdateCheckResult.Failed
    if (release.optBoolean("prerelease") || release.optBoolean("draft")) return UpdateCheckResult.Failed
    val tag = release.optString("tag_name")
    val latest = parseVersion(tag) ?: return UpdateCheckResult.Failed
    val installed = parseVersion(installedVersion) ?: return UpdateCheckResult.Failed
    val latestVersion = tag.removePrefix("v")
    val order = compareVersions(latest, installed)
    return when {
        order > 0 -> {
            // Only ever this repository's own release pages, whatever the response says.
            val url = release.optString("html_url").takeIf { it.startsWith("$RELEASES_URL/") } ?: RELEASES_URL
            UpdateCheckResult.UpdateAvailable(latestVersion, url)
        }
        order < 0 -> UpdateCheckResult.NewerThanLatest(latestVersion)
        else -> UpdateCheckResult.UpToDate
    }
}

// Not a child of the caller: a stalled DNS lookup can't be interrupted, and the deadline mustn't wait for it.
private val requestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Asks GitHub for its latest release. Never throws: anything that goes wrong is [UpdateCheckResult.Failed]. */
suspend fun checkForUpdate(installedVersion: String): UpdateCheckResult {
    val connection = runCatching { URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection }
        .getOrElse { return UpdateCheckResult.Failed }
    val request = requestScope.async {
        // A lookup can't be interrupted, so a late answer mustn't still send the request. Not through a
        // proxy, which does its own.
        if (isDirect(connection.url)) {
            InetAddress.getAllByName(connection.url.host)
            ensureActive()
        }
        fetch(connection, installedVersion)
    }
    return try {
        withTimeoutOrNull(CHECK_TIMEOUT) { request.await() } ?: UpdateCheckResult.Failed
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        UpdateCheckResult.Failed
    } finally {
        // Closes the socket, so a stalled connect or read ends now rather than at its own timeout.
        connection.disconnect()
        request.cancel()
    }
}

private fun isDirect(url: URL): Boolean =
    runCatching { ProxySelector.getDefault()?.select(url.toURI())?.all { it.type() == Proxy.Type.DIRECT } }
        .getOrNull() ?: true

private fun fetch(connection: HttpURLConnection, installedVersion: String): UpdateCheckResult {
    connection.connectTimeout = CONNECT_TIMEOUT.inWholeMilliseconds.toInt()
    connection.readTimeout = READ_TIMEOUT.inWholeMilliseconds.toInt()
    connection.setRequestProperty("Accept", "application/vnd.github+json")
    connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
    // GitHub's API refuses requests without one.
    connection.setRequestProperty("User-Agent", "QRServ/$installedVersion")
    val statusCode = connection.responseCode
    if (statusCode != HttpURLConnection.HTTP_OK) return updateCheckResult(installedVersion, statusCode, null)
    val body = connection.inputStream.use { input ->
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            bytes.write(buffer, 0, read)
            if (bytes.size() > MAX_RESPONSE_BYTES) return UpdateCheckResult.Failed
        }
        String(bytes.toByteArray(), Charsets.UTF_8)
    }
    return updateCheckResult(installedVersion, statusCode, body)
}
