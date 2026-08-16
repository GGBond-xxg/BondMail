package com.bond.mail.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.basic.ArrowUpDownIntegrated
import top.yukonga.miuix.kmp.icon.icons.basic.Check
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SmoothRoundedCornerShape
import top.yukonga.miuix.kmp.utils.pressSink

@Composable
fun MiuixSettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
        content = content,
    )
}

@Composable
fun MiuixSectionTitle(text: String) {
    SmallTitle(
        text = text,
        insideMargin = PaddingValues(start = 16.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
fun MiuixDropdownSetting(
    title: String,
    summary: String? = null,
    labels: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (index: Int, centerInWindow: Offset) -> Unit,
) {
    if (labels.isEmpty()) return

    val safeSelectedIndex = selectedIndex.coerceIn(labels.indices)
    var expanded by remember { mutableStateOf(false) }
    var anchorBounds by remember { mutableStateOf(Rect.Zero) }
    var pendingSelection by remember { mutableStateOf<Pair<Int, Offset>?>(null) }

    LaunchedEffect(pendingSelection) {
        val (index, origin) = pendingSelection ?: return@LaunchedEffect
        // Let the anchored dialog leave composition before replacing the root theme renderer.
        withFrameNanos { }
        pendingSelection = null
        onSelectedIndexChange(index, origin)
    }

    BasicComponent(
        title = title,
        summary = summary,
        rightActions = {
            Text(
                text = labels[safeSelectedIndex],
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
            )
            Icon(
                imageVector = MiuixIcons.Basic.ArrowUpDownIntegrated,
                contentDescription = null,
                modifier = Modifier.padding(start = 8.dp).size(10.dp, 16.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                anchorBounds = coordinates.boundsInWindow()
            },
        onClick = { expanded = true },
        holdDownState = expanded,
    )

    if (expanded) {
        val outsideInteraction = remember { MutableInteractionSource() }
        val popupShape = remember { SmoothRoundedCornerShape(16.dp) }
        Dialog(
            onDismissRequest = { expanded = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = outsideInteraction,
                        indication = null,
                        onClick = { expanded = false },
                    ),
            ) {
                val density = LocalDensity.current
                val popupWidth = 220.dp
                val popupHeight = (labels.size * 56 + 16).dp
                val popupWidthPx = with(density) { popupWidth.roundToPx() }
                val popupHeightPx = with(density) { popupHeight.roundToPx() }
                val marginPx = with(density) { 12.dp.roundToPx() }
                val screenWidthPx = constraints.maxWidth
                val screenHeightPx = constraints.maxHeight
                val popupX = (anchorBounds.right.toInt() - popupWidthPx - marginPx)
                    .coerceIn(marginPx, (screenWidthPx - popupWidthPx - marginPx).coerceAtLeast(marginPx))
                val spaceBelow = screenHeightPx - anchorBounds.bottom.toInt()
                val popupY = if (spaceBelow >= popupHeightPx + marginPx) {
                    anchorBounds.bottom.toInt() + marginPx
                } else {
                    anchorBounds.top.toInt() - popupHeightPx - marginPx
                }.coerceIn(marginPx, (screenHeightPx - popupHeightPx - marginPx).coerceAtLeast(marginPx))

                Column(
                    modifier = Modifier
                        .offset { IntOffset(popupX, popupY) }
                        .width(popupWidth)
                        .shadow(11.dp, popupShape)
                        .clip(popupShape)
                        .background(MiuixTheme.colorScheme.surface),
                ) {
                    labels.forEachIndexed { index, label ->
                        val selected = index == safeSelectedIndex
                        val rowInteraction = remember(index) { MutableInteractionSource() }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                // MIUIX uses an elastic sink response, not Material's expanding
                                // ripple. Share the source so the native press modifier receives
                                // the exact press/release lifecycle from clickable.
                                .pressSink(rowInteraction)
                                .clickable(
                                    interactionSource = rowInteraction,
                                    indication = null,
                                    onClick = {
                                        expanded = false
                                        if (!selected) {
                                            pendingSelection = index to anchorBounds.center
                                        }
                                    },
                                )
                                .padding(
                                    start = 20.dp,
                                    end = 20.dp,
                                    top = if (index == 0) 20.dp else 12.dp,
                                    bottom = if (index == labels.lastIndex) 20.dp else 12.dp,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.weight(1f),
                                color = if (selected) {
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    MiuixTheme.colorScheme.onSurface
                                },
                                fontSize = MiuixTheme.textStyles.body1.fontSize,
                                fontWeight = FontWeight.Medium,
                            )
                            if (selected) {
                                Icon(
                                    imageVector = MiuixIcons.Basic.Check,
                                    contentDescription = null,
                                    modifier = Modifier.padding(start = 12.dp).size(20.dp),
                                    tint = MiuixTheme.colorScheme.primary,
                                )
                            } else {
                                Spacer(Modifier.padding(start = 12.dp).size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MiuixPermissionSetting(
    icon: ImageVector,
    title: String,
    summary: String,
    status: String,
    actionRequired: Boolean,
    onClick: () -> Unit,
) {
    BasicComponent(
        title = title,
        summary = summary,
        leftAction = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(end = 14.dp).size(24.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        },
        rightActions = {
            Text(
                text = status,
                modifier = Modifier.padding(end = 8.dp),
                color = if (actionRequired) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantActions
                },
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                fontWeight = FontWeight.Medium,
            )
        },
        onClick = if (actionRequired) onClick else null,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun MiuixSwitchSetting(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SuperSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        title = title,
        summary = summary,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun MiuixActionSetting(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    SuperArrow(
        title = title,
        summary = summary,
        leftAction = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(end = 14.dp).size(24.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        },
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun MiuixSettingsDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}
