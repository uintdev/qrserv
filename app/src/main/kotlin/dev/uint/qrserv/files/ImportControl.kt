package dev.uint.qrserv.files

import java.io.Closeable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

private val FIRST_DATA_LIMIT = 30.minutes

private val NO_PROGRESS_LIMIT = 5.minutes

/** A provider may fetch a whole file before sending any of it. */
internal fun importStalled(sinceData: Duration, anyData: Boolean): Boolean =
    sinceData >= if (anyData) NO_PROGRESS_LIMIT else FIRST_DATA_LIMIT

/** Lets a running import be abandoned, and tells whether its provider has stopped sending. */
class ImportControl {
    @Volatile
    private var lastData = TimeSource.Monotonic.markNow()

    @Volatile
    private var anyData = false

    @Volatile
    private var source: Closeable? = null

    @Volatile
    var aborted = false
        private set

    internal fun attach(input: Closeable) {
        source = input
        if (aborted) runCatching { input.close() }
    }

    /** Each file gets its own first-data allowance. */
    internal fun startingFile() {
        lastData = TimeSource.Monotonic.markNow()
        anyData = false
    }

    internal fun dataArrived() {
        lastData = TimeSource.Monotonic.markNow()
        anyData = true
    }

    fun stalled(): Boolean = importStalled(lastData.elapsedNow(), anyData)

    /** Closing the source ends a blocked read. */
    fun abort() {
        aborted = true
        source?.let { runCatching { it.close() } }
    }
}
