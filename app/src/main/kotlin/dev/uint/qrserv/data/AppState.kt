package dev.uint.qrserv.data

enum class PageType {
    LANDING,
    IMPORTED,
    NO_CONNECTION,
    SNAPSHOT_ERROR,
    FILE_REMOVED,
    FILE_MODIFIED,
    PERMISSION_DENIED,
    INSUFFICIENT_STORAGE,
    PORT_IN_USE,
    FALLBACK,
    UNHANDLED_ERROR,
}

/** One entry inside a multi-file archive. */
data class ArchivedEntry(val name: String, val size: Long)

/** User's app theme preference, persisted via [dev.uint.qrserv.data.Preferences]. */
enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** Reads the persisted theme preference, defaulting to SYSTEM if unset or unrecognized. */
fun readPersistedThemeMode(): ThemeMode =
    Preferences.readString(Preferences.PREF_THEME_MODE)
        ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
        ?: ThemeMode.SYSTEM

/**
 * Progress of copying a selection in (a single large file, or zipping several into an archive).
 * [completedFiles]/[totalFiles] is shown as a count for multi-file imports, but the bar itself
 * is always driven by [bytesCopied]/[totalBytes] -- file count alone stalls or jumps when one
 * file dominates the total size, and is meaningless (always 0 until the very end) for one file.
 */
data class ImportProgress(
    val completedFiles: Int,
    val totalFiles: Int,
    val bytesCopied: Long,
    val totalBytes: Long,
)

data class FileInfo(
    val name: String = "",
    val path: String = "",
    val pathPart: String = "",
    val length: Long = 0,
    val archived: List<ArchivedEntry> = emptyList(),
)

data class AppUiState(
    val pageType: PageType = PageType.LANDING,
    val actionButtonLoading: Boolean = false,
    val serverRunning: Boolean = false,
    val serverPoweringDown: Boolean = false,
    val fileInfo: FileInfo = FileInfo(),
    val interfaces: List<String> = emptyList(),
    val selectedIp: String = "",
    val port: Int = 0,
    val savedPort: Int = 0,
    val damEnabled: Boolean = false,
    val damEligible: Boolean = true,
    val damBuildIneligible: Boolean = false,
    val fiuEnabled: Boolean = false,
    val fallbackCode: String = "",
    val importProgress: ImportProgress? = null,
    /** Raw exception detail shown (and copyable) on [PageType.UNHANDLED_ERROR]. */
    val errorDetail: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)
