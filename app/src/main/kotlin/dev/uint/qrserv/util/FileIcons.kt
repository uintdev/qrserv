package dev.uint.qrserv.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.ui.graphics.vector.ImageVector

private val archiveExt = setOf("7z", "xz", "bz2", "gz", "tar", "zip", "rar", "cab")
private val imageExt = setOf(
    "png", "jpg", "jpeg", "webp", "avif", "bmp", "gif", "heic", "heif", "svg", "tif", "tiff"
)
private val videoExt = setOf("3gp", "avi", "mkv", "mov", "mp4", "mpeg", "mpg", "webm", "wmv")
private val audioExt = setOf(
    "aac", "aiff", "flac", "m4a", "mid", "midi", "mka", "mp3", "ogg", "wav", "weba", "wma"
)

fun iconForFileName(fileName: String): ImageVector {
    val dotIndex = fileName.lastIndexOf('.')
    if (dotIndex == -1) return Icons.AutoMirrored.Filled.InsertDriveFile
    val ext = fileName.substring(dotIndex + 1).lowercase()

    return when (ext) {
        in archiveExt -> Icons.Filled.FolderZip
        in imageExt -> Icons.Filled.Image
        in videoExt -> Icons.Filled.VideoFile
        in audioExt -> Icons.Filled.AudioFile
        "apk" -> Icons.Filled.Android
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}
