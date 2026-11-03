package dev.uint.qrserv.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopyTest {

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
