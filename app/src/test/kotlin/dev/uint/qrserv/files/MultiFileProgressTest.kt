package dev.uint.qrserv.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiFileProgressTest {

    private val mb = 1024L * 1024L

    @Test
    fun theLastFileStillCopyingIsNotFinal() {
        val progress = multiFileProgress(filesDone = 2, totalFiles = 3, bytesCopied = 3 * mb, totalBytes = 42 * mb)
        assertFalse(progress.isFinal)
        assertEquals(2, progress.completedFiles)
        assertEquals(3 * mb, progress.bytesCopied)
        assertEquals(42 * mb, progress.totalBytes)
    }

    @Test
    fun allFilesDoneIsFinal() {
        assertTrue(multiFileProgress(filesDone = 3, totalFiles = 3, bytesCopied = 42 * mb, totalBytes = 42 * mb).isFinal)
    }

    @Test
    fun withoutEverySizeTheBarFollowsTheFileCount() {
        val progress = multiFileProgress(filesDone = 1, totalFiles = 4, bytesCopied = 900 * mb, totalBytes = null)
        assertEquals(1L, progress.bytesCopied)
        assertEquals(4L, progress.totalBytes)
    }
}
