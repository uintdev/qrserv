package dev.uint.qrserv.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PickedNameTest {

    @Test
    fun plainNameIsKept() {
        assertEquals("photo.jpg", sanitizePickedName("photo.jpg"))
    }

    @Test
    fun onlyTheLastPathSegmentIsKept() {
        assertEquals("passwd", sanitizePickedName("../../etc/passwd"))
        assertEquals("win.ini", sanitizePickedName("C:\\Windows\\win.ini"))
        assertEquals("b.txt", sanitizePickedName("a/..\\b.txt"))
    }

    @Test
    fun controlCharactersAreReplaced() {
        assertEquals("a_b_c", sanitizePickedName("a\u0000b\nc"))
        assertEquals("a_b", sanitizePickedName("a\u007Fb"))
    }

    @Test
    fun surroundingWhitespaceIsTrimmed() {
        assertEquals("name.txt", sanitizePickedName("  name.txt  "))
    }

    @Test
    fun namesThatAreOnlyAPathFallBack() {
        for (name in listOf("", "   ", ".", "..", "dir/", "dir/..", "dir\\.")) {
            assertEquals(name, "file", sanitizePickedName(name))
        }
    }

    @Test
    fun containment() {
        val dir = Files.createTempDirectory("qrserv").toFile()
        try {
            assertTrue(isContainedIn(File(dir, "a.txt"), dir))
            assertFalse(isContainedIn(File(dir, "../a.txt"), dir))
            assertFalse(isContainedIn(dir, dir))
            assertFalse(isContainedIn(File(dir.path + "-sibling", "a.txt"), dir))
        } finally {
            dir.deleteRecursively()
        }
    }
}
