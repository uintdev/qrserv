package dev.uint.qrserv.files

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import dev.uint.qrserv.data.ArchivedEntry
import dev.uint.qrserv.data.FileInfo
import dev.uint.qrserv.util.TokenGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

sealed class ImportResult {
    data class Success(val fileInfo: FileInfo) : ImportResult()
    object EmptySelection : ImportResult()
    object FileGone : ImportResult()
    object DirectAccessPathMissing : ImportResult()
    /** A picked file couldn't be read/archived, or a shared text file couldn't be written --
     * for any reason other than [FileGone] (e.g. deleted between selection and confirmation). */
    data class SelectionFailed(val message: String) : ImportResult()
}

/** One picked item before it's copied/zipped. */
data class PickedFile(val name: String, val directPath: String? = null)

private fun importFailureResult(error: Throwable): ImportResult =
    if (error is FileNotFoundException) ImportResult.FileGone else ImportResult.SelectionFailed(error.toString())

class FileRepository(private val context: Context) {

    /** Whether Direct Access Mode (browsing the full shared storage tree) is active. */
    var directAccessMode: Boolean = false

    /** Last archive created for a multi-file selection, kept so it survives the next cache sweep. */
    var archivedLast: String = ""
        private set

    companion object {
        val DIRECT_ACCESS_ROOT: String = Environment.getExternalStorageDirectory()?.path ?: "/storage/emulated/0"
    }

    fun pickerDir(ignoreDam: Boolean = false): String {
        return if (!ignoreDam && directAccessMode) {
            DIRECT_ACCESS_ROOT
        } else {
            File(context.cacheDir, "file_picker").apply { mkdirs() }.path
        }
    }

    fun directModeDetect(path: String): Boolean = path.startsWith(DIRECT_ACCESS_ROOT)

