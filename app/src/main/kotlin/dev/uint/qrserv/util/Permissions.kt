package dev.uint.qrserv.util

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/** Below Android 13 there's no runtime permission for notifications, so it's always allowed. */
fun Context.canPostNotifications(): Boolean =
    Build.VERSION.SDK_INT < 33 || isGranted(Manifest.permission.POST_NOTIFICATIONS)

/** Posts the notification from [build] under [id] when allowed; false (without building it) when not. */
fun Context.notifyIfAllowed(id: Int, build: () -> Notification): Boolean {
    // The same check as canPostNotifications(), written out next to notify() where lint can see it.
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return false
    }
    NotificationManagerCompat.from(this).notify(id, build())
    return true
}

/** From Android 17, other devices on the network can't reach the server without it; implied before that. */
fun Context.isLocalNetworkGranted(): Boolean =
    Build.VERSION.SDK_INT < 37 || isGranted(Manifest.permission.ACCESS_LOCAL_NETWORK)

/** The private hotspot needs it, along with Android 13 or later to begin with. */
fun Context.isNearbyWifiGranted(): Boolean =
    Build.VERSION.SDK_INT >= 33 && isGranted(Manifest.permission.NEARBY_WIFI_DEVICES)

/** What Direct Access Mode needs: all-files access on Android 11 or later, read access before that. */
fun Context.hasDirectAccess(): Boolean = if (Build.VERSION.SDK_INT >= 30) {
    Environment.isExternalStorageManager()
} else {
    isGranted(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/** Where all-files access is granted: this app's own screen, else the list of apps. Builds can lack either. */
@RequiresApi(30)
fun Context.directAccessSettingsIntents(): List<Intent> = listOf(
    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:$packageName".toUri()),
    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
)

/** False where Direct Access Mode's permission can't be granted, as on builds (TV, Go, some OEMs) without its screens. */
fun Context.canGetDirectAccess(): Boolean =
    Build.VERSION.SDK_INT < 30 || hasDirectAccess() ||
        directAccessSettingsIntents().any { packageManager.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY) != null }

private fun Context.isGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
