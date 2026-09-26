package dev.uint.qrserv.server

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
}
