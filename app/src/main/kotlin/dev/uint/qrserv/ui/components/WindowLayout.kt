package dev.uint.qrserv.ui.components

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.map

@Composable
fun rememberIsWideScreen(): Boolean {
    // containerSize/containerDpSize reflects the actual hosting window, unlike
    // Configuration.screenWidthDp/screenHeightDp which can be stale or mismatched in
    // multi-window/embedded scenarios.
    val containerDpSize = LocalWindowInfo.current.containerDpSize
    // On a foldable opened flat on a table, the hinge runs horizontally and the screen is
    // effectively split top/bottom -- same reachability problem as a wide/short window, so both
    // trigger the same adaptive treatment below.
    val activity = LocalActivity.current
    val isTabletopPosture by produceState(initialValue = false, activity) {
        if (activity == null) return@produceState
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity)
            .map { layoutInfo ->
                layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>().any {
                    it.state == FoldingFeature.State.HALF_OPENED && it.orientation == FoldingFeature.Orientation.HORIZONTAL
                }
            }
            .collect { value = it }
    }
    // A width breakpoint (not aspect ratio) -- this needs to stay true for a tablet in portrait
    // too, which is still comfortably wide despite being taller than it is wide. Covers a phone
    // rotated to landscape, a tablet in either orientation, and a foldable unfolded to its large
    // display, all alike.
    val windowSizeClass = remember(containerDpSize) {
        WindowSizeClass.BREAKPOINTS_V1.computeWindowSizeClass(
            widthDp = containerDpSize.width.value,
            heightDp = containerDpSize.height.value,
        )
    }
    return windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) || isTabletopPosture
}

@Composable
fun isShortWindow(): Boolean = LocalWindowInfo.current.containerDpSize.height < 480.dp

@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
