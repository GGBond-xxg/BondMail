package com.bond.mail.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
import kotlinx.coroutines.delay

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
    var popupMounted by remember { mutableStateOf(false) }
    var anchorBounds by remember { mutableStateOf(Rect.Zero) }
    var pendingSelection by remember { mutableStateOf<Pair<Int, Offset>?>(null) }
    val hapticFeedback = LocalHapticFeedback.current

    LaunchedEffect(expanded) {
        if (expanded) {
            popupMounted = true
        } else if (popupMounted) {
            delay(150L)
            popupMounted = false
        }
    }

    LaunchedEffect(pendingSelection) {
        val (index, origin) = pendingSelection ?: return@LaunchedEffect
        // Let the popup leave composition before replacing the root theme renderer.
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
        onClick = {
            expanded = true
            hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
        },
        holdDownState = expanded,
    )

    if (popupMounted) {
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
                val popupWidth = 248.dp
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

                AnimatedVisibility(
                    visible = expanded,
                    modifier = Modifier.offset { IntOffset(popupX, popupY) },
                    enter = fadeIn(tween(190)) + scaleIn(
                        initialScale = 0.9f,
                        transformOrigin = TransformOrigin(0.88f, 0f),
                        animationSpec = tween(220),
                    ),
                    exit = fadeOut(tween(120)) + scaleOut(
                        targetScale = 0.94f,
                        transformOrigin = TransformOrigin(0.88f, 0f),
                        animationSpec = tween(150),
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .width(popupWidth)
                            .shadow(11.dp, popupShape)
                            .clip(popupShape)
                            .background(MiuixTheme.colorScheme.surfaceContainer),
                    ) {
                    labels.forEachIndexed { index, label ->
                        val selected = index == safeSelectedIndex
                        val rowInteraction = remember(index) { MutableInteractionSource() }
                        val contentColor = if (selected) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onSurface
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MiuixTheme.colorScheme.surfaceContainer)
                                .pressSink(rowInteraction)
                                .clickable(
                                    interactionSource = rowInteraction,
                                    indication = null,
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
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
                                color = contentColor,
                                fontSize = MiuixTheme.textStyles.body1.fontSize,
                                fontWeight = FontWeight.Medium,
                            )
                            if (selected) {
                                Icon(
                                    imageVector = MiuixIcons.Basic.Check,
                                    contentDescription = null,
                                    modifier = Modifier.padding(start = 12.dp).size(20.dp),
                                    tint = contentColor,
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
    showDot: Boolean = false,
    onClick: () -> Unit,
) {
    SuperArrow(
        title = title,
        summary = summary,
        leftAction = {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.padding(end = 14.dp).size(24.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
                if (showDot) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(7.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(androidx.compose.material3.MaterialTheme.colorScheme.error),
                    )
                }
            }
        },
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun MiuixSettingsDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}
