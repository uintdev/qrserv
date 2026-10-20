package dev.uint.qrserv.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.uint.qrserv.BuildConfig
import dev.uint.qrserv.R
import dev.uint.qrserv.data.IdleStopOptions
import dev.uint.qrserv.data.ThemeMode
import dev.uint.qrserv.net.HotspotBand
import dev.uint.qrserv.net.labelRes
import dev.uint.qrserv.ui.components.BackNavigationIcon
import dev.uint.qrserv.ui.components.openLink
import dev.uint.qrserv.ui.theme.ReducedDialogScrim
import dev.uint.qrserv.ui.theme.reducedBottomInsetContentWindowInsets
import dev.uint.qrserv.ui.theme.subtleContainerColor
import dev.uint.qrserv.ui.theme.transparentTopAppBarColors
import dev.uint.qrserv.util.ManifestUtils
import dev.uint.qrserv.util.RELEASES_URL
import dev.uint.qrserv.util.UpdateCheckResult
import dev.uint.qrserv.util.UpdateCheckState
import dev.uint.qrserv.viewmodel.QRServViewModel
import dev.uint.qrserv.viewmodel.UiEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: QRServViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    var showPortDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showBandDialog by remember { mutableStateOf(false) }
    var showIdleDialog by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    val updateCheck by viewModel.updateCheck.collectAsState()
    val context = LocalContext.current
    // The GitHub version only, told apart from the Play version the same way About does.
    val canCheckForUpdates = remember { ManifestUtils.hasManageExternalStorageInManifest(context) }

    DisposableEffect(viewModel) {
        onDispose { viewModel.cancelUpdateCheck() }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is UiEvent.PortSaved) showPortDialog = false
        }
    }

    val groupItemColors = ListItemDefaults.colors(containerColor = Color.Transparent)
    val disabledItemColors = ListItemDefaults.colors(
        containerColor = Color.Transparent,
        headlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        supportingColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
    )

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = reducedBottomInsetContentWindowInsets(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { BackNavigationIcon(onClick = onBack) },
                colors = transparentTopAppBarColors(),
                modifier = Modifier.padding(top = 4.dp),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(bottom = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(stringResource(R.string.settings_subheading_server))
            SettingsGroup {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_server_port_list_title)) },
                    supportingContent = { Text(stringResource(R.string.settings_server_port_list_subtitle)) },
                    colors = groupItemColors,
                    modifier = Modifier.clickable { showPortDialog = true },
                )
                GroupDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_server_allinterfaces_list_title)) },
                    supportingContent = { Text(stringResource(R.string.settings_server_allinterfaces_list_subtitle)) },
                    trailingContent = {
                        Switch(
                            checked = uiState.allInterfacesEnabled,
                            onCheckedChange = { viewModel.toggleAllInterfaces() },
                        )
                    },
                    colors = groupItemColors,
                    modifier = Modifier.clickable { viewModel.toggleAllInterfaces() },
                )
                GroupDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_server_idle_list_title)) },
                    supportingContent = {
                        Text(
                            if (uiState.idleStopMinutes <= 0) {
                                stringResource(R.string.settings_server_idle_off)
                            } else {
                                pluralStringResource(R.plurals.settings_server_idle_subtitle, uiState.idleStopMinutes, uiState.idleStopMinutes)
                            },
                        )
                    },
                    colors = groupItemColors,
                    modifier = Modifier.clickable { showIdleDialog = true },
                )
            }

            // Empty before 36, or without 5 GHz: the hotspot then always uses the system default. It's still
            // shown, disabled, on Android 13 to 15 where 5 GHz exists, since an update would unlock it.
            val bandOptions = uiState.hotspotBandOptions
            val bandEnabled = bandOptions.isNotEmpty()
            // Not where the hotspot can't be used at all, e.g. blocked by a device policy.
            val hotspotUsable = uiState.hotspotAvailability.unavailable == null
            if (hotspotUsable && (bandEnabled || uiState.hotspotBandNeedsNewerAndroid)) {
                SectionHeader(stringResource(R.string.hotspot_screen_title))
                SettingsGroup {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_hotspot_band_list_title)) },
                        supportingContent = {
                            Text(
                                if (bandEnabled) {
                                    stringResource((uiState.hotspotBand ?: bandOptions.first()).labelRes)
                                } else {
                                    stringResource(R.string.settings_hotspot_band_needsandroid)
                                },
                            )
                        },
                        colors = if (bandEnabled) groupItemColors else disabledItemColors,
                        modifier = Modifier.clickable(enabled = bandEnabled) { showBandDialog = true },
                    )
                }
            }

            SectionHeader(stringResource(R.string.settings_subheading_client))
            SettingsGroup {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_client_dam_list_title)) },
                    supportingContent = {
                        Text(
                            if (uiState.damBuildIneligible) {
                                stringResource(
                                    R.string.settings_client_dam_subtitle_combined,
                                    stringResource(R.string.settings_client_dam_list_subtitle),
                                    stringResource(R.string.settings_client_dam_ineligiblebuild),
                                )
                            } else {
                                stringResource(R.string.settings_client_dam_list_subtitle)
                            },
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = uiState.damEnabled,
                            onCheckedChange = { viewModel.toggleDam() },
                            enabled = !uiState.damBuildIneligible,
                        )
                    },
                    colors = if (uiState.damBuildIneligible) {
                        disabledItemColors
                    } else {
                        groupItemColors
                    },
                    modifier = Modifier.clickable(enabled = !uiState.damBuildIneligible) { viewModel.toggleDam() },
                )
                GroupDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_client_fiu_list_title)) },
                    supportingContent = { Text(stringResource(R.string.settings_client_fiu_list_subtitle)) },
                    trailingContent = {
                        Switch(
                            checked = uiState.fiuEnabled,
                            onCheckedChange = { viewModel.toggleFiu() },
                        )
                    },
                    colors = groupItemColors,
                    modifier = Modifier.clickable { viewModel.toggleFiu() },
                )
            }

            SectionHeader(stringResource(R.string.settings_subheading_general))
            SettingsGroup {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_general_theme_list_title)) },
                    supportingContent = { Text(themeModeLabel(uiState.themeMode)) },
                    colors = groupItemColors,
                    modifier = Modifier.clickable { showThemeDialog = true },
                )
                GroupDivider()
                if (canCheckForUpdates) {
                    val checking = updateCheck == UpdateCheckState.Checking
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_update_check_title)) },
                        supportingContent = { Text(stringResource(R.string.settings_update_version, BuildConfig.VERSION_NAME)) },
                        // Always there, so the spinner showing up doesn't shift the text; the result is a dialog.
                        trailingContent = {
                            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                if (checking) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        },
                        colors = groupItemColors,
                        modifier = Modifier.clickable(enabled = !checking) { viewModel.checkForUpdates() },
                    )
                    GroupDivider()
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_general_defaults_list_title)) },
                    colors = groupItemColors,
                    modifier = Modifier.clickable { showRestoreConfirm = true },
                )
            }
        }
    }

    if (showPortDialog) {
        PortDialog(
            currentPort = uiState.savedPort,
            serverRunning = uiState.serverRunning,
            onDismiss = { showPortDialog = false },
            onSave = { port -> viewModel.trySavePort(port) },
        )
    }

    if (showThemeDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_general_theme_list_title),
            options = ThemeMode.entries,
            current = uiState.themeMode,
            label = { themeModeLabel(it) },
            onDismiss = { showThemeDialog = false },
            onSelect = { mode -> viewModel.setThemeMode(mode) },
        )
    }

    if (showBandDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_hotspot_band_list_title),
            options = uiState.hotspotBandOptions,
            current = uiState.hotspotBand ?: uiState.hotspotBandOptions.firstOrNull(),
            label = { stringResource(it.labelRes) },
            description = { stringResource(bandDescriptionRes(it)) },
            onDismiss = { showBandDialog = false },
            onSelect = { band -> viewModel.hotspot.setHotspotBand(band) },
        )
    }

    if (showIdleDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_server_idle_list_title),
            options = IdleStopOptions,
            current = uiState.idleStopMinutes,
            label = { minutes ->
                if (minutes <= 0) {
                    stringResource(R.string.settings_server_idle_off)
                } else {
                    pluralStringResource(R.plurals.settings_server_idle_option, minutes, minutes)
                }
            },
            onDismiss = { showIdleDialog = false },
            onSelect = { minutes -> viewModel.setIdleStopMinutes(minutes) },
        )
    }

    (updateCheck as? UpdateCheckState.Done)?.let { done ->
        UpdateResultDialog(
            result = done.result,
            onOpenLink = { url ->
                openLink(context, url)
                viewModel.cancelUpdateCheck()
            },
            onDismiss = viewModel::cancelUpdateCheck,
        )
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            containerColor = subtleContainerColor(),
            title = {
                ReducedDialogScrim()
                Text(stringResource(R.string.settings_general_defaults_list_title))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.restoreDefaults()
                    showRestoreConfirm = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun UpdateResultDialog(result: UpdateCheckResult, onOpenLink: (String) -> Unit, onDismiss: () -> Unit) {
    val message = when (result) {
        is UpdateCheckResult.UpdateAvailable -> stringResource(R.string.settings_update_available, result.version)
        UpdateCheckResult.UpToDate -> stringResource(R.string.settings_update_latest)
        is UpdateCheckResult.NewerThanLatest -> stringResource(R.string.settings_update_newer, result.latestVersion)
        UpdateCheckResult.Failed -> stringResource(R.string.settings_update_failed)
    }
    val link = when (result) {
        is UpdateCheckResult.UpdateAvailable -> R.string.settings_update_view_release to result.releaseUrl
        UpdateCheckResult.Failed -> R.string.settings_update_open_releases to RELEASES_URL
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = subtleContainerColor(),
        title = {
            ReducedDialogScrim()
            Text(stringResource(R.string.settings_update_dialog_title))
        },
        text = { Text(message) },
        confirmButton = {
            if (link != null) {
                TextButton(onClick = { onOpenLink(link.second) }) { Text(stringResource(link.first)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
            }
        },
        dismissButton = link?.let {
            { TextButton(onClick = onDismiss) { Text(stringResource(R.string.about_close)) } }
        },
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = subtleContainerColor(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(content = content)
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun PortDialog(
    currentPort: Int,
    serverRunning: Boolean,
    onDismiss: () -> Unit,
    onSave: (Int?) -> Unit,
) {
    var textFieldValue by remember {
        val initial = if (currentPort > 0) currentPort.toString() else ""
        mutableStateOf(TextFieldValue(text = initial, selection = TextRange(0, initial.length)))
    }
    val text = textFieldValue.text
    val portMin = 1024
    val portMax = 65535

    val parsedValue: Int? = text.toIntOrNull()
    val valid = text.isEmpty() || (parsedValue != null && parsedValue in portMin..portMax)

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = subtleContainerColor(),
        title = {
            ReducedDialogScrim()
            Text(stringResource(R.string.settings_server_port_list_title))
        },
        text = {
            Column {
                Text(
                    stringResource(
                        R.string.settings_server_port_dialog_description,
                        portMin.toString(),
                        portMax.toString(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (serverRunning) {
                    Text(
                        stringResource(R.string.settings_server_port_dialog_serveractive),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        val filtered = newValue.text.filter { c -> c.isDigit() }
                        textFieldValue = newValue.copy(
                            text = filtered,
                            selection = TextRange(
                                newValue.selection.start.coerceIn(0, filtered.length),
                                newValue.selection.end.coerceIn(0, filtered.length),
                            ),
                        )
                    },
                    label = { Text(stringResource(R.string.settings_server_port_list_title)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = !valid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .focusRequester(focusRequester),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = { onSave(if (text.isEmpty()) null else parsedValue) },
            ) { Text(stringResource(R.string.settings_server_port_dialog_submit)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> stringResource(R.string.settings_general_theme_option_system)
    ThemeMode.DARK -> stringResource(R.string.settings_general_theme_option_dark)
    ThemeMode.LIGHT -> stringResource(R.string.settings_general_theme_option_light)
}

private fun bandDescriptionRes(band: HotspotBand): Int = when (band) {
    HotspotBand.DUAL -> R.string.settings_hotspot_band_dual_description
    HotspotBand.FIVE_GHZ -> R.string.settings_hotspot_band_5ghz_description
    HotspotBand.TWO_GHZ -> R.string.settings_hotspot_band_2ghz_description
    // Never offered; see HotspotBand.SIX_GHZ.
    HotspotBand.SIX_GHZ -> R.string.settings_hotspot_band_5ghz_description
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    current: T?,
    label: @Composable (T) -> String,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
    description: (@Composable (T) -> String)? = null,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        ReducedDialogScrim()
        Surface(
            shape = AlertDialogDefaults.shape,
            color = subtleContainerColor(),
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(modifier = Modifier.padding(24.dp).selectableGroup()) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                options.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == current,
                                role = Role.RadioButton,
                                onClick = {
                                    if (option != current) {
                                        onSelect(option)
                                        onDismiss()
                                    }
                                },
                            )
                            .padding(vertical = 10.dp),
                    ) {
                        RadioButton(selected = option == current, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(label(option))
                            if (description != null) {
                                Text(
                                    description(option),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
