package dev.uint.qrserv.files

import java.io.Closeable

/** Lets a running import be abandoned. */
class ImportControl {
    @Volatile
    private var source: Closeable? = null

    @Volatile
    var aborted = false
        private set

    internal fun attach(input: Closeable) {
        source = input
        if (aborted) runCatching { input.close() }
    }

    /** Closing the source ends a blocked read. */
    fun abort() {
        aborted = true
        source?.let { runCatching { it.close() } }
    }
}
