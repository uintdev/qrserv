package dev.uint.qrserv.files

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ImportControlTest {

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
}
