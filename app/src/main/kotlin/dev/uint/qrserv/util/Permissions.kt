package dev.uint.qrserv.util

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

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

/** The private hotspot needs it, along with Android 13 or later to begin with. */
fun Context.isNearbyWifiGranted(): Boolean =
    Build.VERSION.SDK_INT >= 33 && isGranted(Manifest.permission.NEARBY_WIFI_DEVICES)

/** What Direct Access Mode needs: all-files access on Android 11 or later, read access before that. */
fun Context.hasDirectAccess(): Boolean = if (Build.VERSION.SDK_INT >= 30) {
    Environment.isExternalStorageManager()
} else {
    isGranted(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun Context.isGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
