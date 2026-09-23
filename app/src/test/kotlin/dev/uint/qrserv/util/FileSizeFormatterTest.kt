package dev.uint.qrserv.util

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class FileSizeFormatterTest {

    @Before
    fun useUsLocale() {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun megabytesAreIsolatedLeftToRight() {
        assertEquals("\u20664.77 MB\u2069", FileSizeFormatter.humanReadable(5_000_000))
    }

    @Test
    fun bytesHaveNoDecimals() {
        assertEquals("\u2066512 B\u2069", FileSizeFormatter.humanReadable(512))
    }

    @Test
    fun zeroIsIsolatedToo() {
        assertEquals("\u20660 B\u2069", FileSizeFormatter.humanReadable(0))
    }

    @Test
    fun usesTheGivenLocalesDecimalSeparator() {
        assertEquals("\u20664,77 MB\u2069", FileSizeFormatter.humanReadable(5_000_000, Locale.GERMANY))
    }
}
