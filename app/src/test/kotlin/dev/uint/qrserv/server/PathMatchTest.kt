package dev.uint.qrserv.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PathMatchTest {

    @Test
    fun rootMatchesAnyFile() {
        assertTrue(pathMatchesFile("/", "report.pdf"))
        assertTrue(pathMatchesFile("", "report.pdf"))
    }

    @Test
    fun exactNameMatches() {
        assertTrue(pathMatchesFile("/report.pdf", "report.pdf"))
    }

    @Test
    fun percentEncodedNameMatches() {
        assertTrue(pathMatchesFile("/my%20file%20%281%29.txt", "my file (1).txt"))
        assertTrue(pathMatchesFile("/%E4%B8%AD%E6%96%87.txt", "中文.txt"))
    }

    @Test
    fun plusIsNotASpaceInAPath() {
        assertFalse(pathMatchesFile("/my+file.txt", "my file.txt"))
        assertTrue(pathMatchesFile("/a+b.txt", "a+b.txt"))
    }

    @Test
    fun otherPathsDoNotMatch() {
        assertFalse(pathMatchesFile("/older-report.pdf", "report.pdf"))
        assertFalse(pathMatchesFile("/report.pdf/", "report.pdf"))
        assertFalse(pathMatchesFile("/dir/report.pdf", "report.pdf"))
        assertFalse(pathMatchesFile("/REPORT.PDF", "report.pdf"))
    }

    @Test
    fun malformedEncodingDoesNotMatch() {
        assertFalse(pathMatchesFile("/bad%zz", "bad%zz"))
    }

    @Test
    fun theAppsOwnLinksMatch() {
        listOf(
            "report.pdf",
            "my file (1).txt",
            "a+b.txt",
            "50%.txt",
            "what?#.txt",
            "semi;colon&amp=1.txt",
            "tilde~'quote'!.txt",
            "中文 ✓.txt",
            "emoji 😀.png",
        ).forEach { name ->
            val path = java.net.URI(shareUrl("192.168.2.25", 8080, name)).rawPath
            assertTrue(name, pathMatchesFile(path, name))
        }
    }

    @Test
    fun linkWithoutNameIsTheRoot() {
        assertEquals("http://192.168.2.25:8080/", shareUrl("192.168.2.25", 8080, null))
        assertEquals("http://[2a0e::1]:8080/", shareUrl("2a0e::1", 8080, null))
    }
}
