package dev.uint.qrserv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.uint.qrserv.R
import dev.uint.qrserv.data.HotspotDialog
import dev.uint.qrserv.ui.theme.ReducedDialogScrim
import dev.uint.qrserv.ui.theme.subtleContainerColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotspotPermissionDialog(
    dialog: HotspotDialog,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        ReducedDialogScrim()
        Surface(
            shape = AlertDialogDefaults.shape,
            color = subtleContainerColor(),
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(stringResource(R.string.hotspot_dialog_title), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(
                        if (dialog == HotspotDialog.WIFI_CONTROL_SETTINGS) {
                            R.string.hotspot_dialog_wificontrol
                        } else {
                            R.string.hotspot_dialog_nearby
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End),
                ) {
                    val buttonPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    TextButton(onClick = onDismiss, contentPadding = buttonPadding) {
                        Text(stringResource(R.string.hotspot_dialog_notnow))
                    }
                    if (dialog == HotspotDialog.EXPLAIN_NEARBY) {
                        TextButton(onClick = onContinue, contentPadding = buttonPadding) {
                            Text(stringResource(R.string.hotspot_dialog_continue))
                        }
                    } else {
                        TextButton(onClick = onOpenSettings, contentPadding = buttonPadding) {
                            Text(stringResource(R.string.hotspot_dialog_settings))
                        }
                    }
                }
            }
        }
    }
}