    /** Resolves a content:// URI's display name and size via the ContentResolver. */
    private fun resolveUriMeta(resolver: ContentResolver, uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment ?: "file"
        var size = 0L
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
            }
        }
        return name to size
    }

    /**
     * Copies [input] to [output] in chunks, calling [onBytesCopied] with the running total no
     * more than once per 100ms (large files can churn through this loop thousands of times a
     * second, and there's no point re-rendering progress UI faster than that).
     */
    private fun copyWithProgress(
        input: InputStream,
        output: OutputStream,
        onBytesCopied: (bytesCopied: Long) -> Unit,
    ): Long {
        val buffer = ByteArray(64 * 1024)
        var copied = 0L
        var lastReportAt = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
            copied += read
            val now = System.currentTimeMillis()
            if (now - lastReportAt >= 100) {
                onBytesCopied(copied)
                lastReportAt = now
            }
        }
        return copied
    }

    /** Imports files picked via the system document picker or received via a share intent. */
    suspend fun importUris(
        uris: List<Uri>,
        onProgress: (completedFiles: Int, totalFiles: Int, bytesCopied: Long, totalBytes: Long) -> Unit =
            { _, _, _, _ -> },
    ): ImportResult = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext ImportResult.EmptySelection

        val dir = File(pickerDir(ignoreDam = true))
        if (!dir.exists()) dir.mkdirs()

        val resolver = context.contentResolver

        if (uris.size == 1) {
            val uri = uris.first()
            val (rawName, size) = resolveUriMeta(resolver, uri)
            val destFile = File(dir, rawName)
            val totalBytes = size.coerceAtLeast(1L)
            var finalBytes = 0L
            val copyResult = runCatching {
                resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        finalBytes = copyWithProgress(input, output) { copied ->
                            // A real size wasn't available from the content provider -- fall
                            // back to the plain spinner rather than a meaningless bar/percentage.
                            if (size > 0) onProgress(0, 1, copied, totalBytes)
                        }
                    }
                } ?: throw FileNotFoundException(rawName)
            }
            copyResult.onFailure { error ->
                destFile.delete()
                return@withContext importFailureResult(error)
            }
            if (size > 0) onProgress(1, 1, finalBytes.coerceAtLeast(totalBytes), totalBytes)
            val picked = listOf(PickedFile(rawName, directPath = destFile.path))
            pruneCacheKeeping(dir.path, picked.mapNotNull { it.directPath })
            return@withContext finalizeSelection(picked, dir.path)
        }

        // Multiple files: streams each URI's content straight into the archive rather than
        // copying every file locally first and zipping in a second pass -- this way onProgress
        // reflects the entire operation, not just the fast tail end.
        val usedNames = mutableSetOf<String>()
        val archiveName = TokenGenerator.generate("1234567890ABCDEF", 8) + ".zip"
        val archiveFile = File(dir, archiveName)
        val archivedEntries = mutableListOf<ArchivedEntry>()

        // Resolve names/sizes up front so progress reports bytes copied rather than files completed --
        // otherwise a "1 of 3 done" count sits idle for the biggest file then blows through the rest.
        // Each metadata query runs concurrently as an independent content-provider round trip.
        val metas = coroutineScope {
            uris.map { uri -> async { uri to resolveUriMeta(resolver, uri) } }.awaitAll()
        }
        val totalBytes = metas.sumOf { (_, meta) -> meta.second }.coerceAtLeast(1L)
        var bytesCopiedSoFar = 0L

        try {
            ZipOutputStream(FileOutputStream(archiveFile)).use { zos ->
                for ((index, entry) in metas.withIndex()) {
                    val (uri, meta) = entry
                    val (rawName, size) = meta
                    var finalName = rawName
                    if (!usedNames.add(finalName)) {
                        finalName = "${TokenGenerator.generate("0123456789ABCDEF", 6)}_$rawName"
                        usedNames.add(finalName)
                    }
                    resolver.openInputStream(uri)?.use { input ->
                        zos.putNextEntry(ZipEntry(finalName))
                        val base = bytesCopiedSoFar
                        bytesCopiedSoFar = base + copyWithProgress(input, zos) { copied ->
                            onProgress(index + 1, uris.size, base + copied, totalBytes)
                        }
                        zos.closeEntry()
                    } ?: throw FileNotFoundException(finalName)
                    archivedEntries.add(ArchivedEntry(finalName, size))
                    onProgress(index + 1, uris.size, bytesCopiedSoFar, totalBytes)
                }
            }
        } catch (e: Exception) {
            archiveFile.delete()
            return@withContext importFailureResult(e)
        }

        // Purge any stale files left over from a previous session/selection, keeping the file
        // currently being served (if any) until it's replaced by this new archive below.
        pruneCacheKeeping(dir.path, listOf(archiveFile.path))
        archivedLast = archiveFile.path

        ImportResult.Success(
            FileInfo(
                name = archiveName,
                path = archiveFile.path,
                pathPart = dir.path,
                length = archiveFile.length(),
                archived = archivedEntries,
            ),
        )
    }

    /** Imports a single file already on disk, selected via the Direct Access Mode browser. */
    suspend fun importDirectAccessFile(path: String): ImportResult = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext ImportResult.DirectAccessPathMissing

        val picked = listOf(PickedFile(file.name, directPath = file.path))
        finalizeSelection(picked, File(path).parent ?: DIRECT_ACCESS_ROOT)
    }

    /** Writes shared plain text (e.g. a URL shared from another app) to a generated .txt file and imports it. */
    suspend fun importSharedText(text: String): ImportResult = withContext(Dispatchers.IO) {
        val dir = File(pickerDir(ignoreDam = true))
        if (!dir.exists()) dir.mkdirs()

        val fileName = TokenGenerator.generate("1234567890ABCDEF", 8) + ".txt"
        val destFile = File(dir, fileName)

        try {
            destFile.writeText(text)
        } catch (e: Exception) {
            return@withContext importFailureResult(e)
        }

        val picked = listOf(PickedFile(fileName, directPath = destFile.path))
        pruneCacheKeeping(dir.path, picked.mapNotNull { it.directPath })
        finalizeSelection(picked, dir.path)
    }

    /** Deletes everything in [dirPath] except [keep] and the file currently being served, if any. */
    private suspend fun pruneCacheKeeping(dirPath: String, keep: List<String>) {
        CacheManager.deleteCache(
            dirPath,
            keep + listOfNotNull(archivedLast.takeIf { it.isNotEmpty() }),
            exclude = true,
        )
    }

    /** Finalizes a single-file selection (multi-file selections are archived directly in [importUris]). */
    private suspend fun finalizeSelection(
        picked: List<PickedFile>,
        pickerDirPath: String,
    ): ImportResult = withContext(Dispatchers.IO) {
        val single = picked.firstOrNull() ?: return@withContext ImportResult.EmptySelection
        archivedLast = ""
        val path = single.directPath ?: return@withContext ImportResult.EmptySelection
        val f = File(path)
        if (!f.exists()) return@withContext ImportResult.FileGone
        ImportResult.Success(
            FileInfo(
                name = single.name,
                path = path,
                pathPart = f.parent ?: pickerDirPath,
                length = f.length(),
                archived = emptyList(),
            ),
        )
    }

    /** Simple listing helper for the Direct Access Mode file browser UI. */
    fun listDirectory(path: String): List<File> {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return (dir.listFiles()?.toList() ?: emptyList())
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    fun externalStorageRoot(): String =
        Environment.getExternalStorageDirectory()?.path ?: DIRECT_ACCESS_ROOT
}
