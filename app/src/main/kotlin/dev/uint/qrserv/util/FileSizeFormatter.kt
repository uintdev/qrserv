package dev.uint.qrserv.util

import java.util.Locale
import kotlin.math.roundToLong

object FileSizeFormatter {
    private val UNITS = listOf("B", "KB", "MB", "GB", "TB")

    /** Formats a byte count as a human-readable string, e.g. "12.34 MB". */
    fun humanReadable(length: Long, locale: Locale = Locale.getDefault()): String {
        if (length <= 0) return isolateLtr("0 B")

        var unitIndex = 0
        var size = length.toDouble()
        while (size >= 1024.0 && unitIndex < UNITS.lastIndex) {
            size /= 1024.0
            unitIndex++
        }

        var rounded = roundTo2Decimals(size)
        if (rounded >= 1024.0 && unitIndex < UNITS.lastIndex) {
            unitIndex++
            rounded = roundTo2Decimals(rounded / 1024.0)
        }

        val formatted = if (unitIndex == 0) {
            rounded.toLong().toString()
        } else {
            String.format(locale, "%.2f", rounded)
        }

        return isolateLtr("$formatted ${UNITS[unitIndex]}")
    }

    private fun isolateLtr(text: String): String = "\u2066$text\u2069"

    private fun roundTo2Decimals(value: Double): Double = (value * 100.0).roundToLong() / 100.0
}
