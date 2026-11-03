package dev.uint.qrserv.files

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes

class ImportControlTest {

    @Test
    fun noDataForTheLimitIsAStall() {
        assertFalse(importStalled(4.minutes, anyData = true))
        assertTrue(importStalled(5.minutes, anyData = true))
    }

    @Test
    fun aProviderStillFetchingGetsMoreRoomBeforeItsFirstData() {
        assertFalse(importStalled(29.minutes, anyData = false))
        assertTrue(importStalled(30.minutes, anyData = false))
    }

    @Test
    fun aFreshImportIsNotStalled() {
        assertFalse(ImportControl().stalled())
    }

    @Test
    fun abortClosesTheSource() {
        val closed = AtomicInteger()
        val control = ImportControl()
        control.attach { closed.incrementAndGet() }
        control.abort()
        assertEquals(1, closed.get())
    }

    @Test
    fun aSourceAttachedAfterAbortIsClosedToo() {
        val closed = AtomicInteger()
        val control = ImportControl()
        control.abort()
        control.attach { closed.incrementAndGet() }
        assertEquals(1, closed.get())
    }

    @Test
    fun dataArrivingEndsTheFirstDataAllowanceUntilTheNextFile() {
        val control = ImportControl()
        runBlocking { copyWithProgress(ByteArrayInputStream(ByteArray(1024)), OutputStream.nullOutputStream(), control::dataArrived) {} }
        assertFalse(control.stalled())
        control.startingFile()
        assertFalse(control.stalled())
    }
}
