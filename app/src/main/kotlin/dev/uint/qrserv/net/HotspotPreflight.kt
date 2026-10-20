package dev.uint.qrserv.net

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import dev.uint.qrserv.R

enum class HotspotUnavailable { REQUIRES_ANDROID_13, NO_WIFI, BLOCKED }

val HotspotUnavailable.messageRes: Int
    get() = when (this) {
        HotspotUnavailable.REQUIRES_ANDROID_13 -> R.string.hotspot_unavailable_android
        HotspotUnavailable.NO_WIFI -> R.string.hotspot_unavailable_nowifi
        HotspotUnavailable.BLOCKED -> R.string.hotspot_unavailable_blocked
    }

data class HotspotAvailability(
    val unavailable: HotspotUnavailable? = HotspotUnavailable.REQUIRES_ANDROID_13,
    val disconnectsWifi: Boolean = false,
)

/** [hotspotUnavailableReason] for this device, as if it ran Android [sdkInt]. */
fun Context.hotspotUnavailableOn(): HotspotUnavailable? = hotspotUnavailableReason(
    sdkInt = Build.VERSION.SDK_INT,
    hasWifi = packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI),
    tetheringRestricted = getSystemService(UserManager::class.java).hasUserRestriction(UserManager.DISALLOW_CONFIG_TETHERING),
)

fun hotspotUnavailableReason(sdkInt: Int, hasWifi: Boolean, tetheringRestricted: Boolean): HotspotUnavailable? = when {
    sdkInt < 33 -> HotspotUnavailable.REQUIRES_ANDROID_13
    !hasWifi -> HotspotUnavailable.NO_WIFI
    tetheringRestricted -> HotspotUnavailable.BLOCKED
    else -> null
}

sealed interface HotspotPreflight {
    data object Ready : HotspotPreflight
    data class Unavailable(val reason: HotspotUnavailable) : HotspotPreflight

    data object NeedsWifiControl : HotspotPreflight

    data object NeedsNearby : HotspotPreflight
}

fun hotspotPreflight(
    unavailable: HotspotUnavailable?,
    wifiControlAllowed: Boolean,
    nearbyGranted: Boolean,
): HotspotPreflight = when {
    unavailable != null -> HotspotPreflight.Unavailable(unavailable)
    !wifiControlAllowed -> HotspotPreflight.NeedsWifiControl
    !nearbyGranted -> HotspotPreflight.NeedsNearby
    else -> HotspotPreflight.Ready
}
