package dev.uint.qrserv.ui.components

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import dev.uint.qrserv.R

fun shareText(context: Context, text: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(shareIntent, null))
}

fun copyToClipboard(context: Context, text: String, sensitive: Boolean = false, label: String? = null) {
    val clip = ClipData.newPlainText(label, text)
    if (sensitive) {
        // Hides the password from the clipboard preview; ignored before Android 13.
        clip.description.extras = PersistableBundle().apply {
            putBoolean(
                if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE",
                true,
            )
        }
    }
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
    Toast.makeText(context, context.getString(R.string.page_imported_share_clipboard), Toast.LENGTH_SHORT).show()
}
