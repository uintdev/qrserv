package dev.uint.qrserv.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.uint.qrserv.BuildConfig
import dev.uint.qrserv.R
import dev.uint.qrserv.ui.theme.ReducedDialogScrim
import dev.uint.qrserv.ui.theme.subtleContainerColor
import dev.uint.qrserv.util.ManifestUtils
import androidx.core.net.toUri
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    BasicAlertDialog(onDismissRequest = onDismiss) {
        ReducedDialogScrim()
        Surface(
            shape = AlertDialogDefaults.shape,
            color = subtleContainerColor(),
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(stringResource(R.string.about_title), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                Text(
                    "${stringResource(R.string.app_name)} v${BuildConfig.VERSION_NAME} " +
                        "(build ${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    buildString {
                        append(if (BuildConfig.DEBUG) "Debug" else "Release")
                        append(", ")
                        append(
                            if (ManifestUtils.hasManageExternalStorageInManifest(context)) {
                                "GitHub"
                            } else {
                                "Google Play"
                            },
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    LinkRow(Icons.Filled.Archive, stringResource(R.string.about_releases_title), "https://github.com/uintdev/qrserv/releases")
                    LinkRow(Icons.Filled.Code, stringResource(R.string.about_opensource_title), "https://github.com/uintdev/qrserv")
                    LinkRow(Icons.Filled.LocalCafe, stringResource(R.string.about_donate_title), "https://ko-fi.com/uintdev")
                }

                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(stringResource(R.string.about_close))
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkRow(icon: ImageVector, label: String, url: String) {
    val context = LocalContext.current
    val linkOpenFailedMessage = stringResource(R.string.info_exception_linkopenfailed)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                    .onFailure {
                        Toast.makeText(context, linkOpenFailedMessage, Toast.LENGTH_SHORT).show()
                    }
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
