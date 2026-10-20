package dev.uint.qrserv.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class ThrowablesTest {

    @Test
    fun walksEachCauseInTurn() {
        val root = IllegalStateException("root")
        val middle = IOException("middle", root)
        val top = RuntimeException("top", middle)
        assertEquals(listOf(top, middle, root), top.causeChain().toList())
    }

    @Test
    fun endsWhenACauseComesRoundAgain() {
        val first = IOException("first")
        val second = IOException("second", first)
        first.initCause(second)
        assertEquals(listOf(first, second), first.causeChain().toList())
    }

    @Test
    fun canBeWalkedMoreThanOnce() {
        val chain = RuntimeException(IOException()).causeChain()
        assertEquals(2, chain.count())
        assertEquals(2, chain.count())
    }
}
