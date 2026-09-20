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
     *
     * One lock covers both branches below because both operate on the same cache directory, and
     * the specific-file branch can name a file the directory sweep is walking.
     */
    private val mutex = Mutex()

    /**
     * When [exclude] is true, everything in [pickerDir] EXCEPT paths in [files] is deleted
     * (used when importing a new selection). When false and [files] is non-empty, exactly
     * those paths are deleted.
     */
    suspend fun deleteCache(
        pickerDir: String,
        files: List<String> = emptyList(),
        exclude: Boolean = false,
        directAccessRoot: String? = null,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (files.isEmpty() || exclude) {
                val dir = File(pickerDir)
                if (dir.exists()) {
                    dir.listFiles()?.forEach { entity ->
                        if (files.contains(entity.path)) return@forEach
                        if (directAccessRoot != null && entity.path.startsWith(directAccessRoot)) return@forEach
                        entity.deleteRecursively()
                    }
                }
            } else {
                for (path in files) {
                    if (directAccessRoot != null && path.startsWith(directAccessRoot)) continue
                    runCatching { File(path).delete() }
                }
            }
        }
    }
}
