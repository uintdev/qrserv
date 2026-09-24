package dev.uint.qrserv.ui

import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.uint.qrserv.ui.screens.AboutDialog
import dev.uint.qrserv.ui.screens.DamBrowserScreen
import dev.uint.qrserv.ui.screens.HotspotPermissionDialog
import dev.uint.qrserv.ui.screens.HotspotScreen
import dev.uint.qrserv.ui.screens.MainScreen
import dev.uint.qrserv.ui.screens.SettingsScreen
import dev.uint.qrserv.viewmodel.QRServViewModel
import dev.uint.qrserv.viewmodel.UiEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private enum class Screen { MAIN, SETTINGS, DAM_BROWSER, HOTSPOT }

// android.view.animation.BackGestureInterpolator's exact curve (PathInterpolator(0.1, 0.1, 0, 1)):
// a cubic bezier with control points (0.1, 0.1) and (0, 1), same parameterization as Android's
// PathInterpolator(x1, y1, x2, y2).
private val BackGestureEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

/**
 * Plays the same "commit" finish -- position/scale/shadow spring plus the front-loaded fade --
 * that a completed predictive-back gesture plays, but callable directly for a plain back-button
 * tap, which never goes through PredictiveBackHandler's progress Flow at all.
 */
private suspend fun playBackCommitAnimation(
    backProgressAnim: Animatable<Float, AnimationVector1D>,
    commitFadeAnim: Animatable<Float, AnimationVector1D>,
    initialVelocity: Float = 0.5f,
) {
    coroutineScope {
        launch {
            backProgressAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
                initialVelocity = initialVelocity,
            )
        }
        launch { commitFadeAnim.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) }
    }
}

