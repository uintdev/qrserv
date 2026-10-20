package dev.uint.qrserv.util

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun renamedRepositoryStillLinksToTheRelease() {
        val renamed = "https://github.com/someone-else/qrserv-renamed/releases/tag/v4.4.0"
        assertEquals(
            UpdateCheckResult.UpdateAvailable("4.4.0", renamed),
            updateCheckResult("4.3.0", release("v4.4.0", url = renamed)),
        )
    }

    @Test
    fun releaseLinkThatIsntAGitHubReleasePageFallsBackToTheReleasesPage() {
        for (url in listOf(
            "https://example.com/qrserv.apk",
            "https://github.com.example.com/uintdev/qrserv/releases/tag/v4.4.0",
            "http://github.com/uintdev/qrserv/releases/tag/v4.4.0",
            "https://github.com/uintdev/qrserv/releases/download/v4.4.0/qrserv.apk",
            "https://github.com/uintdev/qrserv/releases/tag/v4.4.0/../../../../evil",
            "https://github.com/uintdev/qrserv/releases/tag/v4.4.0\\..\\..\\..\\..\\settings\\tokens",
            "https://github.com/%2e%2e/%2e%2e/releases/tag/v4.4.0",
            "https://github.com/../../releases/tag/v4.4.0",
            "https://github.com/uintdev/qrserv/releases/tag/v9.9.9",
        )) {
            assertEquals(url, UpdateCheckResult.UpdateAvailable("4.4.0", RELEASES_URL), updateCheckResult("4.3.0", release("v4.4.0", url = url)))
        }
    }

    @Test
    fun anythingElseIsAFailure() {
        assertEquals(UpdateCheckResult.Failed, updateCheckResult("4.3.0", null))
        assertEquals(UpdateCheckResult.Failed, updateCheckResult("4.3.0", "<html>rate limited</html>"))
        assertEquals(UpdateCheckResult.Failed, updateCheckResult("4.3.0", release("v4.4.0", prerelease = true)))
        assertEquals(UpdateCheckResult.Failed, updateCheckResult("4.3.0", release("nightly")))
        assertEquals(UpdateCheckResult.Failed, updateCheckResult("4.3.0-dev", release("v4.4.0")))
    }
}
