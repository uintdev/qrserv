package dev.uint.qrserv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun QrDetailsLayout(
    isWideScreen: Boolean,
    wideSideClearance: Dp,
    bottomClearance: Dp,
    gap: Dp,
    qr: @Composable () -> Unit,
    details: @Composable () -> Unit,
) {
    val scrollState = rememberScrollState()
    if (isWideScreen) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(vertical = 8.dp)
                .padding(horizontal = wideSideClearance),
        ) {
            qr()
            details()
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp)
                .padding(bottom = bottomClearance),
        ) {
            qr()
            Spacer(Modifier.size(gap))
            details()
        }
    }
}
