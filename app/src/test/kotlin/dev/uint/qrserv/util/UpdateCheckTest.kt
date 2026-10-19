package dev.uint.qrserv.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {

    private fun release(tag: String, url: String = "$RELEASES_URL/tag/$tag", prerelease: Boolean = false) =
        """{"tag_name":"$tag","html_url":"$url","prerelease":$prerelease,"draft":false,"name":""}"""

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
            updateCheckResult("4.3.0", 200, release("v4.4.0")),
        )
    }

    @Test
    fun sameVersionIsUpToDate() {
        assertEquals(UpdateCheckResult.UpToDate, updateCheckResult("4.3.0", 200, release("v4.3.0")))
    }

    @Test
    fun installedNewerThanLatestSaysSo() {
        assertEquals(UpdateCheckResult.NewerThanLatest("4.2.1"), updateCheckResult("4.3.0", 200, release("v4.2.1")))
    }

    @Test
    fun releaseLinkOutsideThisRepositoryFallsBackToTheReleasesPage() {
        assertEquals(
            UpdateCheckResult.UpdateAvailable("4.4.0", RELEASES_URL),
            updateCheckResult("4.3.0", 200, release("v4.4.0", url = "https://example.com/qrserv.apk")),
        )
    }

    @Test
    fun anythingElseIsAFailure() {
        assertEquals(UpdateCheckResult.Failed, updateCheckResult("4.3.0", 403, null))
        assertEquals(UpdateCheckResult.Failed, updateCheckResult("4.3.0", 404, null))
        assertEquals(UpdateCheckResult.Failed, updateCheckResult("4.3.0", 200, "<html>rate limited</html>"))
        assertEquals(UpdateCheckResult.Failed, updateCheckResult("4.3.0", 200, release("v4.4.0", prerelease = true)))
        assertEquals(UpdateCheckResult.Failed, updateCheckResult("4.3.0", 200, release("nightly")))
        assertEquals(UpdateCheckResult.Failed, updateCheckResult("4.3.0-dev", 200, release("v4.4.0")))
    }
}
