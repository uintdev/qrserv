package dev.uint.qrserv.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateExpiredException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class UpdateCheckTest {

    private fun release(tag: String, url: String = "$RELEASES_URL/tag/$tag", prerelease: Boolean = false) =
        JSONObject()
            .put("tag_name", tag)
            .put("html_url", url)
            .put("prerelease", prerelease)
            .put("draft", false)
            .put("name", "")
            .toString()

    @Test
    fun parsesTagsWithOrWithoutTheV() {
        assertEquals(listOf(4, 2, 1), parseVersion("v4.2.1"))
        assertEquals(listOf(4, 2, 1), parseVersion("4.2.1"))
        assertEquals(listOf(4, 10), parseVersion("v4.10"))
    }

    @Test
    fun rejectsTagsThatArentPlainNumbers() {
        for (tag in listOf("", "v", "4.3.0-beta", "4..1", "4.2.", "latest", "v4.2.1 ", "4.x.0", "99999999999")) {
            assertNull(tag, parseVersion(tag))
        }
    }

    @Test
    fun comparesNumericallyPartByPart() {
        assertTrue(compareVersions(listOf(4, 10, 0), listOf(4, 9, 0)) > 0)
        assertTrue(compareVersions(listOf(4, 2, 1), listOf(4, 3)) < 0)
        assertEquals(0, compareVersions(listOf(4, 2), listOf(4, 2, 0)))
    }

    @Test
    fun newerReleaseLinksToThatRelease() {
        assertEquals(
            UpdateCheckResult.UpdateAvailable("4.4.0", "$RELEASES_URL/tag/v4.4.0"),
            updateCheckResult("4.3.0", release("v4.4.0")),
        )
    }

    @Test
    fun sameVersionIsUpToDate() {
        assertEquals(UpdateCheckResult.UpToDate, updateCheckResult("4.3.0", release("v4.3.0")))
    }

    @Test
    fun installedNewerThanLatestSaysSo() {
        assertEquals(UpdateCheckResult.NewerThanLatest("4.2.1"), updateCheckResult("4.3.0", release("v4.2.1")))
    }

    @Test
    fun releaseLinkIsAlwaysThisRepositorysPageForTheTag() {
        for (url in listOf(
            "https://github.com/someone-else/qrserv/releases/tag/v4.4.0",
            "https://example.com/qrserv.apk",
            "https://github.com/uintdev/qrserv/releases/download/v4.4.0/qrserv.apk",
            "https://github.com/uintdev/qrserv/releases/tag/v9.9.9",
        )) {
            assertEquals(
                url,
                UpdateCheckResult.UpdateAvailable("4.4.0", "$RELEASES_URL/tag/v4.4.0"),
                updateCheckResult("4.3.0", release("v4.4.0", url = url)),
            )
        }
    }

    @Test
    fun clientAndServerErrorsGiveTheirStatus() {
        for (status in listOf(400, 403, 404, 429, 500, 503, 599)) {
            assertEquals(UpdateCheckFailure.HttpError(status), failureForStatus(status))
        }
    }

    @Test
    fun otherUnsuccessfulResponsesAreInvalid() {
        for (status in listOf(-1, 100, 204, 301, 304, 399, 600)) {
            assertEquals(status.toString(), UpdateCheckFailure.InvalidResponse, failureForStatus(status))
        }
    }

    @Test
    fun exceptionsAreClassifiedThroughTheirCauses() {
        assertEquals(UpdateCheckFailure.TimedOut, failureForException(SocketTimeoutException()))
        assertEquals(
            UpdateCheckFailure.CertificateInvalid,
            failureForException(SSLHandshakeException("handshake").apply { initCause(CertificateExpiredException()) }),
        )
        assertEquals(UpdateCheckFailure.CertificateInvalid, failureForException(SSLPeerUnverifiedException("Hostname not verified")))
        assertEquals(UpdateCheckFailure.NoConnection, failureForException(UnknownHostException()))
        assertEquals(UpdateCheckFailure.NoConnection, failureForException(IOException(ConnectException())))
        assertEquals(UpdateCheckFailure.Other, failureForException(SSLHandshakeException("protocol")))
        assertEquals(UpdateCheckFailure.Other, failureForException(IllegalStateException()))
    }

    @Test
    fun responseThatIsntARelease() {
        val invalid = UpdateCheckResult.Failed(UpdateCheckFailure.InvalidResponse)
        assertEquals(invalid, updateCheckResult("4.3.0", "<html>rate limited</html>"))
        assertEquals(invalid, updateCheckResult("4.3.0", release("v4.4.0", prerelease = true)))
        assertEquals(invalid, updateCheckResult("4.3.0", release("nightly")))
    }

    @Test
    fun jsonNullTagIsInvalid() {
        // Android's optString gives "null" here, where the reference org.json gives "": the tests run on Android's.
        assertEquals("null", JSONObject("""{"tag_name":null}""").optString("tag_name"))
        assertEquals(
            UpdateCheckResult.Failed(UpdateCheckFailure.InvalidResponse),
            updateCheckResult("4.3.0", """{"tag_name":null,"prerelease":false,"draft":false}"""),
        )
    }

    @Test
    fun okResponseIsReadAsARelease() {
        assertEquals(UpdateCheckResult.UpToDate, responseResult("4.3.0", 200) { release("v4.3.0") })
    }

    @Test
    fun errorResponseIsntReadAndGivesItsStatus() {
        for (status in listOf(403, 404, 500)) {
            val result = responseResult("4.3.0", status) { throw AssertionError("body read for $status") }
            assertEquals(UpdateCheckResult.Failed(UpdateCheckFailure.HttpError(status)), result)
        }
    }

    @Test
    fun responseThatIsntAnErrorOrA200IsInvalid() {
        for (status in listOf(-1, 204, 301)) {
            val result = responseResult("4.3.0", status) { throw AssertionError("body read for $status") }
            assertEquals(UpdateCheckResult.Failed(UpdateCheckFailure.InvalidResponse), result)
        }
    }

    @Test
    fun oversizedOrUnparseableBodyIsInvalid() {
        val invalid = UpdateCheckResult.Failed(UpdateCheckFailure.InvalidResponse)
        assertEquals(invalid, responseResult("4.3.0", 200) { null })
        assertEquals(invalid, responseResult("4.3.0", 200) { "<html>captive portal</html>" })
    }

    @Test
    fun requestsResultIsPassedOn() = runBlocking {
        val request = CompletableDeferred<UpdateCheckResult>(UpdateCheckResult.UpToDate)
        assertEquals(UpdateCheckResult.UpToDate, awaitResult(request, 10.seconds))
    }

    @Test
    fun requestThatOutlastsTheDeadlineTimesOut() = runBlocking {
        val request = CompletableDeferred<UpdateCheckResult>()
        assertEquals(UpdateCheckResult.Failed(UpdateCheckFailure.TimedOut), awaitResult(request, 50.milliseconds))
    }

    @Test
    fun requestThatThrowsGivesWhy() = runBlocking {
        val certificate = CompletableDeferred<UpdateCheckResult>()
        certificate.completeExceptionally(SSLHandshakeException("handshake").apply { initCause(CertificateExpiredException()) })
        assertEquals(UpdateCheckResult.Failed(UpdateCheckFailure.CertificateInvalid), awaitResult(certificate, 10.seconds))

        val readTimeout = CompletableDeferred<UpdateCheckResult>()
        readTimeout.completeExceptionally(SocketTimeoutException())
        assertEquals(UpdateCheckResult.Failed(UpdateCheckFailure.TimedOut), awaitResult(readTimeout, 10.seconds))
    }

    @Test
    fun callerBeingCanceledIsntTurnedIntoAFailure() = runBlocking {
        // Were it returned as a failure, the caller would go on to show it, e.g. after leaving Settings.
        var returned = false
        val waiting = launch(start = CoroutineStart.UNDISPATCHED) {
            awaitResult(CompletableDeferred(), 10.seconds)
            returned = true
        }
        waiting.cancel()
        waiting.join()
        assertFalse(returned)
    }

    @Test
    fun installedVersionThatCantBeComparedIsAnotherFailure() {
        assertEquals(UpdateCheckResult.Failed(UpdateCheckFailure.Other), updateCheckResult("4.3.0-dev", release("v4.4.0")))
    }
}
