package com.bond.mail.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bond.mail.data.model.MailProvider

/** Consistent service icons for the account picker and account setup header. */
@Composable
fun ProviderAvatar(
    provider: MailProvider,
    size: Dp = 48.dp,
) {
    if (provider.id == "custom") ProviderIconCircle(
        size = size,
        background = MaterialTheme.colorScheme.tertiaryContainer,
        foreground = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(size * 0.52f))
    } else BrandAvatar(
        senderName = "",
        senderAddress = provider.avatarAddress(),
        size = size,
    )
}

// Brand and asset lookup extract the domain from a mailbox address, not a bare domain.
internal fun MailProvider.avatarAddress(): String =
    (if (id == "m365") "outlook.com" else suffixes.firstOrNull())?.let { "@$it" }.orEmpty()

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
