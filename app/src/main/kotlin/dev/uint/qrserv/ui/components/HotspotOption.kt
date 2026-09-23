package dev.uint.qrserv.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiLock
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.uint.qrserv.R
import dev.uint.qrserv.net.HotspotAvailability
import dev.uint.qrserv.net.HotspotUnavailable

@Composable
fun hotspotNote(availability: HotspotAvailability): String? = when (availability.unavailable) {
    HotspotUnavailable.REQUIRES_ANDROID_13 -> stringResource(R.string.hotspot_unavailable_android)
    HotspotUnavailable.NO_WIFI -> stringResource(R.string.hotspot_unavailable_nowifi)
    HotspotUnavailable.BLOCKED -> stringResource(R.string.hotspot_unavailable_blocked)
    null -> if (availability.disconnectsWifi) stringResource(R.string.hotspot_note_disconnects) else null
}

@Composable
fun ColumnScope.HotspotOption(availability: HotspotAvailability, onClick: () -> Unit) {
    HotspotButton(
        icon = Icons.Filled.WifiLock,
        text = stringResource(R.string.hotspot_share_button),
        enabled = availability.unavailable == null,
        onClick = onClick,
    )
    val note = hotspotNote(availability)
    if (note != null) {
        Spacer(Modifier.size(8.dp))
        Text(
            text = note,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun HotspotButton(icon: ImageVector, text: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, textAlign = TextAlign.Center)
    }
}
