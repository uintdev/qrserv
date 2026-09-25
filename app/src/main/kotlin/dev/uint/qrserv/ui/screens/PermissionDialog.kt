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
import dev.uint.qrserv.data.CompatibleBandWarning
import dev.uint.qrserv.ui.theme.ReducedDialogScrim
import dev.uint.qrserv.ui.theme.subtleContainerColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    dismissLabel: String = stringResource(R.string.permission_dialog_notnow),
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        ReducedDialogScrim()
        Surface(
            shape = AlertDialogDefaults.shape,
            color = subtleContainerColor(),
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End),
                ) {
                    val buttonPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    TextButton(onClick = onDismiss, contentPadding = buttonPadding) {
                        Text(dismissLabel)
                    }
                    TextButton(onClick = onConfirm, contentPadding = buttonPadding) {
                        Text(confirmLabel)
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationPermissionDialog(onDismiss: () -> Unit, onContinue: () -> Unit) {
    PermissionDialog(
        title = stringResource(R.string.notification_dialog_title),
        text = stringResource(R.string.notification_dialog_body),
        confirmLabel = stringResource(R.string.permission_dialog_continue),
        onDismiss = onDismiss,
        onConfirm = onContinue,
    )
}

@Composable
fun CompatibleBandWarningDialog(
    warning: CompatibleBandWarning,
    /** The band the hotspot will restart on, when it can't be switched; null when it can. */
    sameBand: String?,
    faster: Boolean,
    fasterDropsTwoGhz: Boolean,
    onDismiss: () -> Unit,
    onRestart: () -> Unit,
) {
    val warningText = stringResource(
        when (warning) {
            CompatibleBandWarning.DOWNLOAD_ACTIVE -> R.string.hotspot_restart_downloading
            CompatibleBandWarning.CLIENT_SEEN -> R.string.hotspot_restart_connected
            CompatibleBandWarning.NONE_SEEN -> R.string.hotspot_restart_fallback
        },
    )
    val note = when {
        faster && fasterDropsTwoGhz -> stringResource(R.string.hotspot_restart_faster_note)
        faster -> null
        sameBand != null -> stringResource(R.string.hotspot_restart_sameband_note, sameBand)
        else -> null
    }
    PermissionDialog(
        title = stringResource(
            when {
                faster -> R.string.hotspot_restart_title_faster
                sameBand != null -> R.string.hotspot_restart_title_sameband
                else -> R.string.hotspot_restart_title
            },
        ),
        text = if (note != null) warningText + "\n\n" + note else warningText,
        confirmLabel = stringResource(R.string.hotspot_restart_confirm),
        onDismiss = onDismiss,
        onConfirm = onRestart,
        dismissLabel = stringResource(android.R.string.cancel),
    )
}
