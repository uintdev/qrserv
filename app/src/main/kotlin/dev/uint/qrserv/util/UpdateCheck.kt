package dev.uint.qrserv.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.NoRouteToHostException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

const val RELEASES_URL = "https://github.com/uintdev/qrserv/releases"

// GitHub's "latest" release is never a pre-release or a draft. By the repository's ID rather than its
// name: that survives an account or repository rename, and a freed-up old name can't take the check over.
private const val LATEST_RELEASE_API = "https://api.github.com/repositories/343788099/releases/latest"

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

    data class Failed(val reason: UpdateCheckFailure) : UpdateCheckResult
}

sealed interface UpdateCheckFailure {
    /** GitHub answered with a client or server error, such as 403 when rate-limited. */
    data class HttpError(val status: Int) : UpdateCheckFailure

    /** Not a release as expected: not JSON, too large, or without a plain version. */
    data object InvalidResponse : UpdateCheckFailure

    data object TimedOut : UpdateCheckFailure

    data object CertificateInvalid : UpdateCheckFailure

    data object NoConnection : UpdateCheckFailure

    /** Nothing more specific to say, such as an installed version that can't be compared. */
    data object Other : UpdateCheckFailure
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

/** What GitHub's latest-release response means for [installedVersion]. */
internal fun updateCheckResult(installedVersion: String, body: String): UpdateCheckResult {
    val invalid = UpdateCheckResult.Failed(UpdateCheckFailure.InvalidResponse)
    val release = runCatching { JSONObject(body) }.getOrNull() ?: return invalid
    if (release.optBoolean("prerelease") || release.optBoolean("draft")) return invalid
    val tag = release.optString("tag_name")
    val latest = parseVersion(tag) ?: return invalid
    val installed = parseVersion(installedVersion) ?: return UpdateCheckResult.Failed(UpdateCheckFailure.Other)
    val latestVersion = tag.removePrefix("v")
    val order = compareVersions(latest, installed)
    return when {
        // Built from the tag rather than taken from the response's html_url, so it can only ever lead to this
        // repository. The tag is only "v" and numbers, as parsed above. After a rename, GitHub redirects it.
        order > 0 -> UpdateCheckResult.UpdateAvailable(latestVersion, "$RELEASES_URL/tag/$tag")
        order < 0 -> UpdateCheckResult.NewerThanLatest(latestVersion)
        else -> UpdateCheckResult.UpToDate
    }
}

// Not a child of the caller: a stalled DNS lookup can't be interrupted, and the deadline mustn't wait for it.
private val requestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Why a response other than 200 failed: its status for a client or server error, otherwise not a release. */
internal fun failureForStatus(status: Int): UpdateCheckFailure =
    if (status in 400..599) UpdateCheckFailure.HttpError(status) else UpdateCheckFailure.InvalidResponse

/** Why the request threw, from anywhere in the cause chain: Android wraps a certificate error in a handshake one. */
internal fun failureForException(e: Throwable): UpdateCheckFailure {
    val chain = generateSequence(e) { it.cause }.take(16).toList()
    return when {
        chain.any { it is SocketTimeoutException } -> UpdateCheckFailure.TimedOut
        chain.any { it is CertificateException || it is SSLPeerUnverifiedException } ->
            UpdateCheckFailure.CertificateInvalid
        chain.any { it is UnknownHostException || it is ConnectException || it is NoRouteToHostException } ->
            UpdateCheckFailure.NoConnection
        else -> UpdateCheckFailure.Other
    }
}

/** Asks GitHub for its latest release. Never throws: anything that goes wrong is [UpdateCheckResult.Failed]. */
suspend fun checkForUpdate(installedVersion: String): UpdateCheckResult {
    val connection = runCatching { URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection }
        .getOrElse { return UpdateCheckResult.Failed(UpdateCheckFailure.Other) }
    val request = requestScope.async {
        // A lookup can't be interrupted, so a late answer mustn't still send the request. Not through a
        // proxy, which does its own.
        if (isDirect(connection.url)) {
            InetAddress.getAllByName(connection.url.host)
            ensureActive()
        }
        val status = send(connection)
        if (status != HttpURLConnection.HTTP_OK) return@async UpdateCheckResult.Failed(failureForStatus(status))
        readBody(connection)?.let { updateCheckResult(installedVersion, it) }
            ?: UpdateCheckResult.Failed(UpdateCheckFailure.InvalidResponse)
    }
    return try {
        withTimeoutOrNull(CHECK_TIMEOUT) { request.await() } ?: UpdateCheckResult.Failed(UpdateCheckFailure.TimedOut)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        UpdateCheckResult.Failed(failureForException(e))
    } finally {
        // Closes the socket, so a stalled connect or read ends now rather than at its own timeout.
        connection.disconnect()
        request.cancel()
    }
}

private fun isDirect(url: URL): Boolean =
    runCatching { ProxySelector.getDefault()?.select(url.toURI())?.all { it.type() == Proxy.Type.DIRECT } }
        .getOrNull() ?: true

/** Sends the request and returns the response's status, -1 if it isn't HTTP. Throws on a network failure. */
private fun send(connection: HttpURLConnection): Int {
    connection.connectTimeout = CONNECT_TIMEOUT.inWholeMilliseconds.toInt()
    connection.readTimeout = READ_TIMEOUT.inWholeMilliseconds.toInt()
    connection.setRequestProperty("Accept", "application/vnd.github+json")
    connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
    // GitHub's API refuses requests without one, and Android's default names the device model.
    connection.setRequestProperty("User-Agent", "QRServ")
    return connection.responseCode
}

/** The response's body, or null for one over [MAX_RESPONSE_BYTES]. Throws on a network failure. */
private fun readBody(connection: HttpURLConnection): String? =
    connection.inputStream.use { input ->
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            bytes.write(buffer, 0, read)
            if (bytes.size() > MAX_RESPONSE_BYTES) return null
        }
        bytes.toString(Charsets.UTF_8.name())
    }
