package dev.uint.qrserv.ui.theme

import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import dev.uint.qrserv.R
import dev.uint.qrserv.data.ThemeMode

private val AppFontFamily = FontFamily(
    Font(R.font.nunito_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.nunito_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.nunito_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.nunito_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private fun Typography.withFontFamily(fontFamily: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = fontFamily),
    displayMedium = displayMedium.copy(fontFamily = fontFamily),
    displaySmall = displaySmall.copy(fontFamily = fontFamily),
    headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
    headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
    headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
    titleLarge = titleLarge.copy(fontFamily = fontFamily),
    titleMedium = titleMedium.copy(fontFamily = fontFamily),
    titleSmall = titleSmall.copy(fontFamily = fontFamily),
    bodyLarge = bodyLarge.copy(fontFamily = fontFamily),
    bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
    bodySmall = bodySmall.copy(fontFamily = fontFamily),
    labelLarge = labelLarge.copy(fontFamily = fontFamily),
    labelMedium = labelMedium.copy(fontFamily = fontFamily),
    labelSmall = labelSmall.copy(fontFamily = fontFamily),
)

private val LightColors = lightColorScheme(
    primary = BrandAccent,
    onPrimary = Color.White,
    secondary = BrandAccentSoft,
    background = BrandCanvasLight,
    surface = BrandCardLight,
    surfaceVariant = BrandCardLight,
    error = BrandError,
)

private val DarkColors = darkColorScheme(
    primary = BrandAccent,
    secondary = BrandAccentSoft,
    background = BrandCanvasDark,
    surface = BrandCardDark,
    surfaceVariant = BrandCardDark,
    error = BrandError,
)

private val AppTypography = Typography().withFontFamily(AppFontFamily).let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp),
    )
}

/**
 * App theme. Uses Material You dynamic color on Android 12+ (so the app matches the user's
 * wallpaper-derived palette like other native apps), falling back to the brand's purple palette
 * on older versions.
 */
@Composable
fun QRServTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val baseColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    // Nudge container surfaces darker without touching hue/branding -- keeps whatever palette
    // was picked above (including Material You's wallpaper-derived one) otherwise untouched.
    val colorScheme = baseColorScheme.copy(
        surface = lerp(baseColorScheme.surface, Color.Black, 0.12f),
        surfaceVariant = lerp(baseColorScheme.surfaceVariant, Color.Black, 0.12f),
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}

/**
 * A subtle tint off the screen background -- 6% toward black/white depending on which reads as
 * "recessed" for the current theme. Used to group related content (settings list groups, status
 * containers) without the stronger contrast of a full surface color.
 */
@Composable
fun subtleContainerColor(): Color {
    val background = MaterialTheme.colorScheme.background
    val target = if (background.luminance() < 0.5f) Color.White else Color.Black
    return lerp(background, target, 0.06f)
}

/**
 * The system nav bar's own inset (up to ~48dp for 3-button nav) reads as excessive empty space
 * for this app's simple screens -- replaced with a small fixed gap on the bottom, and the
 * dynamic top (status bar) inset gets a little extra breathing room added on top of it. Unioned
 * with displayCutout, not just systemBars, since a notch/cutout isn't always fully covered by the
 * status bar -- most visibly a side cutout once the device is rotated into one of this app's
 * landscape/wide layouts, where the status bar's own inset doesn't protect the sides at all.
 * The horizontal side is symmetrized (same clearance on both edges, sized to whichever side
 * actually needs more) rather than applied as-is -- a cutout confined to just one side would
 * otherwise shift screens with centered content off the true screen center, since content is
 * only ever centered within whatever width padding leaves behind, not the true full width.
 */
@Composable
fun reducedBottomInsetContentWindowInsets(): WindowInsets {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val combined = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val horizontalPx = maxOf(
        combined.getLeft(density, layoutDirection),
        combined.getRight(density, layoutDirection),
    )
    return WindowInsets(
        left = horizontalPx,
        top = combined.getTop(density),
        right = horizontalPx,
        bottom = 0,
    ).add(WindowInsets(top = 8.dp, bottom = 16.dp))
}

/**
 * Transparent, not literally the background color: TopAppBar animates its own containerColor
 * internally (meant for scroll-driven elevation tinting, which none of this app's bars trigger
 * since none use a scrollBehavior), so a theme switch would visibly lag behind the Scaffold's
 * own background -- which paints underneath the bar instantly -- by that animation's duration.
 * Transparent lets that instant background show through instead, with no separate panel look.
 */
@Composable
fun transparentTopAppBarColors(): TopAppBarColors =
    TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)

/**
 * Reduces this dialog's backdrop dim from the platform default (~0.6 alpha) to something lighter.
 * Compose's Dialog/AlertDialog/BasicAlertDialog don't expose the scrim as a parameter -- it lives
 * on the underlying dialog Window instead, reached via the hosting View's DialogWindowProvider.
 * Call this once from anywhere inside a dialog's own content (any composable slot works).
 */
@Composable
fun ReducedDialogScrim() {
    val view = LocalView.current
    SideEffect {
        (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0.4f)
    }
}

/** The device's actual current dark/light setting, independent of any per-Activity override. */
fun isSystemActuallyInDarkTheme(): Boolean {
    val uiMode = Resources.getSystem().configuration.uiMode
    return (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}

/** Resolves this preference to an actual dark/light boolean, e.g. for [QRServTheme]'s `darkTheme`. */
fun ThemeMode.resolveIsDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemActuallyInDarkTheme()
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
}
