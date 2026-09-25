package dev.uint.qrserv.ui.screens

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import dev.uint.qrserv.net.HotspotBand
import dev.uint.qrserv.net.HotspotInfo
import dev.uint.qrserv.net.labelRes
import dev.uint.qrserv.net.WifiQr
import dev.uint.qrserv.ui.components.BackNavigationIcon
import dev.uint.qrserv.ui.components.DetailsCardMaxWidth
import dev.uint.qrserv.ui.components.DetailsFieldHeight
import dev.uint.qrserv.ui.components.FieldCard
import dev.uint.qrserv.ui.components.InfoRow
import dev.uint.qrserv.ui.components.KeepScreenOn
import dev.uint.qrserv.ui.components.MiddleEllipsisText
import dev.uint.qrserv.ui.components.QrCodeImage
import dev.uint.qrserv.ui.components.QrDetailsLayout
import dev.uint.qrserv.ui.components.copyToClipboard
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

    KeepScreenOn()

    uiState.compatibleBandWarning?.let { warning ->
        CompatibleBandWarningDialog(
            warning = warning,
            // Before 36 the band can't be requested, so the restart comes back on the same one.
            sameBand = stringResource(info.band.labelRes).takeIf { Build.VERSION.SDK_INT < 36 },
            faster = uiState.hotspotRestartFaster,
            // A dual-band hotspot still carries 2.4 GHz.
            fasterDropsTwoGhz = info.fasterBand == HotspotBand.FIVE_GHZ,
            onDismiss = viewModel::onCompatibleBandWarningDismissed,
            onRestart = viewModel::onCompatibleBandWarningConfirmed,
        )
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
            val bandLink = when {
                info.band != HotspotBand.TWO_GHZ ->
                    BandLink(stringResource(R.string.hotspot_compatible_link), viewModel::onUseCompatibleBandClicked)
                info.fasterBand != null ->
                    BandLink(stringResource(R.string.hotspot_faster_link), viewModel::onUseFasterBandClicked)
                else -> null
            }
            QrDetailsLayout(
                isWideScreen = isWideScreen,
                wideSideClearance = 16.dp,
                bottomClearance = 0.dp,
                // The link's touch target already pads it below.
                gap = when {
                    isWideScreen -> 36.dp
                    bandLink != null -> 8.dp
                    else -> 24.dp
                },
                qr = {
                    JoinQr(
                        info = info,
                        bandLink = bandLink,
                    )
                },
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
private fun JoinQr(info: HotspotInfo, bandLink: BandLink?) {
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
                stringResource(R.string.hotspot_forget_older_hint, ssidPrefix(info.ssid)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (bandLink != null) {
                TextButton(onClick = bandLink.onClick) {
                    Text(bandLink.label, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// 5 GHz-only hotspots are invisible to 2.4 GHz-only clients, and a dual-band one may drop its idle
// 2.4 GHz half without telling apps.
private class BandLink(val label: String, val onClick: () -> Unit)

// A client that still has a previous session's network saved may fail to join the new one (seen on iOS).
private fun ssidPrefix(ssid: String): String {
    val prefix = ssid.substringBeforeLast('_', "")
    val suffix = ssid.substringAfterLast('_')
    return if (prefix.isNotEmpty() && suffix.isNotEmpty() && suffix.all(Char::isDigit)) prefix else ssid
}

@Composable
private fun HotspotDetailsCard(info: HotspotInfo, rowGap: Dp, onStop: () -> Unit) {
    val context = LocalContext.current
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(1.dp),
        colors = CardDefaults.cardColors(containerColor = subtleContainerColor()),
        modifier = Modifier.widthIn(max = DetailsCardMaxWidth),
    ) {
        // Short landscape windows need every row of the card to fit without scrolling.
        val compact = rowGap < 16.dp
        // The stop button's touch target already pads below its outline.
        Column(
            modifier = Modifier.padding(
                start = 24.dp,
                end = 24.dp,
                top = if (compact) 10.dp else 24.dp,
                bottom = if (compact) 6.dp else 24.dp,
            ),
        ) {
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
            // Tighter lines in compact windows keep the card clear of the bottom edge.
            val infoRowStyle = MaterialTheme.typography.bodyMedium.let { if (compact) it.copy(lineHeight = 16.sp) else it }
            InfoRow(
                stringResource(R.string.hotspot_security),
                info.securityName ?: stringResource(R.string.hotspot_security_open),
                style = infoRowStyle,
            )
            Spacer(Modifier.size(if (compact) 0.dp else 4.dp))
            InfoRow(
                stringResource(R.string.hotspot_band),
                stringResource(info.band.labelRes),
                style = infoRowStyle,
            )

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
        FieldCard(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = DetailsFieldHeight)
                    .then(if (onFieldClick != null) Modifier.clickable(onClick = onFieldClick) else Modifier)
                    .padding(start = 14.dp, end = if (trailing != null) 0.dp else 14.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) { content() }
                trailing?.invoke()
            }
        }
        Spacer(Modifier.size(12.dp))
        FieldCard(modifier = Modifier.size(DetailsFieldHeight)) {
            IconButton(onClick = onCopy, modifier = Modifier.size(DetailsFieldHeight)) {
                Icon(Icons.Filled.ContentCopy, contentDescription = copyDescription, modifier = Modifier.size(18.dp))
            }
        }
    }
}
