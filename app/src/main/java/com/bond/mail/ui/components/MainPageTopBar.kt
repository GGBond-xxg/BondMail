package com.bond.mail.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bond.mail.data.settings.UiStyle
import com.bond.mail.ui.i18n.tr
import com.bond.mail.ui.motion.*
import com.bond.mail.ui.theme.BondIcon as Icon
import com.bond.mail.ui.theme.LocalUiStyle
import com.bond.mail.ui.theme.bondSurfaces

/** Mail and contacts share action geometry, press feedback and the search origin. */
@Composable
internal fun MainPageTopBar(
    title: String,
    onOpenDrawer: () -> Unit,
    onSearch: () -> Unit,
    searchLabel: String,
    searchOverlayActive: Boolean,
    onSearchBoundsChanged: (Rect) -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        TopActionButton(onClick = onOpenDrawer, containerColor = Color.Transparent) {
            Icon(Icons.Default.Menu, contentDescription = tr("open_navigation_drawer"))
        }
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(if (LocalUiStyle.current == UiStyle.LIQUID_GLASS) 48.dp else 44.dp)
                .onGloballyPositioned { onSearchBoundsChanged(it.boundsInRoot()) }, contentAlignment = Alignment.Center) {
                if (!searchOverlayActive) TopActionButton(onClick = onSearch) {
                    Icon(Icons.Default.Search, contentDescription = searchLabel)
                }
            }
            actions()
        }
    }
}

@Composable
internal fun TopActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.bondSurfaces.popup,
    content: @Composable () -> Unit,
) {
    if (LocalUiStyle.current == UiStyle.LIQUID_GLASS) {
        com.bond.mail.ui.theme.GlassIconButton(onClick, modifier, true, content)
        return
    }
    val pressResetter = rememberBondPressResetter()
    key(pressResetter.epoch) {
        val motionEnabled = bondMotionEnabled()
        val interactionSource = rememberBondPressInteraction()
        val pressScale by rememberBondPressScale(
            interactionSource = interactionSource,
            pressedScale = 0.90f,
            enabled = motionEnabled,
        )
        Surface(
            modifier = modifier
                .size(44.dp)
                .bondPressTransform(pressScale),
            shape = CircleShape,
            color = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            tonalElevation = 0.dp,
        ) {
            Box(
                modifier = Modifier.fillMaxSize().clickable(
                    interactionSource = interactionSource,
                    indication = if (LocalUiStyle.current == UiStyle.MIUIX) null else LocalIndication.current,
                    onClick = { pressResetter.resetThen(onClick) },
                ),
                contentAlignment = Alignment.Center,
            ) { content() }
        }
    }
}

