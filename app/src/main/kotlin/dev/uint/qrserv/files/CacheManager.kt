package dev.uint.qrserv.files

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

object CacheManager {
    /**
     * A lock rather than the pair of in-progress flags this used to keep: those made an
     * overlapping call return having deleted nothing, so a purge that raced another one left
     * copies of previously shared files sitting in the cache -- silently, and for an app whose
     * whole job is handing files to strangers. Waiting is the right answer; skipping isn't.
     */
    private val mutex = Mutex()

    /** Deletes everything in [dir] except [keep], if given. */
    suspend fun deleteCache(dir: String, keep: String? = null) = withContext(Dispatchers.IO) {
        mutex.withLock {
            File(dir).listFiles()?.forEach { entity ->
                if (entity.path != keep) entity.deleteRecursively()
            }
        }
    }
}
