package com.bond.mail.ui.motion

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Shared scroll-to-hide behaviour for every main tab.
 *
 * Screens keep their own list and top chrome, while the parent owns [visible] so the floating
 * bottom dock and the active screen always move as one piece. Direction changes clear the
 * accumulator to prevent tiny finger corrections or a settling fling from toggling the chrome.
 */
@Composable
fun ObserveLazyListChromeVisibility(
    listState: LazyListState,
    visible: Boolean,
    onVisibilityChanged: (Boolean) -> Unit,
    enabled: Boolean = true,
    hideThreshold: Dp = 52.dp,
    revealThreshold: Dp = 52.dp,
    topResetOffsetPx: Int = 8,
    onScrollInProgressChanged: (Boolean) -> Unit = {},
) {
    val density = LocalDensity.current
    val hideThresholdPx = with(density) { hideThreshold.roundToPx() }
    val revealThresholdPx = with(density) { revealThreshold.roundToPx() }
    val latestOnVisibilityChanged by rememberUpdatedState(onVisibilityChanged)
    val latestOnScrollInProgressChanged by rememberUpdatedState(onScrollInProgressChanged)

    LaunchedEffect(
        listState,
        enabled,
        hideThresholdPx,
        revealThresholdPx,
        topResetOffsetPx,
        visible,
    ) {
        var userHasScrolled = false
        var previousIndex = listState.firstVisibleItemIndex
        var previousOffset = listState.firstVisibleItemScrollOffset
        var accumulatedDelta = 0
        var lastDirection = 0
        var requestedVisible = visible

        if (!enabled) return@LaunchedEffect

        snapshotFlow {
            Triple(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                listState.isScrollInProgress,
            )
        }
            .distinctUntilChanged()
            .collect { (index, offset, scrolling) ->
                if (scrolling) userHasScrolled = true
                if (userHasScrolled) latestOnScrollInProgressChanged(scrolling)

                when {
                    index == 0 && offset <= topResetOffsetPx -> {
                        accumulatedDelta = 0
                        lastDirection = 0
                        if (!requestedVisible) {
                            requestedVisible = true
                            latestOnVisibilityChanged(true)
                        }
                    }

                    !scrolling -> {
                        accumulatedDelta = 0
                        lastDirection = 0
                    }

                    else -> {
                        val delta = when {
                            index > previousIndex -> hideThresholdPx
                            index < previousIndex -> -revealThresholdPx
                            else -> offset - previousOffset
                        }
                        val direction = delta.compareTo(0)
                        if (direction != 0) {
                            if (lastDirection != 0 && direction != lastDirection) {
                                accumulatedDelta = 0
                            }
                            accumulatedDelta += delta
                            lastDirection = direction
                        }

                        when {
                            accumulatedDelta >= hideThresholdPx && requestedVisible -> {
                                requestedVisible = false
                                accumulatedDelta = 0
                                latestOnVisibilityChanged(false)
                            }

                            accumulatedDelta <= -revealThresholdPx && !requestedVisible -> {
                                requestedVisible = true
                                accumulatedDelta = 0
                                latestOnVisibilityChanged(true)
                            }
                        }
                    }
                }

                previousIndex = index
                previousOffset = offset
            }
    }

    DisposableEffect(listState, enabled) {
        if (!enabled) latestOnScrollInProgressChanged(false)
        onDispose { latestOnScrollInProgressChanged(false) }
    }
}

/** Uses one duration/easing pair for top bars and the shared bottom dock. */
@Composable
fun animateChromeOffset(
    visible: Boolean,
    hiddenOffset: Dp,
    label: String,
): Dp {
    val offset by animateDpAsState(
        targetValue = if (visible) 0.dp else hiddenOffset,
        animationSpec = tween(
            durationMillis = BondMotionDuration.ChromeReveal,
            easing = if (visible) {
                BondMotionEasing.EmphasizedDecelerate
            } else {
                BondMotionEasing.EmphasizedAccelerate
            },
        ),
        label = label,
    )
    return offset
}
