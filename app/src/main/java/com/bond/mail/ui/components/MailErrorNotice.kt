package com.bond.mail.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bond.mail.ui.i18n.tr
import com.bond.mail.ui.theme.BondIcon
import com.bond.mail.ui.theme.BondIconButton
import com.bond.mail.data.settings.UiStyle
import com.bond.mail.ui.theme.LocalUiStyle
import com.bond.mail.ui.theme.LocalGlassBackdrop
import com.bond.mail.ui.theme.glassSurface
import com.kyant.backdrop.Backdrop

/** Match the active theme's floating surfaces; reserve error color for the status icon. */
@Composable
internal fun MailErrorNotice(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
) {
    val shape = MaterialTheme.shapes.extraLarge
    // This overlay is inside the activity window: sample only the list, never the whole window.
    Surface(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }
            .glassSurface(backdrop, shape, prominent = true),
        shape = shape,
        color = if (LocalUiStyle.current == UiStyle.LIQUID_GLASS) Color.Transparent else MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        CompositionLocalProvider(LocalGlassBackdrop provides null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BondIcon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp),
                )
                Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                BondIconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    BondIcon(Icons.Default.Close, contentDescription = tr("close"), modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}
