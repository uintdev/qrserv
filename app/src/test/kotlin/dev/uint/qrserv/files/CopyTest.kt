package dev.uint.qrserv.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class CopyTest {

    @Test
    fun aStreamEndingBeforeItsReportedSizeFails() {
        assertThrows(IOException::class.java) { checkFullyCopied(copied = 2, expected = 8) }
    }

    @Test
    fun aCompleteCopyPasses() {
        checkFullyCopied(copied = 8, expected = 8)
    }

    @Test
    fun aProviderThatUnderreportsOrDoesNotKnowIsFine() {
        checkFullyCopied(copied = 9, expected = 8)
        checkFullyCopied(copied = 9, expected = 0)
    }

    @Test
    fun aFilesOwnLengthOutranksTheReportedSize() {
        assertEquals(1000L, expectedBytes(descriptorLength = 1000, reportedSize = 4022))
        assertEquals(0L, expectedBytes(descriptorLength = 0, reportedSize = 4022))
    }

    @Test
    fun aPipeFallsBackToTheReportedSize() {
        assertEquals(4022L, expectedBytes(descriptorLength = -1, reportedSize = 4022))
    }

    @Test
    fun anIncompleteCopyHasItsOwnResult() {
        assertEquals(ImportResult.Incomplete, importFailureResult(IncompleteCopyException()))
        assertTrue(importFailureResult(IOException("other")) is ImportResult.SelectionFailed)
    }

    private fun bytes(name: String) = name.toByteArray(Charsets.UTF_8).size

    @Test
    fun aShortNameJustGetsTheTag() {
        assertEquals("A1B2C3_photo.jpg", prefixedEntryName("A1B2C3", "photo.jpg"))
    }

    @Test
    fun aNameAtTheLimitStaysWithinItAndKeepsItsExtension() {
        val name = prefixedEntryName("A1B2C3", "a".repeat(251) + ".jpg")
        assertEquals(255, bytes(name))
        assertTrue(name.startsWith("A1B2C3_"))
        assertTrue(name.endsWith(".jpg"))
    }

    @Test
    fun shorteningNeverSplitsACharacter() {
        val cjk = prefixedEntryName("A1B2C3", "中".repeat(83) + ".txt")
        assertTrue(bytes(cjk) <= 255)
        assertFalse(cjk.contains('\uFFFD'))
        val emoji = prefixedEntryName("A1B2C3", "😀".repeat(62) + ".txt")
        assertTrue(bytes(emoji) <= 255)
        assertEquals(emoji, String(emoji.toByteArray(Charsets.UTF_8), Charsets.UTF_8))
        assertTrue(emoji.endsWith("😀.txt"))
    }

    @Test
    fun aLongNameFitsWithoutATag() {
        assertEquals("photo.jpg", fittedEntryName("photo.jpg"))
        val name = fittedEntryName("a".repeat(300) + ".jpg")
        assertEquals(255, bytes(name))
        assertTrue(name.endsWith(".jpg"))
    }

    @Test
    fun aHugeExtensionIsTreatedAsPartOfTheName() {
        val name = prefixedEntryName("A1B2C3", "a." + "b".repeat(253))
        assertTrue(bytes(name) <= 255)
        assertTrue(name.startsWith("A1B2C3_a."))
    }
}
