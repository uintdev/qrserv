package dev.uint.qrserv.net

enum class HotspotUnavailable { REQUIRES_ANDROID_13, NO_WIFI, BLOCKED }

data class HotspotAvailability(
    val unavailable: HotspotUnavailable? = HotspotUnavailable.REQUIRES_ANDROID_13,
    val disconnectsWifi: Boolean = false,
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
