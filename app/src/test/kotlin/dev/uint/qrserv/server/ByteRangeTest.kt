package dev.uint.qrserv.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class ByteRangeTest {

    @Test
    fun noHeaderMeansTheWholeFile() {
        assertEquals(ByteRange.Full, byteRange(null, 100))
    }

    @Test
    fun closedRange() {
        assertEquals(ByteRange.Partial(10, 19), byteRange("bytes=10-19", 100))
    }

    @Test
    fun openEndedRangeRunsToTheEnd() {
        assertEquals(ByteRange.Partial(40, 99), byteRange("bytes=40-", 100))
    }

    @Test
    fun endPastTheFileIsClamped() {
        assertEquals(ByteRange.Partial(90, 99), byteRange("bytes=90-5000", 100))
    }

    @Test
    fun suffixRange() {
        assertEquals(ByteRange.Partial(70, 99), byteRange("bytes=-30", 100))
        assertEquals(ByteRange.Partial(0, 99), byteRange("bytes=-500", 100))
    }

    @Test
    fun unitAndSpacingAreLenient() {
        assertEquals(ByteRange.Partial(5, 9), byteRange(" BYTES= 5 - 9 ", 100))
    }

    @Test
    fun rangesThatCantBeMetAreUnsatisfiable() {
        assertEquals(ByteRange.Unsatisfiable, byteRange("bytes=100-", 100))
        assertEquals(ByteRange.Unsatisfiable, byteRange("bytes=-0", 100))
        assertEquals(ByteRange.Unsatisfiable, byteRange("bytes=0-", 0))
    }

    @Test
    fun anythingElseFallsBackToTheWholeFile() {
        for (header in listOf("items=0-5", "bytes=0-5,10-15", "bytes=5", "bytes=9-5", "bytes=a-b", "bytes=+5-", "bytes=-", "bytes=99999999999999999999-")) {
            assertEquals(header, ByteRange.Full, byteRange(header, 100))
        }
    }

    @Test
    fun aRangeNeedsTheExactTag() {
        assertFalse(rangeValidated(null, null, "\"abc\""))
        assertTrue(rangeValidated("\"abc\"", null, "\"abc\""))
        assertTrue(rangeValidated(null, "\"xyz\", \"abc\"", "\"abc\""))
        assertFalse(rangeValidated(null, "*", "\"abc\""))
        assertFalse(rangeValidated("W/\"abc\"", null, "\"abc\""))
        assertFalse(rangeValidated("Sat, 26 Sep 2026 05:00:00 GMT", null, "\"abc\""))
    }

    @Test
    fun ifMatchAcceptsTheTagInAListOrAWildcard() {
        assertTrue(ifMatchMatches(null, "\"abc\""))
        assertTrue(ifMatchMatches("*", "\"abc\""))
        assertTrue(ifMatchMatches("\"xyz\", \"abc\"", "\"abc\""))
        assertFalse(ifMatchMatches("\"xyz\"", "\"abc\""))
        assertFalse(ifMatchMatches("W/\"abc\"", "\"abc\""))
    }

    @Test
    fun limitedStreamStopsAtItsLimit() {
        val input = LimitedInputStream(ByteArrayInputStream(ByteArray(10) { it.toByte() }), 4)
        assertEquals(listOf<Byte>(0, 1, 2, 3), input.readBytes().toList())
    }
}
