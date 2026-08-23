package com.bond.mail.ui.motion

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val STAGGER_STEP_MS = 42L
private const val STAGGER_DURATION_MS = 250
private const val STAGGER_ENTRY_WINDOW_MS = 820L
private const val MAX_STAGGERED_ITEM_COUNT = 12

/** One entrance timeline shared by the visible content of a top-level destination. */
@Stable
class BondStaggeredEntranceState internal constructor(
    internal val enabled: Boolean,
    internal val startedAtMs: Long,
)

@Composable
fun rememberBondStaggeredEntranceState(
    enabled: Boolean = bondMotionEnabled(),
): BondStaggeredEntranceState = remember {
    BondStaggeredEntranceState(
        enabled = enabled,
        startedAtMs = SystemClock.uptimeMillis(),
    )
}

/**
 * Reveals the first viewport in a short, shared stagger: fade, rise and a very small scale-up.
 *
 * The elapsed-time guard is intentional. Lazy content composed later because the user scrolled
 * must be immediately visible instead of replaying a page-entry animation.
 */
fun Modifier.bondStaggeredEntrance(
    state: BondStaggeredEntranceState,
    index: Int,
    verticalOffset: Dp = 12.dp,
): Modifier = composed {
    val safeIndex = index.coerceAtLeast(0)
    val shouldAnimate = state.enabled && safeIndex < MAX_STAGGERED_ITEM_COUNT
    val progress = remember(state, safeIndex) {
        Animatable(if (shouldAnimate) 0f else 1f)
    }
    val offsetPx = with(LocalDensity.current) { verticalOffset.toPx() }

    LaunchedEffect(state, safeIndex, shouldAnimate) {
        if (!shouldAnimate) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }

        val elapsedMs = SystemClock.uptimeMillis() - state.startedAtMs
        if (elapsedMs >= STAGGER_ENTRY_WINDOW_MS) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }

        val scheduledStartMs = safeIndex * STAGGER_STEP_MS
        delay((scheduledStartMs - elapsedMs).coerceAtLeast(0L))
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = STAGGER_DURATION_MS,
                easing = BondMotionEasing.EmphasizedDecelerate,
            ),
        )
    }

    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * offsetPx
        val scale = 0.985f + (0.015f * progress.value)
        scaleX = scale
        scaleY = scale
    }
}
