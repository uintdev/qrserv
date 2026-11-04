package dev.uint.qrserv.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SleepCountingDelayTest {

    @Test
    fun evenFiringAsLateAsAllowedLandsOnTheDeadline() {
        for (remaining in listOf(5.minutes, 10.minutes, 30.minutes, 60.minutes)) {
            val delay = inexactAlarmDelay(remaining)
            assertTrue(remaining.toString(), delay * 1.75 <= remaining + 1.seconds)
            assertTrue(remaining.toString(), delay < remaining)
        }
    }

    @Test
    fun aShortRemainderIsNotShortenedFurther() {
        assertEquals(5.seconds, inexactAlarmDelay(5.seconds))
    }
}
