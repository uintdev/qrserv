package dev.uint.qrserv.ui.screens

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PortableWifiOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.uint.qrserv.R
import dev.uint.qrserv.net.HotspotInfo
import dev.uint.qrserv.net.WifiQr
import dev.uint.qrserv.ui.components.BackNavigationIcon
import dev.uint.qrserv.ui.components.MiddleEllipsisText
import dev.uint.qrserv.ui.components.QrCodeImage
import dev.uint.qrserv.ui.components.QrDetailsLayout
import dev.uint.qrserv.ui.components.isShortWindow
import dev.uint.qrserv.ui.components.rememberIsWideScreen
import dev.uint.qrserv.ui.theme.reducedBottomInsetContentWindowInsets
import dev.uint.qrserv.ui.theme.subtleContainerColor
import dev.uint.qrserv.ui.theme.transparentTopAppBarColors
import dev.uint.qrserv.viewmodel.QRServViewModel

private val QrSize = 176.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotspotScreen(
    viewModel: QRServViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    // QRServApp closes this screen on the same change.
    val info = uiState.hotspot ?: return

    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val isWideScreen = rememberIsWideScreen()
    val compact = isShortWindow()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = reducedBottomInsetContentWindowInsets(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.hotspot_screen_title)) },
                navigationIcon = { BackNavigationIcon(onClick = onBack) },
                expandedHeight = if (isWideScreen) 40.dp else 48.dp,
                colors = transparentTopAppBarColors(),
                modifier = Modifier.padding(top = 4.dp),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            QrDetailsLayout(
                isWideScreen = isWideScreen,
                wideSideClearance = 16.dp,
                bottomClearance = 0.dp,
                gap = if (isWideScreen) 36.dp else 24.dp,
                qr = { JoinQr(info) },
                details = {
                    HotspotDetailsCard(
                        info = info,
                        rowGap = if (compact) 8.dp else 16.dp,
                        onStop = viewModel::onStopHotspotClicked,
                    )
                },
            )
        }
    }
}

@Composable
private fun JoinQr(info: HotspotInfo) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            elevation = CardDefaults.cardElevation(1.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        ) {
            QrCodeImage(data = WifiQr.payload(info.ssid, info.passphrase, info.security), size = QrSize)
        }
        Spacer(Modifier.size(16.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = QrSize + 48.dp)) {
            Text(
                stringResource(R.string.hotspot_scan_hint),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.hotspot_forget_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun HotspotDetailsCard(info: HotspotInfo, rowGap: Dp, onStop: () -> Unit) {
    val context = LocalContext.current
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(1.dp),
        colors = CardDefaults.cardColors(containerColor = subtleContainerColor()),
        modifier = Modifier.widthIn(max = 320.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = if (rowGap < 16.dp) 16.dp else 24.dp)) {
            FieldLabel(stringResource(R.string.hotspot_network))
            CopyableField(
                copyDescription = stringResource(R.string.hotspot_copy_network),
                onCopy = { copyToClipboard(context, info.ssid, sensitive = false) },
            ) {
                MiddleEllipsisText(text = info.ssid, fontSize = 14.sp, textAlign = TextAlign.Start)
            }

            if (info.security != dev.uint.qrserv.net.HotspotSecurity.OPEN) {
                Spacer(Modifier.size(rowGap))
                FieldLabel(stringResource(R.string.hotspot_password))
                CopyableField(
                    copyDescription = stringResource(R.string.hotspot_copy_password),
                    onCopy = { copyToClipboard(context, info.passphrase, sensitive = true) },
                    onFieldClick = { passwordVisible = !passwordVisible },
                    trailing = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = stringResource(
                                    if (passwordVisible) R.string.hotspot_password_hide else R.string.hotspot_password_show,
                                ),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                ) {
                    val shown = if (passwordVisible) WifiQr.groupForDisplay(info.passphrase) else "•".repeat(info.passphrase.length)
                    Text(
                        text = shown,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        maxLines = 1,
                        modifier = Modifier.semantics {
                            contentDescription = if (passwordVisible) info.passphrase.toList().joinToString(" ") else ""
                        },
                    )
                }
            }

            Spacer(Modifier.size(rowGap))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.hotspot_security), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(info.securityName ?: stringResource(R.string.hotspot_security_open), style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.size(rowGap))
            OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.PortableWifiOff, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.hotspot_stop), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun CopyableField(
    copyDescription: String,
    onCopy: () -> Unit,
    onFieldClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Card(
            shape = RoundedCornerShape(10.dp),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = FieldHeight)
                    .then(if (onFieldClick != null) Modifier.clickable(onClick = onFieldClick) else Modifier)
                    .padding(start = 14.dp, end = if (trailing != null) 0.dp else 14.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) { content() }
                trailing?.invoke()
            }
        }
        Spacer(Modifier.size(12.dp))
        Card(
            shape = RoundedCornerShape(10.dp),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.size(FieldHeight),
        ) {
            IconButton(onClick = onCopy, modifier = Modifier.size(FieldHeight)) {
                Icon(Icons.Filled.ContentCopy, contentDescription = copyDescription, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private val FieldHeight = 48.dp

private fun copyToClipboard(context: Context, text: String, sensitive: Boolean) {
    val clip = ClipData.newPlainText(null, text)
    if (sensitive) {
        // Hides the password from the clipboard preview; ignored before Android 13.
        clip.description.extras = PersistableBundle().apply {
            putBoolean(
                if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE",
                true,
            )
        }
    }
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
    Toast.makeText(context, context.getString(R.string.page_imported_share_clipboard), Toast.LENGTH_SHORT).show()
}
