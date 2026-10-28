package dev.uint.qrserv

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import dev.uint.qrserv.data.HotspotDialog
import dev.uint.qrserv.data.Preferences
import dev.uint.qrserv.data.ThemeMode
import dev.uint.qrserv.data.readPersistedThemeMode
import dev.uint.qrserv.ui.QRServApp
import dev.uint.qrserv.ui.theme.QRServTheme
import dev.uint.qrserv.ui.theme.isSystemActuallyInDarkTheme
import dev.uint.qrserv.ui.theme.resolveIsDark
import dev.uint.qrserv.util.AppShortcut
import dev.uint.qrserv.util.SHORTCUT_LAUNCHER
import dev.uint.qrserv.util.directAccessSettingsIntents
import dev.uint.qrserv.util.isNearbyWifiGranted
import dev.uint.qrserv.util.publishAppShortcuts
import dev.uint.qrserv.viewmodel.QRServViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: QRServViewModel by viewModels()

    private var systemInDarkTheme by mutableStateOf(isSystemActuallyInDarkTheme())

    // Android 12+ moves a root task started from the launcher icon to the background on back; anywhere
    // else, back would finish the activity and end the share, so it's done here instead. Compose
    // registers the other screens' back handlers later, so they still come first.
    private val moveToBackOnBack = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            moveTaskToBack(true)
        }
    }

    private var startedFromHomeScreen = false

    private val safPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            viewModel.onFilesPicked(uris.filter(::isAcceptableShareUri))
        }

    private val legacyStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onDirectAccessPermissionResult(granted)
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onNotificationPermissionResult(granted)
        }

    // No rationale after a denial means it can only be granted in Settings now.
    private val nearbyPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            when {
                granted -> viewModel.hotspot.onNearbyPermissionResult(true)
                Build.VERSION.SDK_INT >= 33 &&
                    !shouldShowRequestPermissionRationale(android.Manifest.permission.NEARBY_WIFI_DEVICES) ->
                    viewModel.hotspot.showHotspotDialog(HotspotDialog.NEARBY_SETTINGS)
                else -> viewModel.hotspot.onNearbyPermissionResult(false)
            }
        }

    private val manageStorageSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.onDirectAccessPermissionResult(viewModel.hasDirectAccessPermission())
        }

    // Runs before the window/theme is created, so the cold-start windowBackground (themes.xml's
    // light/dark split) matches the in-app theme preference instead of the system setting --
    // otherwise a Dark-mode user on a light system would see a light flash before Compose corrects it.
    override fun attachBaseContext(newBase: Context) {
        val themeMode = readPersistedThemeMode()
        val baseUiMode = newBase.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()
        val configOverride = Configuration()
        when (themeMode) {
            ThemeMode.DARK -> configOverride.uiMode = baseUiMode or Configuration.UI_MODE_NIGHT_YES
            ThemeMode.LIGHT -> configOverride.uiMode = baseUiMode or Configuration.UI_MODE_NIGHT_NO
            ThemeMode.SYSTEM -> Unit
        }
        super.attachBaseContext(newBase.createConfigurationContext(configOverride))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Restored on recreation, as the intent may have been replaced since. Read before the share
        // and shortcut handling below clear the action.
        startedFromHomeScreen = savedInstanceState?.getBoolean(STATE_STARTED_FROM_HOME_SCREEN)
            ?: (Build.VERSION.SDK_INT >= 31 && isHomeScreenLaunch())
        updateBackHandling()
        onBackPressedDispatcher.addCallback(this, moveToBackOnBack)

        val relaunched = savedInstanceState != null ||
            intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (relaunched || !handleShareIntent(intent)) {
            viewModel.purgeStaleCacheOnLaunch()
        }
        if (!relaunched) handleShortcutIntent(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            // Not isSystemInDarkTheme() for the SYSTEM case -- that reads LocalConfiguration, which
            // attachBaseContext's override permanently forced one way if the stored preference was
            // DARK/LIGHT at launch. systemInDarkTheme follows the real device setting instead (see
            // onConfigurationChanged), so switching back to System mid-session takes effect immediately.
            val darkTheme = uiState.themeMode.resolveIsDark(systemInDarkTheme)
            // windowLightStatusBar/windowLightNavigationBar in themes.xml are static, resolved once
            // at window creation -- they never react to a mid-session theme switch, so this keeps them live.
            val view = LocalView.current
            SideEffect {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
            QRServTheme(darkTheme = darkTheme) {
                QRServApp(
                    viewModel = viewModel,
                    onOpenSafPicker = ::openSafPicker,
                    onRequestDamPermission = ::requestDirectAccessPermission,
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onRequestNearbyPermission = ::handleNearbyPermissionRequest,
                    onLaunchNearbyPrompt = ::launchNearbyPrompt,
                    onOpenAppSettings = ::openAppSettings,
                )
            }
        }
    }

    // uiMode is in configChanges, so a system theme switch arrives here instead of recreating the
    // Activity. Compose can't observe Resources.getSystem(), and LocalConfiguration's uiMode stays
    // pinned by attachBaseContext's override, so this state is what makes the SYSTEM theme live.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        systemInDarkTheme = isSystemActuallyInDarkTheme()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_STARTED_FROM_HOME_SCREEN, startedFromHomeScreen)
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppResumed()
        updateBackHandling()
        // Picks up anything that changed the shortcuts while the app was away (policy, language).
        lifecycleScope.launch(Dispatchers.IO) { publishAppShortcuts(applicationContext) }
    }

    private fun handleNearbyPermissionRequest() {
        viewModel.hotspot.onNearbyPermissionRequestTaken()
        if (Build.VERSION.SDK_INT < 33) return
        val permission = android.Manifest.permission.NEARBY_WIFI_DEVICES
        when {
            isNearbyWifiGranted() -> viewModel.hotspot.onNearbyPermissionResult(true)
            // Permanently denied: the prompt returns at once and leads to the settings dialog.
            Preferences.wasAskedOnThisInstall(Preferences.PREF_NEARBY_ASKED) && !shouldShowRequestPermissionRationale(permission) ->
                launchNearbyPrompt()
            else -> viewModel.hotspot.showHotspotDialog(HotspotDialog.EXPLAIN_NEARBY)
        }
    }

    private fun launchNearbyPrompt() {
        if (Build.VERSION.SDK_INT < 33) return
        Preferences.markAskedOnThisInstall(Preferences.PREF_NEARBY_ASKED)
        nearbyPermissionLauncher.launch(android.Manifest.permission.NEARBY_WIFI_DEVICES)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Once any other intent reaches the task (a share, even the app's own notification), Android's
        // own back finishes the activity and the share; the home screen's launcher intent doesn't.
        startedFromHomeScreen = startedFromHomeScreen && isHomeScreenLaunch()
        updateBackHandling()
        handleShareIntent(intent)
        handleShortcutIntent(intent)
    }

    // Rechecked on resume, as isTaskRoot can change while the activity lives.
    private fun updateBackHandling() {
        moveToBackOnBack.isEnabled = !(startedFromHomeScreen && isTaskRoot)
    }

    /**
     * Android's own test, on the current intent: exactly a launcher intent, sent by the home screen.
     * In onNewIntent, [getReferrer] is that intent's sender.
     */
    private fun isHomeScreenLaunch(): Boolean {
        val launcherIntent = intent.action == Intent.ACTION_MAIN &&
            intent.categories == setOf(Intent.CATEGORY_LAUNCHER) && intent.data == null && intent.type == null
        if (!launcherIntent) return false
        // Supplied by the launching app, so not to be trusted.
        val callerReferrer = readExtras(intent) {
            hasExtra(Intent.EXTRA_REFERRER) || hasExtra(Intent.EXTRA_REFERRER_NAME)
        } ?: true
        if (callerReferrer) return false
        val home = packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName
        return home != null && referrer?.host == home
    }

    private fun handleShortcutIntent(intent: Intent) {
        // Only the non-exported alias can carry these: MainActivity itself is open to other apps.
        if (intent.component?.className != SHORTCUT_LAUNCHER) return
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val shortcut = AppShortcut.fromAction(intent.action) ?: return
        // Clear the action so a recreation within this process doesn't run it again.
        intent.action = null
        ShortcutManagerCompat.reportShortcutUsed(this, shortcut.id)
        viewModel.onShortcut(shortcut)
    }

    // Some builds (TV, Automotive, some locked-down ones) have nothing that handles it.
    private fun openSafPicker() {
        try {
            safPickerLauncher.launch(arrayOf("*/*"))
        } catch (_: ActivityNotFoundException) {
            viewModel.onFilePickerMissing()
        }
    }

    // No public screen for "Wi-Fi control" alone. The app's own page can be missing too, as on some TVs.
    private fun openAppSettings() {
        val launched = launchFirst(
            listOf(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()),
                Intent(Settings.ACTION_SETTINGS),
            ),
        ) { startActivity(it) }
        if (!launched) viewModel.hotspot.onAppSettingsMissing()
    }

    private fun requestDirectAccessPermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            val launched = launchFirst(directAccessSettingsIntents()) { manageStorageSettingsLauncher.launch(it) }
            // Without either screen the permission can never be granted, so use the system picker.
            if (!launched) openSafPicker()
        } else {
            legacyStoragePermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun handleShareIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        val uris: List<Uri> = readExtras(intent) {
            when (action) {
                Intent.ACTION_SEND -> {
                    val single = if (Build.VERSION.SDK_INT >= 33) {
                        getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        getParcelableExtra(Intent.EXTRA_STREAM)
                    }
                    listOfNotNull(single)
                }
                Intent.ACTION_SEND_MULTIPLE -> {
                    if (Build.VERSION.SDK_INT >= 33) {
                        getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                    } ?: emptyList()
                }
                else -> emptyList()
            }
        }?.filter(::isAcceptableShareUri) ?: return false

        if (uris.isNotEmpty()) {
            viewModel.onSharedFilesReceived(uris)
            // Clear the action so a recreation within this process doesn't re-import the same share.
            intent.action = null
            return true
        }

        // No file stream -- fall back to shared plain text (e.g. a URL shared from a browser).
        if (intent.action == Intent.ACTION_SEND) {
            val text = readExtras(intent) { getStringExtra(Intent.EXTRA_TEXT) }
            if (!text.isNullOrEmpty()) {
                viewModel.onSharedTextReceived(text)
                intent.action = null
                return true
            }
        }
        return false
    }
}

private const val STATE_STARTED_FROM_HOME_SCREEN = "started_from_home_screen"

/** Launches the first of [intents] that something handles; false when nothing does. */
private inline fun launchFirst(intents: List<Intent>, launch: (Intent) -> Unit): Boolean =
    intents.any { intent ->
        try {
            launch(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

/** Another app's extras, or null if they can't be unpacked (an unknown class, before Android 13). */
private inline fun <T> readExtras(intent: Intent, read: Intent.() -> T): T? =
    try {
        intent.read()
    } catch (_: RuntimeException) {
        null
    }

/**
 * Whether a URI handed over by another app is one this app is willing to open. ContentResolver
 * resolves file:// and android.resource:// just as happily as content://, and the StrictMode guard
 * that's meant to stop an app passing out a file:// URI runs in the *sender's* process -- nothing
 * enforces it here. Without this, any app willing to relax its own VM policy could point QRServ at
 * a file it can't read itself and have it copied out and served on the network under QRServ's uid.
 */
private fun isAcceptableShareUri(uri: Uri): Boolean =
    uri.scheme.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true)
