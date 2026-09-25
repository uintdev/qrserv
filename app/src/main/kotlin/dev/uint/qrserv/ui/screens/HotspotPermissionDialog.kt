package dev.uint.qrserv.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.uint.qrserv.R
import dev.uint.qrserv.data.HotspotDialog

@Composable
fun HotspotPermissionDialog(
    dialog: HotspotDialog,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val explain = dialog == HotspotDialog.EXPLAIN_NEARBY
    PermissionDialog(
        title = stringResource(R.string.hotspot_dialog_title),
        text = stringResource(
            if (dialog == HotspotDialog.WIFI_CONTROL_SETTINGS) {
                R.string.hotspot_dialog_wificontrol
            } else {
                R.string.hotspot_dialog_nearby
            },
        ),
        confirmLabel = stringResource(if (explain) R.string.permission_dialog_continue else R.string.hotspot_dialog_settings),
        onDismiss = onDismiss,
        onConfirm = if (explain) onContinue else onOpenSettings,
    )
}
