package dev.uint.qrserv.util

import org.json.JSONObject
import org.junit.Assert.assertEquals
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
    fun installedVersionThatCantBeComparedIsAnotherFailure() {
        assertEquals(UpdateCheckResult.Failed(UpdateCheckFailure.Other), updateCheckResult("4.3.0-dev", release("v4.4.0")))
    }
}
