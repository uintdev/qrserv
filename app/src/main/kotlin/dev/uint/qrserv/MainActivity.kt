package dev.uint.qrserv

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import dev.uint.qrserv.data.Preferences
import dev.uint.qrserv.data.ThemeMode
import dev.uint.qrserv.data.readPersistedThemeMode
import dev.uint.qrserv.ui.QRServApp
import dev.uint.qrserv.ui.theme.QRServTheme
import dev.uint.qrserv.ui.theme.resolveIsDark
import dev.uint.qrserv.viewmodel.QRServViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: QRServViewModel by viewModels()

    private val safPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            viewModel.onFilesPicked(uris)
        }

    private val legacyStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onDirectAccessPermissionResult(granted)
        }

    private val manageStorageSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                viewModel.onDirectAccessPermissionResult(Environment.isExternalStorageManager())
            }
        }

    // Runs before the window/theme is created, so the cold-start windowBackground (themes.xml's
    // light/dark split) matches the in-app theme preference instead of the system setting --
    // otherwise a Dark-mode user on a light system would see a light flash before Compose corrects it.
    override fun attachBaseContext(newBase: Context) {
        Preferences.init(newBase)
        val themeMode = readPersistedThemeMode()
        val configOverride = Configuration(newBase.resources.configuration)
        when (themeMode) {
            ThemeMode.DARK -> configOverride.uiMode =
                (configOverride.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            ThemeMode.LIGHT -> configOverride.uiMode =
                (configOverride.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
            ThemeMode.SYSTEM -> Unit
        }
        super.attachBaseContext(newBase.createConfigurationContext(configOverride))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleShareIntent(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            // Not isSystemInDarkTheme() for the SYSTEM case -- that reads LocalConfiguration, which
            // attachBaseContext's override permanently forced one way if the stored preference was
            // DARK/LIGHT at launch. resolveIsDark() checks the real device setting instead, so
            // switching back to System mid-session takes effect immediately.
            val darkTheme = uiState.themeMode.resolveIsDark()
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
                    onOpenSafPicker = { safPickerLauncher.launch(arrayOf("*/*")) },
                    onRequestDamPermission = ::requestDirectAccessPermission,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun requestDirectAccessPermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                "package:$packageName".toUri(),
            )
            manageStorageSettingsLauncher.launch(intent)
        } else {
            legacyStoragePermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> {
                val single = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                listOfNotNull(single)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                } ?: emptyList()
            }
            else -> emptyList()
        }
        if (uris.isNotEmpty()) {
            viewModel.onSharedFilesReceived(uris)
            // Clear the action so rotation / process restarts don't re-import the same share.
            intent.action = null
            return
        }

        // No file stream -- fall back to shared plain text (e.g. a URL shared from a browser).
        if (intent.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrEmpty()) {
                viewModel.onSharedTextReceived(text)
                intent.action = null
            }
        }
    }
}
