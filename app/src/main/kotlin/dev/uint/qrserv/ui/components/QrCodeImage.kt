package dev.uint.qrserv.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import androidx.core.graphics.createBitmap

private fun generateQrBitmap(data: String): Bitmap {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 0,
    )
    val matrix = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, 0, 0, hints)
    val modules = matrix.width
    val pixels = IntArray(modules * modules) { i ->
        if (matrix[i % modules, i / modules]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return createBitmap(modules, modules, Bitmap.Config.RGB_565).apply {
        setPixels(pixels, 0, modules, 0, 0, modules, modules)
    }
}

@Composable
fun QrCodeImage(data: String, size: Dp, modifier: Modifier = Modifier) {
    val bitmap = remember(data) { generateQrBitmap(data).asImageBitmap() }

    Image(
        bitmap = bitmap,
        contentDescription = null,
        filterQuality = FilterQuality.None,
        modifier = modifier
            .size(size)
            .background(Color.White)
            .padding(20.dp),
    )
}
