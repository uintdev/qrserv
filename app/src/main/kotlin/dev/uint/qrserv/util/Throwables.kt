package dev.uint.qrserv.util

import java.util.Collections
import java.util.IdentityHashMap

/** This throwable, then its cause, then that one's, and so on; ending early if a cause comes round again. */
internal fun Throwable.causeChain(): Sequence<Throwable> = sequence {
    val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    var current: Throwable? = this@causeChain
    while (current != null && seen.add(current)) {
        yield(current)
        current = current.cause
    }
}
