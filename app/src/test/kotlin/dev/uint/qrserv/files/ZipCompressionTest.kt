package dev.uint.qrserv.files

import dev.uint.qrserv.data.ZipCompression
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class ZipCompressionTest {

    private val text = "the same line over and over\n".repeat(50_000).toByteArray()

    private fun archive(compression: ZipCompression): ByteArray {
        val bytes = ByteArrayOutputStream()
        zipOutputStream(bytes, compression).use { zip ->
            for (name in listOf("a.txt", "b.txt")) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    @Test
    fun everyLevelReadsBackIntact() {
        for (compression in ZipCompression.entries) {
            ZipInputStream(ByteArrayInputStream(archive(compression))).use { zip ->
                for (name in listOf("a.txt", "b.txt")) {
                    assertEquals(compression.name, name, zip.nextEntry?.name)
                    assertArrayEquals(compression.name, text, zip.readBytes())
                }
            }
        }
    }

    @Test
    fun noneKeepsTheFilesAtTheirSize() {
        assertTrue(archive(ZipCompression.NONE).size >= text.size * 2)
    }

    @Test
    fun theOtherLevelsCompress() {
        for (compression in ZipCompression.entries - ZipCompression.NONE) {
            assertTrue(compression.name, archive(compression).size < text.size / 10)
        }
    }

    @Test
    fun normalIsNoLargerThanFast() {
        assertTrue(archive(ZipCompression.NORMAL).size <= archive(ZipCompression.FAST).size)
    }
}
