package dev.uint.qrserv.files

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CacheManager {
    private var deletingDir = false
    private var deletingSpecific = false

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
        if (files.isEmpty() || exclude) {
            if (deletingDir) return@withContext
            deletingDir = true
            try {
                val dir = File(pickerDir)
                if (dir.exists()) {
                    dir.listFiles()?.forEach { entity ->
                        if (files.contains(entity.path)) return@forEach
                        if (directAccessRoot != null && entity.path.startsWith(directAccessRoot)) return@forEach
                        entity.deleteRecursively()
                    }
                }
            } finally {
                deletingDir = false
            }
        } else {
            if (deletingSpecific) return@withContext
            deletingSpecific = true
            try {
                for (path in files) {
                    if (directAccessRoot != null && path.startsWith(directAccessRoot)) continue
                    runCatching { File(path).delete() }
                }
            } finally {
                deletingSpecific = false
            }
        }
    }
}
