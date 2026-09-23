package com.bond.mail.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import com.bond.mail.data.support.SponsorshipWallet
import com.bond.mail.ui.i18n.tr

@Composable
internal fun SponsorshipQr(wallet: SponsorshipWallet) {
    val context = LocalContext.current
    val bitmap = remember(wallet.address) {
        context.assets.open("sponsorship/${wallet.address}.png").use { BitmapFactory.decodeStream(it).asImageBitmap() }
    }
    var standard by remember(wallet.address) { mutableStateOf(false) }
    val surface = MaterialTheme.colorScheme.surface
    val dark = surface.luminance() < 0.5f
    val foreground = if (standard) Color.Black else MaterialTheme.colorScheme.onSurface
    val background = if (standard) Color.White else surface
    val matrix = remember(foreground, background) { qrColorMatrix(foreground, background) }
    Image(bitmap, contentDescription = tr("sponsor_qr") + " · " + wallet.network,
        modifier = Modifier.fillMaxWidth().aspectRatio(1f), filterQuality = FilterQuality.None,
        colorFilter = ColorFilter.colorMatrix(matrix))
    if (dark) TextButton(onClick = { standard = !standard }) {
        Text(tr(if (standard) "sponsor_qr_themed" else "sponsor_qr_standard"))
    }
}

// Map black modules and the white quiet zone together; keep module geometry and alpha intact.
internal fun qrColorMatrix(foreground: Color, background: Color) = ColorMatrix(floatArrayOf(
    background.red - foreground.red, 0f, 0f, 0f, foreground.red * 255f,
    0f, background.green - foreground.green, 0f, 0f, foreground.green * 255f,
    0f, 0f, background.blue - foreground.blue, 0f, foreground.blue * 255f,
    0f, 0f, 0f, 1f, 0f,
))