@Composable
fun QRServApp(
    viewModel: QRServViewModel,
    onOpenSafPicker: () -> Unit,
    onRequestDamPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestNearbyPermission: () -> Unit,
    onLaunchNearbyPrompt: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    var screen by rememberSaveable { mutableStateOf(Screen.MAIN) }
    var showAbout by rememberSaveable { mutableStateOf(false) }

    val context by rememberUpdatedState(LocalContext.current)
    val uiState by viewModel.uiState.collectAsState()

    // A sharesheet import can start while Settings/DAM browser is active (e.g. resumed via
    // onNewIntent) -- fall back to MAIN so it doesn't show either screen over an import it didn't
    // start. Edge-triggered on loading's false->true transition, not a continuous gate -- otherwise
    // navigating to Settings/DAM browser during an already-running import (which didn't interrupt
    // either screen) would be suppressed too, stuck showing MAIN until the import finished.
    LaunchedEffect(uiState.actionButtonLoading) {
        if (uiState.actionButtonLoading) {
            if (screen != Screen.MAIN) screen = Screen.MAIN
            // Same reasoning as above, but the About dialog isn't part of the screen enum -- it can
            // be open regardless of `screen` -- so it needs its own guard.
            showAbout = false
        }
    }

    LaunchedEffect(uiState.notificationPermissionPending) {
        if (uiState.notificationPermissionPending) {
            onRequestNotificationPermission()
            viewModel.onNotificationPermissionRequested()
        }
    }

    LaunchedEffect(uiState.hotspot) {
        if (uiState.hotspot == null && screen == Screen.HOTSPOT) screen = Screen.MAIN
    }

    LaunchedEffect(uiState.nearbyPermissionRequest) {
        if (uiState.nearbyPermissionRequest) onRequestNearbyPermission()
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                UiEvent.OpenSafPicker -> onOpenSafPicker()
                UiEvent.OpenDamBrowser -> screen = Screen.DAM_BROWSER
                UiEvent.RequestDamPermission -> onRequestDamPermission()
                UiEvent.PortSaved -> {}
                is UiEvent.Toast -> {
                    val message = if (event.event.args.isEmpty()) {
                        context.getString(event.event.resId)
                    } else {
                        context.getString(event.event.resId, *event.event.args.toTypedArray())
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val scope = rememberCoroutineScope()
    val backProgressAnim = remember { Animatable(0f) }
    // Distinct from backProgressAnim: stays 0 during the live drag and only animates once the
    // gesture commits, so the fade is part of the finish, not the whole drag.
    val commitFadeAnim = remember { Animatable(0f) }
    // Guards against a second tap (or a lingering cancel spring) launching another animateTo on
    // the same Animatable, which would cancel the first mid-flight -- including the screen change after it.
    var isPlayingBackTransition by remember { mutableStateOf(false) }
    // Frozen snapshot of backProgressAnim.value at commit time. A spring's settling time is fixed
    // regardless of distance, so animating from the live value would make shallow releases rush;
    // interpolating from this snapshot via commitFadeAnim's own timeline keeps commit motion consistent.
    var commitStartProgress by remember { mutableFloatStateOf(0f) }

    // screen = Screen.MAIN flips PredictiveBackHandler's enabled to false, canceling its own
    // coroutine -- so resetting these inline right after that assignment risks landing on that
    // cancellation point. A separate LaunchedEffect keyed on the same value sidesteps the race.
    LaunchedEffect(screen) {
        if (screen == Screen.MAIN) {
            backProgressAnim.snapTo(0f)
            commitFadeAnim.snapTo(0f)
            isPlayingBackTransition = false
        }
    }

    PredictiveBackHandler(enabled = screen != Screen.MAIN) { progress ->
        try {
            // Tracks finger speed at release so the commit spring can inherit it instead of
            // always starting from rest.
            var lastProgress = 0f
            var lastTimeNanos = System.nanoTime()
            var releaseVelocity = 0f
            progress.collect { event ->
                // event.swipeEdge is ignored -- this always animates as a left-edge swipe regardless of
                // where the gesture started. The raw progress is run through BackGestureEasing first (AOSP's
                // own BackGestureInterpolator does the same), which is why the drag visually "settles" onto
                // its end state well before your finger reaches the screen edge, rather than tracking linearly.
                val easedProgress = BackGestureEasing.transform(event.progress)
                val now = System.nanoTime()
                val deltaSeconds = (now - lastTimeNanos) / 1_000_000_000f
                if (deltaSeconds > 0f) {
                    releaseVelocity = (easedProgress - lastProgress) / deltaSeconds
                }
                lastProgress = easedProgress
                lastTimeNanos = now
                backProgressAnim.snapTo(easedProgress)
            }
            // Gesture committed: the touch can release at any progress short of 1f, so this finishes the
            // motion smoothly before swapping screens rather than jumping to the end state. Safe to await
            // directly here since PredictiveBackHandler is still enabled/uncanceled at this point.
            // DampingRatioLowBouncy matches AOSP's postCommitFlingSpring, meant to overshoot slightly rather
            // than glide to a stop. Velocity ceiling lowered from 6f -- that much velocity produced an
            // aggressive overshoot instead of a smooth one.
            val commitVelocity = releaseVelocity.coerceIn(0.5f, 2.5f)
            commitStartProgress = backProgressAnim.value
            playBackCommitAnimation(backProgressAnim, commitFadeAnim, commitVelocity)
            // Mirrors DamBrowserScreen's own Cancel button, which also resets the
            // loading flag set by onImportClicked() -- otherwise it's stuck spinning.
            if (screen == Screen.DAM_BROWSER) viewModel.onImportCancelled()
            screen = Screen.MAIN
        } catch (_: CancellationException) {
            // Gesture canceled -- springs back rather than snapping. This coroutine is itself being
            // canceled right now, so the animation must run in a separate scope, or it'd never play.
            // commitFadeAnim never left 0 during a cancel, so it needs no reset here.
            scope.launch {
                backProgressAnim.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
            }
        }
    }
    // Only the 0/nonzero threshold crossing needs to trigger recomposition -- the continuous value
    // is read directly inside the graphicsLayer blocks instead, so a live drag (touching these dozens
    // of times a second) doesn't recompose the whole app every frame.
    val showPeek by remember { derivedStateOf { screen != Screen.MAIN && backProgressAnim.value > 0f } }

    // Mimics the system's predictive-back preview: outgoing screen shrinks/rounds while sliding to
    // the left edge (pivoting from that edge, not center), lifting off peekScrimModifier's flat
    // backdrop rather than casting a real shadow. Reading backProgressAnim/commitFadeAnim directly
    // inside this draw-phase graphicsLayer (rather than hoisted vals) means the Modifier is safe to
    // build once and reuse -- values still update live on every draw.
    val predictiveBackShape = remember { RoundedCornerShape(28.dp) }
    val predictiveBackModifier = remember {
        Modifier.graphicsLayer {
            // commitFadeAnim stays at 0 for the entire live drag and only animates once the
            // gesture is committed.
            val commitProgress = commitFadeAnim.value.coerceIn(0f, 1f)
            // Live drag tracks backProgressAnim directly; once committed, interpolates from the frozen
            // commitStartProgress snapshot (see above) via commitProgress's own timeline instead.
            val backProgress = if (commitProgress > 0f) {
                commitStartProgress + (1f - commitStartProgress) * commitProgress
            } else {
                backProgressAnim.value
            }
            transformOrigin = TransformOrigin(0f, 0.5f)
            val scale = 1f - backProgress * 0.1f
            scaleX = scale
            scaleY = scale
            // Small on purpose -- just enough of a gap on the left edge to peek the destination
            // screen (peekBackgroundModifier below) underneath, not a wide reveal.
            val dragShift = backProgress * (size.width * 0.12f)
            // On commit, a further short push onward (continuing the same direction it was
            // dragged) as it fades, rather than stopping dead where the drag left it.
            val commitFlick = commitProgress * (size.width * 0.18f)
            translationX = dragShift + commitFlick
            // Fixed corner radius (not animated in) avoids re-tessellating the clip outline every frame.
            // No shadowElevation -- depth comes from peekScrimModifier's flat backdrop instead of a real
            // drop shadow.
            shape = predictiveBackShape
            clip = true
            // Light front-load -- snappier than a fully linear fade, without flickering.
            alpha = (1f - commitProgress * 1.4f).coerceAtLeast(0f)
        }
    }

    // The screen being backed out *to* also shrinks slightly as you drag -- both layers receding
    // together reads as real depth, ending up smaller than the foreground at the same point.
    val peekBackgroundModifier = remember {
        Modifier.graphicsLayer {
            val commitProgress = commitFadeAnim.value.coerceIn(0f, 1f)
            // Same frozen-snapshot reasoning as commitStartProgress above.
            val backProgress = if (commitProgress > 0f) {
                commitStartProgress + (1f - commitStartProgress) * commitProgress
            } else {
                backProgressAnim.value
            }
            // Anchored to the left edge (matching the foreground's shift) so this fills the gap left
            // behind, rather than uniformly shrinking from center which read as a generic dim.
            transformOrigin = TransformOrigin(0f, 0.5f)
            val dragShrink = backProgress * 0.14f
            // Smoothly resizes back to its original full size once the gesture is committed,
            // rather than staying shrunk at whatever the drag happened to leave it at.
            val scale = 1f - dragShrink * (1f - commitProgress)
            scaleX = scale
            scaleY = scale
            val peekShift = size.width * 0.03f
            val dragOffset = (1f - backProgress) * peekShift
            // Same commit-driven resolution as the scale above, so position and size finish
            // settling together.
            translationX = -dragOffset * (1f - commitProgress)
        }
    }
    // Matches the system's own scrim: noticeably heavier in dark theme than in light theme,
    // rather than one flat intensity regardless of which palette is active.
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val maxScrimAlpha = if (isDarkTheme) 0.275f else 0.125f
    // Alpha applied via graphicsLayer (draw-phase, deferred) rather than Color.copy(alpha=...),
    // which would be a composition-time read and recompose the whole app every frame.
    val peekScrimModifier = remember(maxScrimAlpha) {
        Modifier
            .graphicsLayer {
                // Fixed at maxScrimAlpha during the drag -- not tied to backProgressAnim, so only the sizes
                // change while dragging, not the backdrop. Clears once committed, deliberately slower than
                // predictiveBackModifier's own fade so the backdrop lingers a beat after the foreground has
                // already faded, rather than both clearing in lockstep.
                val commitProgress = commitFadeAnim.value.coerceIn(0f, 1f)
                alpha = ((1f - commitProgress) * maxScrimAlpha).coerceAtLeast(0f)
            }
            .background(Color.Black)
    }

    // Shared by SettingsScreen's onBack and DamBrowserScreen's onCancel -- a button tap plays the
    // same commit transition as a swipe, always from the left, guarded against double-taps.
    fun playButtonBackTransition(onSettled: () -> Unit) {
        if (isPlayingBackTransition) return
        isPlayingBackTransition = true
        // A button tap has no prior drag at all, so this always starts the commit motion from
        // scratch -- same as a swipe committed the instant it crossed the edge.
        commitStartProgress = 0f
        scope.launch {
            playBackCommitAnimation(backProgressAnim, commitFadeAnim)
            onSettled()
        }
    }

    // Each screen owns its own Scaffold/TopAppBar and handles insets itself -- this outer one is
    // just a shared background, so it shouldn't reserve space for system bars too (double-up).
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Single always-present composition for both "at rest" and "peeking" states -- previously two
            // separate MainScreen call sites, which meant a back transition mounted a brand-new instance
            // right as the old one's fade/scrim reached fully invisible, showing up as a brief black flash
            // where only the swap itself was visible.
            MainScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(padding).then(if (showPeek) peekBackgroundModifier else Modifier),
                onOpenSettings = { if (screen == Screen.MAIN) screen = Screen.SETTINGS },
                onOpenAbout = { if (screen == Screen.MAIN) showAbout = true },
                onOpenHotspot = { if (screen == Screen.MAIN) screen = Screen.HOTSPOT },
                onHotspotScreenDue = {
                    screen = Screen.HOTSPOT
                    viewModel.onHotspotScreenOpened()
                },
            )
            if (showPeek) {
                Box(modifier = Modifier.fillMaxSize().then(peekScrimModifier))
            }
            when (screen) {
                Screen.MAIN -> Unit
                Screen.SETTINGS -> SettingsScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(padding).then(predictiveBackModifier),
                    onBack = {
                        playButtonBackTransition {
                            screen = Screen.MAIN
                        }
                    },
                )
                Screen.HOTSPOT -> HotspotScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(padding).then(predictiveBackModifier),
                    onBack = { playButtonBackTransition { screen = Screen.MAIN } },
                )
                Screen.DAM_BROWSER -> DamBrowserScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(padding).then(predictiveBackModifier),
                    onFileChosen = { path ->
                        screen = Screen.MAIN
                        viewModel.onDirectAccessFileChosen(path)
                    },
                    onCancel = {
                        // Only reached when goUpOrCancel() is already at the browser's root -- i.e. genuinely
                        // exiting rather than navigating up a folder in place.
                        playButtonBackTransition {
                            viewModel.onImportCancelled()
                            screen = Screen.MAIN
                        }
                    },
                )
            }

            if (showAbout) {
                AboutDialog(onDismiss = { showAbout = false })
            }
            uiState.hotspotDialog?.let { dialog ->
                HotspotPermissionDialog(
                    dialog = dialog,
                    onDismiss = viewModel::onHotspotDialogDismissed,
                    onContinue = {
                        viewModel.onHotspotDialogContinue()
                        onLaunchNearbyPrompt()
                    },
                    onOpenSettings = onOpenAppSettings,
                )
            }
        }
    }
}
