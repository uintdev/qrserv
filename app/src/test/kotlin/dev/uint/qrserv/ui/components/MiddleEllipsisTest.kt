package dev.uint.qrserv.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiddleEllipsisTest {

    /** Approximates real rendering: one glyph per code point, fixed width. */
    private val widthOf: (String) -> Int = { it.codePointCount(0, it.length) * 10 }

    private fun hasLoneSurrogate(s: String): Boolean {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c.isHighSurrogate()) {
                if (i + 1 >= s.length || !s[i + 1].isLowSurrogate()) return true
                i += 2
                continue
            }
            if (c.isLowSurrogate()) return true
            i++
        }
        return false
    }

    private val samples = listOf(
        "plain_ascii_filename_that_is_quite_long.txt",
        "😀".repeat(12) + ".txt",
        "A" + "😀".repeat(12) + ".txt",
        "AB" + "😀".repeat(12) + ".txt",
        "👨‍👩‍👧‍👦 family album vacation.jpg",
        "🇬🇧🇫🇷🇩🇪 flags of europe.png",
        "👋🏽👋🏿 waving hands tones.gif",
        "é".repeat(10) + " combining accents.txt",
    )

    @Test
    fun neverEmitsALoneSurrogate() {
        for (sample in samples) {
            for (budget in 0..(widthOf(sample) + 20) step 5) {
                val out = middleEllipsis(sample, budget, widthOf)
                assertFalse(
                    "lone surrogate for ${sample.take(12)}… at budget $budget -> $out",
                    hasLoneSurrogate(out),
                )
                val outFile = middleEllipsisFilename(sample, budget, widthOf)
                assertFalse(
                    "lone surrogate (filename) for ${sample.take(12)}… at budget $budget -> $outFile",
                    hasLoneSurrogate(outFile),
                )
            }
        }
    }

    @Test
    fun staysWithinBudgetWhenItCan() {
        for (sample in samples) {
            for (budget in 10..(widthOf(sample) + 20) step 5) {
                val out = middleEllipsis(sample, budget, widthOf)
                assertTrue("overflowed budget $budget: $out", widthOf(out) <= budget || out == "…")
            }
        }
    }

    @Test
    fun fittingTextIsReturnedUnchanged() {
        for (sample in samples) {
            assertEquals(sample, middleEllipsis(sample, widthOf(sample), widthOf))
        }
    }

    @Test
    fun asciiTruncationIsUnchanged() {
        val name = "abcdefghijklmnopqrstuvwxyz"
        assertEquals("abcd…wxyz", middleEllipsis(name, 90, widthOf))
    }

    @Test
    fun keepsExtensionIntactOnEmojiName() {
        val name = "😀".repeat(12) + ".txt"
        val out = middleEllipsisFilename(name, 120, widthOf)
        assertTrue("extension lost: $out", out.endsWith(".txt"))
        assertFalse(hasLoneSurrogate(out))
    }
}
