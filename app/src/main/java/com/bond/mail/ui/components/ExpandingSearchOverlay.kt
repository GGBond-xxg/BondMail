package com.bond.mail.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bond.mail.data.settings.UiStyle
import com.bond.mail.ui.i18n.tr
import com.bond.mail.ui.motion.BondMotionDuration
import com.bond.mail.ui.motion.BondMotionEasing
import com.bond.mail.ui.motion.bondMotionEnabled
import com.bond.mail.ui.theme.BondIcon as Icon
import com.bond.mail.ui.theme.BondIconButton as IconButton
import com.bond.mail.ui.theme.BondSearchField
import com.bond.mail.ui.theme.LocalUiStyle
import com.bond.mail.ui.theme.bondSurfaces

/** Shared search container transform, focus lifecycle and inset handling. */
@Composable
internal fun ExpandingSearchOverlay(
    expanded: Boolean,
    sourceBounds: Rect?,
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onDismiss: () -> Unit,
    onClosed: () -> Unit,
    onTransformActivityChanged: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val motionEnabled = bondMotionEnabled()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val progress = remember { Animatable(if (expanded) 1f else 0f) }
    var searchHasFocus by remember { mutableStateOf(false) }
    var hasOpened by remember { mutableStateOf(expanded) }
    val latestOnClosed by rememberUpdatedState(onClosed)
    val latestOnTransformActivityChanged by rememberUpdatedState(onTransformActivityChanged)

    val normalizedProgress = progress.value.coerceIn(0f, 1f)
    val transformActive = expanded || progress.isRunning || normalizedProgress > 0.001f
    val showCollapsedContainer = transformActive

    BackHandler(enabled = transformActive) { onDismiss() }

    LaunchedEffect(expanded, motionEnabled) {
        if (expanded) {
            hasOpened = true
            latestOnTransformActivityChanged(true)
            if (motionEnabled) {
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = BondMotionDuration.ContainerTransform,
                        easing = BondMotionEasing.EmphasizedDecelerate,
                    ),
                )
            } else {
                progress.snapTo(1f)
            }
            if (expanded && !searchHasFocus) {
                searchHasFocus = true
                focusRequester.requestFocus()
                keyboardController?.show()
            }
        } else {
            // Keep the keyboard and the expanded root constraints in place until the container has
            // returned to its source. Hiding the IME at the beginning of the reverse animation
            // resizes the root mid-flight and causes the one-frame jump seen in the recording.
            if (motionEnabled && progress.value > 0.001f) {
                progress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = BondMotionDuration.SharedAxis,
                        easing = BondMotionEasing.EmphasizedAccelerate,
                    ),
                )
            } else {
                progress.snapTo(0f)
            }
            if (searchHasFocus) {
                searchHasFocus = false
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }
            latestOnTransformActivityChanged(false)
            if (hasOpened) {
                hasOpened = false
                latestOnClosed()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            latestOnTransformActivityChanged(false)
            if (searchHasFocus) {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val fallbackSourceX = (maxWidth - 152.dp).coerceAtLeast(12.dp)
        val sourceX = sourceBounds?.let { with(density) { it.left.toDp() } } ?: fallbackSourceX
        // boundsInRoot already includes the top bar's graphics-layer translation. Adding the
        // animated offset a second time made search travel twice as far as menu/more/add.
        val sourceY = sourceBounds?.let { with(density) { it.top.toDp() } } ?: statusInset + 12.dp
        val sourceWidth = sourceBounds?.let { with(density) { it.width.toDp() } } ?: 44.dp
        val sourceHeight = sourceBounds?.let { with(density) { it.height.toDp() } } ?: 44.dp
        val targetX = 12.dp
        val targetY = statusInset + 8.dp
        val targetWidth = (maxWidth - 24.dp).coerceAtLeast(56.dp)
        val targetHeight = 56.dp

        fun interpolate(start: Dp, end: Dp): Dp =
            start + (end - start) * normalizedProgress

        val searchX = interpolate(sourceX, targetX)
        val searchY = interpolate(sourceY, targetY)
        val searchWidth = interpolate(sourceWidth, targetWidth)
        val searchHeight = interpolate(sourceHeight, targetHeight)
        val cornerRadius = interpolate(sourceHeight / 2f, 28.dp)
        val iconX = interpolate(
            ((sourceWidth - 24.dp) / 2f).coerceAtLeast(0.dp),
            16.dp,
        )
        val scrimAlpha = 0.34f * normalizedProgress
        val fieldAlpha = ((normalizedProgress - 0.18f) / 0.42f).coerceIn(0f, 1f)
        val panelAlpha = ((normalizedProgress - 0.28f) / 0.44f).coerceIn(0f, 1f)
        val panelScale = 0.985f + 0.015f * panelAlpha

        if (transformActive && scrimAlpha > 0.001f) {
            val noRipple = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = noRipple,
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }

        if (transformActive && normalizedProgress > 0.12f) {
            Surface(
                modifier = Modifier
                    .imePadding()
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = statusInset + 72.dp,
                        bottom = 12.dp,
                    )
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = panelAlpha
                        scaleX = panelScale
                        scaleY = panelScale
                    },
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.bondSurfaces.content,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
            ) {
                content()
            }
        }

        // During the transform this Surface temporarily takes ownership from the top-bar button.
        // Outside the transform, the real button stays in the shared top-bar row and therefore uses
        // exactly the same scroll translation as its siblings.
        if (showCollapsedContainer) {
            Surface(
                modifier = Modifier
                    .offset(x = searchX, y = searchY)
                    .size(width = searchWidth, height = searchHeight),
                shape = RoundedCornerShape(cornerRadius),
                color = MaterialTheme.bondSurfaces.popup,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                tonalElevation = 0.dp,
                shadowElevation = 6.dp * fieldAlpha,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier
                            .offset(x = iconX)
                            .size(24.dp),
                    )
                    if (transformActive && fieldAlpha > 0.001f) {
                        BondSearchField(
                            value = query,
                            onValueChange = onQueryChange,
                            modifier = Modifier
                                .fillMaxSize()
                                .focusRequester(focusRequester)
                                .graphicsLayer { alpha = fieldAlpha },
                            placeholder = placeholder,
                            leadingIcon = { Spacer(Modifier.size(24.dp)) },
                            trailingIcon = {
                                Box(Modifier.padding(end = if (LocalUiStyle.current == UiStyle.MIUIX) 0.dp else 8.dp)) {
                                    IconButton(
                                        onClick = {
                                            if (query.isNotEmpty()) onQueryChange("") else onDismiss()
                                        },
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = tr("cancel"))
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

