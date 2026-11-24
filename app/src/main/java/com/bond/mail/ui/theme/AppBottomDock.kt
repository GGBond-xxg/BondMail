package com.bond.mail.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
import com.bond.mail.ui.motion.BondMotionDuration
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance

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
    var progressMessageId by mutableStateOf<String?>(null)
    var readerProgress by mutableFloatStateOf(0f)
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
    visualProgress: Float? = null,
) {
    val glass = LocalUiStyle.current == UiStyle.LIQUID_GLASS
    val material = LocalUiStyle.current == UiStyle.MATERIAL3
    val motion = bondMotionEnabled()
    val animatedProgress by animateFloatAsState(if (readerMode) 1f else 0f,
        tween(if (motion) 260 else 0), label = "dock-mode-progress")
    val progress = (visualProgress ?: animatedProgress).coerceIn(0f, 1f)
    val interactionReady = if (readerMode) progress >= .999f else progress <= .001f
    val labels = if (readerMode) listOf(tr("reply"), tr("forward"), tr("share"))
        else listOf(tr("mail"), tr("contacts"), tr("settings"))
    val readerIcons = listOf(Icons.AutoMirrored.Filled.Reply, Icons.AutoMirrored.Filled.Forward, Icons.Default.Share)
    val mainIcons = if (glass) listOf(GlassIcons.Mail, GlassIcons.People, GlassIcons.Settings)
        else listOf(Icons.Default.Email, Icons.Default.Contacts, Icons.Default.Settings)
    val actions = if (readerMode) listOf(readerActions?.reply, readerActions?.forward, readerActions?.share)
        else List(3) { index -> { onSelectTab(index) } }
    val selection by rememberUpdatedState(selectedTab)
    val onSelection by rememberUpdatedState(onSelectTab)
    val indicatorAlpha = 1f - progress
    val trailingLabel = tr(if (readerMode) "delete" else "compose_mail")
    val mainTrailingIcon = if (glass) GlassIcons.Compose else Icons.Default.Create
    val palette = MaterialTheme.colorScheme
    val actionColor = lerp(palette.primary, palette.error, progress)
    val actionForeground = lerp(palette.onPrimary, if (glass) palette.error else palette.onError, progress)

    Row(
        modifier.widthIn(max = BottomDockDefaults.MaxWidth).navigationBarsPadding()
            .padding(BottomDockDefaults.OuterPadding).fillMaxWidth().glassDockGestureBoundary(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (material) M3SplitDockDefaults.Gap else 10.dp),
    ) {
        if (material) {
            M3SplitDockParts(
                selectedTab = selectedTab,
                readerMode = readerMode,
                indicatorAlpha = indicatorAlpha,
                enabled = interactionReady,
                actions = actions,
                trailingEnabled = interactionReady && (!readerMode || readerActions != null),
                onTrailingClick = { if (readerMode) readerActions?.delete?.invoke() else onCompose() },
                motion = motion,
                tabIcon = { index, tint -> DockIcon(mainIcons[index], readerIcons[index], progress, labels[index], tint) },
                trailingIcon = { tint -> DockIcon(mainTrailingIcon, Icons.Default.Delete, progress, trailingLabel, tint) },
            )
        } else {
            if (glass && LocalGlassEffects.current) {
                LiquidBottomTabs(
                    { selection }, { onSelection(it) }, glassBackdrop(), 3, Modifier.weight(1f),
                    selectionEnabled = !readerMode,
                    selectionAlpha = indicatorAlpha,
                ) {
                    repeat(3) { index ->
                        LiquidBottomTab(
                            onClick = { if (interactionReady) actions[index]?.invoke() },
                            modifier = Modifier.semantics {
                                if (!readerMode) selected = selectedTab == index
                                if (!interactionReady) disabled()
                            },
                            enabled = actions[index] != null,
                            role = if (readerMode) Role.Button else Role.Tab,
                        ) { DockIcon(mainIcons[index], readerIcons[index], progress, labels[index], palette.onSurface) }
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
                            tonalElevation = 0.dp, shadowElevation = 0.dp,
                        ) {}
                        Row(Modifier.fillMaxWidth()) {
                            repeat(3) { index ->
                                val mainTint by animateColorAsState(
                                    if (selectedTab == index) palette.onSecondaryContainer else palette.onSurfaceVariant,
                                    tween(if (motion) 180 else 0), label = "dock-tab-color")
                                val tint = lerp(mainTint, palette.onSurfaceVariant, progress)
                                val interaction = remember { MutableInteractionSource() }
                                val pressAlpha = rememberDockPressAlpha(interaction, motion)
                                // Keep the full touch target; draw press feedback only inside the
                                // 44dp capsule, 2dp larger on each side than the selected capsule.
                                Box(
                                    modifier = Modifier.weight(1f).height(BottomDockDefaults.ItemHeight)
                                        .clickable(
                                            interactionSource = interaction,
                                            indication = null,
                                            enabled = interactionReady && actions[index] != null,
                                            role = if (readerMode) Role.Button else Role.Tab,
                                            onClick = { actions[index]?.invoke() },
                                        )
                                        .semantics {
                                            if (!readerMode) selected = selectedTab == index
                                            if (!interactionReady) disabled()
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        Modifier.fillMaxWidth().padding(horizontal = BottomDockDefaults.PressHorizontalInset)
                                            .height(BottomDockDefaults.PressHeight)
                                            .clip(RoundedCornerShape(50))
                                            .drawBehind { drawRect(palette.onSurface.copy(alpha = .10f * pressAlpha.value)) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        DockIcon(mainIcons[index], readerIcons[index], progress, labels[index], tint)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // The circle is also one persistent surface; only its role, palette and glyph change.
            FloatingCircleAction(
                onClick = { if (interactionReady) {
                    if (readerMode) readerActions?.delete?.invoke() else onCompose()
                } },
                modifier = Modifier.size(BottomDockDefaults.Height).semantics {
                    if (!interactionReady || (readerMode && readerActions == null)) disabled()
                },
                containerColor = actionColor,
                contentColor = actionForeground,
                glassPrimaryFraction = 1f - progress,
            ) {
                DockIcon(mainTrailingIcon, Icons.Default.Delete, progress, trailingLabel, actionForeground)
            }
        }
    }
}

/** Finish the short attack even for a quick tap, then fade without cutting off on selection. */
@Composable
private fun rememberDockPressAlpha(interaction: MutableInteractionSource, motion: Boolean): State<Float> {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(interaction, motion) {
        interaction.interactions.filterIsInstance<PressInteraction>().collectLatest { event ->
            when (event) {
                is PressInteraction.Press -> alpha.animateTo(1f,
                    tween(if (motion) BondMotionDuration.EffectQuick else 0))
                is PressInteraction.Release -> {
                    if (motion && alpha.value < 1f) alpha.animateTo(1f,
                        tween((BondMotionDuration.EffectQuick * (1f - alpha.value)).toInt()))
                    alpha.animateTo(0f, tween(if (motion) BondMotionDuration.DockPressRelease else 0,
                        easing = LinearEasing))
                }
                is PressInteraction.Cancel -> alpha.animateTo(0f,
                    tween(if (motion) BondMotionDuration.ElementEnter else 0, easing = LinearEasing))
            }
        }
    }
    return alpha.asState()
}

@Composable
private fun DockIcon(main: ImageVector, reader: ImageVector, progress: Float, label: String, tint: Color) {
    Box(Modifier.size(24.dp).semantics { contentDescription = label }) {
        BondIcon(main, null, Modifier.size(24.dp).graphicsLayer { alpha = 1f - progress }, tint = tint)
        BondIcon(reader, null, Modifier.size(24.dp).graphicsLayer { alpha = progress }, tint = tint)
    }
}
