package com.bond.mail.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bond.mail.data.model.MailProvider

/** Consistent service icons for the account picker and account setup header. */
@Composable
fun ProviderAvatar(
    provider: MailProvider,
    size: Dp = 48.dp,
) {
    when (provider.id) {
        "qq", "gmail", "icloud" -> BrandAvatar(
            senderName = provider.label,
            senderAddress = provider.suffixes.firstOrNull().orEmpty(),
            size = size,
            monet = false,
        )

        "outlook", "m365" -> OutlookProviderAvatar(size)
        "163", "126" -> NetEaseProviderAvatar(provider.id, size)
        "custom" -> ProviderIconCircle(
            size = size,
            background = MaterialTheme.colorScheme.tertiaryContainer,
            foreground = MaterialTheme.colorScheme.onTertiaryContainer,
        ) {
            Icon(
                Icons.Outlined.Tune,
                contentDescription = null,
                modifier = Modifier.size(size * 0.52f),
            )
        }

        else -> BrandAvatar(
            senderName = provider.label,
            senderAddress = provider.suffixes.firstOrNull().orEmpty(),
            size = size,
            monet = false,
        )
    }
}

@Composable
private fun NetEaseProviderAvatar(label: String, size: Dp) {
    ProviderIconCircle(
        size = size,
        background = Color(0xFFD63B35),
        foreground = Color.White,
    ) {
        Icon(
            Icons.Outlined.MailOutline,
            contentDescription = null,
            modifier = Modifier.size(size * 0.58f),
        )
        Text(
            text = label,
            color = Color.White,
            fontSize = 8.sp,
            lineHeight = 8.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun OutlookProviderAvatar(size: Dp) {
    ProviderIconCircle(
        size = size,
        background = Color(0xFF0A64AD),
        foreground = Color.White,
    ) {
        Canvas(Modifier.size(size * 0.58f)) {
            val stroke = this.size.minDimension * 0.085f
            val envelopeTop = this.size.height * 0.30f
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(this.size.width * 0.18f, envelopeTop),
                size = Size(this.size.width * 0.70f, this.size.height * 0.52f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(stroke, stroke),
                style = Stroke(width = stroke),
            )
            drawLine(
                color = Color.White,
                start = Offset(this.size.width * 0.21f, envelopeTop + stroke),
                end = Offset(this.size.width * 0.53f, this.size.height * 0.58f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color.White,
                start = Offset(this.size.width * 0.85f, envelopeTop + stroke),
                end = Offset(this.size.width * 0.53f, this.size.height * 0.58f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawRect(
                color = Color(0xFF0A64AD),
                topLeft = Offset(0f, this.size.height * 0.18f),
                size = Size(this.size.width * 0.40f, this.size.height * 0.64f),
            )
            drawCircle(
                color = Color.White,
                radius = this.size.width * 0.10f,
                center = Offset(this.size.width * 0.20f, this.size.height * 0.50f),
                style = Stroke(width = stroke),
            )
        }
    }
}

@Composable
private fun ProviderIconCircle(
    size: Dp,
    background: Color,
    foreground: Color,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides foreground,
        ) {
            content()
        }
    }
}
