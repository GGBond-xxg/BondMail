package com.bond.mail.ui.theme

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bond.mail.data.settings.UiStyle
import com.bond.mail.ui.components.FloatingCircleAction
import com.bond.mail.ui.glass.components.LiquidBottomTab
import com.bond.mail.ui.glass.components.LiquidBottomTabs
import com.bond.mail.ui.glass.utils.glassDockGestureBoundary
import com.bond.mail.ui.i18n.tr
import com.bond.mail.ui.motion.bondMotionEnabled

internal class ReaderDockActions(
    val messageId: String,
    val reply: () -> Unit,
    val forward: () -> Unit,
    val share: () -> Unit,
    val delete: () -> Unit,
)

/** Owned above navigation. Screens publish actions/visibility, never another dock surface. */
@Stable
internal class AppDockState {
    var reader by mutableStateOf<ReaderDockActions?>(null)
    var readerVisible by mutableStateOf(true)
    var drawerOpen by mutableStateOf(false)
}

internal val LocalAppDock = staticCompositionLocalOf<AppDockState?> { null }

/** This call site and its surfaces stay alive across main ↔ reader transitions. */
@Composable
internal fun AppBottomDock(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onCompose: () -> Unit,
    readerMode: Boolean = false,
    readerActions: ReaderDockActions? = null,
    modifier: Modifier = Modifier,
) {
    val glass = LocalUiStyle.current == UiStyle.LIQUID_GLASS
    val motion = bondMotionEnabled()
    val duration = if (motion) 180 else 0
    val labels = if (readerMode) listOf(tr("reply"), tr("forward"), tr("share"))
        else listOf(tr("mail"), tr("contacts"), tr("settings"))
    val icons = if (readerMode) listOf(Icons.AutoMirrored.Filled.Reply, Icons.AutoMirrored.Filled.Forward, Icons.Default.Share)
        else if (glass) listOf(GlassIcons.Mail, GlassIcons.People, GlassIcons.Settings)
        else listOf(Icons.Default.Email, Icons.Default.Contacts, Icons.Default.Settings)
    val actions = if (readerMode) listOf(readerActions?.reply, readerActions?.forward, readerActions?.share)
        else List(3) { index -> { onSelectTab(index) } }
    val selection by rememberUpdatedState(selectedTab)
    val onSelection by rememberUpdatedState(onSelectTab)
    val indicatorAlpha by animateFloatAsState(if (readerMode) 0f else 1f, tween(duration), label = "dock-selection-alpha")
    val trailingLabel = tr(if (readerMode) "delete" else "compose_mail")
    val trailingIcon = if (readerMode) Icons.Default.Delete else if (glass) GlassIcons.Compose else Icons.Default.Create

    Row(
        modifier.widthIn(max = BottomDockDefaults.MaxWidth).navigationBarsPadding()
            .padding(BottomDockDefaults.OuterPadding).fillMaxWidth().glassDockGestureBoundary(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (glass && LocalGlassEffects.current) {
            LiquidBottomTabs(
                { selection }, { onSelection(it) }, glassBackdrop(), 3, Modifier.weight(1f),
                selectionEnabled = !readerMode,
            ) {
                repeat(3) { index ->
                    LiquidBottomTab(
                        onClick = { actions[index]?.invoke() },
                        modifier = Modifier.semantics { if (!readerMode) selected = selectedTab == index },
                        enabled = actions[index] != null,
                        role = if (readerMode) Role.Button else Role.Tab,
                    ) { DockIcon(icons[index], labels[index], MaterialTheme.colorScheme.onSurface, duration) }
                }
            }
        } else {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.bondSurfaces.dock.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)),
                tonalElevation = 0.dp, shadowElevation = 0.dp,
            ) {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = BottomDockDefaults.VerticalInset)) {
                    val itemWidth = maxWidth / 3
                    val indicatorX by animateDpAsState(itemWidth * selectedTab + BottomDockDefaults.IndicatorHorizontalInset, tween(if (motion) 240 else 0), label = "dock-selection-x")
                    Surface(
                        modifier = Modifier.offset { IntOffset(indicatorX.roundToPx(), BottomDockDefaults.IndicatorItemInset.roundToPx()) }
                            .width(itemWidth - BottomDockDefaults.IndicatorHorizontalInset * 2).height(BottomDockDefaults.IndicatorHeight)
                            .graphicsLayer { alpha = indicatorAlpha },
                        shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer,
                        tonalElevation = 1.dp, shadowElevation = 1.dp,
                    ) {}
                    Row(Modifier.fillMaxWidth()) {
                        repeat(3) { index ->
                            val tint by animateColorAsState(
                                if (!readerMode && selectedTab == index) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                tween(duration), label = "dock-icon-color",
                            )
                            Surface(
                                onClick = { actions[index]?.invoke() },
                                enabled = actions[index] != null,
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp).height(BottomDockDefaults.ItemHeight)
                                    .semantics { if (!readerMode) selected = selectedTab == index },
                                shape = RoundedCornerShape(50), color = Color.Transparent,
                            ) {
                                Box(contentAlignment = Alignment.Center) { DockIcon(icons[index], labels[index], tint, duration) }
                            }
                        }
                    }
                }
            }
        }
        // The circle is also one persistent surface; only its role, palette and glyph change.
        FloatingCircleAction(
            onClick = { if (readerMode) readerActions?.delete?.invoke() else onCompose() },
            modifier = Modifier.size(BottomDockDefaults.Height).semantics {
                if (readerMode && readerActions == null) disabled()
            },
            containerColor = if (readerMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            contentColor = if (readerMode) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
        ) {
            DockIcon(trailingIcon, trailingLabel,
                if (glass && readerMode) MaterialTheme.colorScheme.error else LocalContentColor.current, duration)
        }
    }
}

@Composable
private fun DockIcon(icon: ImageVector, label: String, tint: Color, duration: Int) {
    Crossfade(icon, Modifier.size(24.dp).semantics { contentDescription = label },
        animationSpec = tween(duration), label = "dock-glyph") { glyph ->
        BondIcon(glyph, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}
