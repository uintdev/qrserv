package dev.uint.qrserv.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import dev.uint.qrserv.R
import dev.uint.qrserv.net.HotspotUnavailable
import dev.uint.qrserv.net.hotspotUnavailableOn
import dev.uint.qrserv.net.messageRes

enum class AppShortcut(val id: String, val action: String) {
    SHARE_FILE("share_file", "dev.uint.qrserv.action.SHARE_FILE"),
    SHARE_HOTSPOT("share_hotspot", "dev.uint.qrserv.action.SHARE_HOTSPOT"),
    ;

    companion object {
        fun fromAction(action: String?): AppShortcut? = entries.firstOrNull { it.action == action }
    }
}

/**
 * The non-exported alias of MainActivity (see the manifest) that shortcuts launch through, so only
 * they can carry the shortcut actions -- MainActivity itself is exported for the launcher and share sheet.
 */
const val SHORTCUT_LAUNCHER = "dev.uint.qrserv.ShortcutLauncher"

/** The hotspot one only where the private hotspot can work at all, as its button is disabled otherwise. */
internal fun availableShortcuts(hotspotUnavailable: HotspotUnavailable?): List<AppShortcut> =
    if (hotspotUnavailable == null) AppShortcut.entries else listOf(AppShortcut.SHARE_FILE)

/**
 * Publishes the launcher shortcuts for the current device state and language. Dynamic rather than
 * static: static ones launch with FLAG_ACTIVITY_CLEAR_TASK, which would end a share in progress.
 */
fun publishAppShortcuts(context: Context) {
    val hotspotUnavailable = context.hotspotUnavailableOn()
    val shortcuts = availableShortcuts(hotspotUnavailable).mapIndexed { rank, shortcut ->
        val (shortLabel, longLabel, icon) = when (shortcut) {
            AppShortcut.SHARE_FILE ->
                Triple(R.string.shortcut_share_file_short, R.string.shortcut_share_file_long, R.drawable.ic_shortcut_share_file)
            AppShortcut.SHARE_HOTSPOT ->
                Triple(R.string.shortcut_hotspot_short, R.string.hotspot_share_button, R.drawable.ic_shortcut_hotspot)
        }
        ShortcutInfoCompat.Builder(context, shortcut.id)
            .setShortLabel(context.getString(shortLabel))
            .setLongLabel(context.getString(longLabel))
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(Intent(shortcut.action).setClassName(context, SHORTCUT_LAUNCHER))
            .setRank(rank)
            .build()
    }
    // A pinned copy outlives its dynamic one, so it's switched off (with the reason) rather than left to fail.
    if (hotspotUnavailable == null) {
        ShortcutManagerCompat.enableShortcuts(context, shortcuts.filter { it.id == AppShortcut.SHARE_HOTSPOT.id })
    }
    ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    if (hotspotUnavailable != null) {
        ShortcutManagerCompat.disableShortcuts(
            context,
            listOf(AppShortcut.SHARE_HOTSPOT.id),
            context.getString(hotspotUnavailable.messageRes),
        )
    }
}

/** Shortcut labels are stored as text, so they're republished in the new language. */
class LocaleChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_LOCALE_CHANGED) publishAppShortcuts(context)
    }
}
