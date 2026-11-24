package com.bond.mail.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance

internal object M3SplitDockDefaults {
    val Gap = 2.dp
    val InnerCorner = 4.dp
    val PressedInnerCorner = 8.dp
    const val PressMillis = 140
    const val ReleaseMillis = 260
    const val SelectionMillis = 300
}

/** Two independently clipped surfaces; only the pressed half changes its joining corners. */
@Composable
internal fun RowScope.M3SplitDockParts(
    selectedTab: Int,
    readerMode: Boolean,
    indicatorAlpha: Float,
    enabled: Boolean,
    actions: List<(() -> Unit)?>,
    trailingEnabled: Boolean,
    onTrailingClick: () -> Unit,
    motion: Boolean,
    tabIcon: @Composable (Int, Color) -> Unit,
    trailingIcon: @Composable (Color) -> Unit,
) {
    val palette = MaterialTheme.colorScheme
    val navigationInteraction = remember { MutableInteractionSource() }
    val actionInteraction = remember { MutableInteractionSource() }
    val navigationCorner = splitDockInnerCorner(navigationInteraction, motion)
    val actionCorner = splitDockInnerCorner(actionInteraction, motion)
    val outerCorner = BottomDockDefaults.Height / 2
    val border = BorderStroke(1.dp, palette.outlineVariant.copy(alpha = .38f))
    Surface(
        modifier = Modifier.weight(1f).height(BottomDockDefaults.Height),
        shape = RoundedCornerShape(topStart = outerCorner, bottomStart = outerCorner,
            topEnd = navigationCorner, bottomEnd = navigationCorner),
        color = palette.surfaceContainer, border = border,
        tonalElevation = 0.dp, shadowElevation = 0.dp,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = BottomDockDefaults.VerticalInset)) {
            val itemWidth = maxWidth / actions.size
            val targetX = itemWidth * selectedTab + BottomDockDefaults.IndicatorHorizontalInset
            val animatedX by animateDpAsState(targetX,
                tween(if (motion) M3SplitDockDefaults.SelectionMillis else 0, easing = FastOutSlowInEasing),
                label = "split-dock-selection")
            val indicatorX = if (motion) animatedX else targetX
            Surface(
                Modifier.offset { IntOffset(indicatorX.roundToPx(), BottomDockDefaults.IndicatorItemInset.roundToPx()) }
                    .width(itemWidth - BottomDockDefaults.IndicatorHorizontalInset * 2)
                    .height(BottomDockDefaults.IndicatorHeight).graphicsLayer { alpha = indicatorAlpha },
                shape = RoundedCornerShape(50), color = palette.primaryContainer,
                tonalElevation = 0.dp, shadowElevation = 0.dp,
            ) {}
            Row(Modifier.fillMaxWidth().then(if (readerMode) Modifier else Modifier.selectableGroup())) {
                actions.forEachIndexed { index, action ->
                    val mainTint by animateColorAsState(
                        if (selectedTab == index) palette.onPrimaryContainer else palette.onSurfaceVariant,
                        tween(if (motion) M3SplitDockDefaults.SelectionMillis else 0), label = "split-dock-icon")
                    Box(
                        Modifier.weight(1f).height(BottomDockDefaults.ItemHeight)
                            .clickable(interactionSource = navigationInteraction, indication = null,
                                enabled = enabled && action != null,
                                role = if (readerMode) Role.Button else Role.Tab,
                                onClick = { action?.invoke() })
                            .semantics { if (!readerMode) selected = selectedTab == index },
                        contentAlignment = Alignment.Center,
                    ) { tabIcon(index, lerp(palette.onSurfaceVariant, mainTint, indicatorAlpha)) }
                }
            }
        }
    }
    Surface(
        modifier = Modifier.size(BottomDockDefaults.Height),
        shape = RoundedCornerShape(topStart = actionCorner, bottomStart = actionCorner,
            topEnd = outerCorner, bottomEnd = outerCorner),
        color = palette.surfaceContainer, border = border,
        tonalElevation = 0.dp, shadowElevation = 0.dp,
    ) {
        Box(Modifier.fillMaxSize().clickable(interactionSource = actionInteraction, indication = null,
            enabled = trailingEnabled, role = Role.Button, onClick = onTrailingClick),
            contentAlignment = Alignment.Center) { trailingIcon(palette.onSurface) }
    }
}

@Composable
private fun splitDockInnerCorner(interaction: MutableInteractionSource, motion: Boolean): Dp {
    val rest = M3SplitDockDefaults.InnerCorner.value
    val pressed = M3SplitDockDefaults.PressedInnerCorner.value
    val corner = remember { Animatable(rest) }
    LaunchedEffect(interaction, motion) {
        val presses = mutableSetOf<PressInteraction.Press>()
        corner.snapTo(rest)
        interaction.interactions.filterIsInstance<PressInteraction>().collectLatest { event ->
            when (event) {
                is PressInteraction.Press -> presses.add(event)
                is PressInteraction.Release -> presses.remove(event.press)
                is PressInteraction.Cancel -> presses.remove(event.press)
            }
            if (!motion) corner.snapTo(rest)
            else if (presses.isNotEmpty()) corner.animateTo(pressed,
                tween(M3SplitDockDefaults.PressMillis, easing = FastOutSlowInEasing))
            else {
                // Match the reference's one-frame tap fallback; all releases restore in 260ms.
                if (event is PressInteraction.Release && corner.value == rest) corner.snapTo(pressed)
                corner.animateTo(rest, tween(M3SplitDockDefaults.ReleaseMillis, easing = FastOutSlowInEasing))
            }
        }
    }
    return if (motion) corner.value.dp else M3SplitDockDefaults.InnerCorner
}
