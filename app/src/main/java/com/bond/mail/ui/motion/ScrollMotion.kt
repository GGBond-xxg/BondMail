package com.bond.mail.ui.motion

import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos
import kotlin.math.roundToInt

/**
 * Scrolls a long lazy list to its beginning with a mail-client style velocity profile.
 *
 * `LazyListState.animateScrollToItem(0)` is intentionally conservative over long distances. On a
 * large mailbox it can look almost constant-speed while forcing many intermediate rows through
 * measurement. This helper first accelerates through a short visible distance, performs the large
 * virtualized jump while the list is already moving quickly, and then decelerates through the last
 * few rows. The user sees one continuous "fast, then slow" motion instead of a rigid linear glide.
 */
suspend fun LazyListState.animateToTopWithMomentum(motionEnabled: Boolean) {
    if (firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0) return
    if (!motionEnabled) {
        scrollToItem(0)
        return
    }

    val viewportPx = (
        layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
        ).coerceAtLeast(1)

    if (firstVisibleItemIndex > 18) {
        // Establish visible upward velocity before skipping the off-screen middle of a long inbox.
        animateScrollBy(
            value = -viewportPx * 1.28f,
            animationSpec = tween(
                durationMillis = 145,
                easing = BondMotionEasing.EmphasizedAccelerate,
            ),
        )
        if (firstVisibleItemIndex > 10) {
            scrollToItem(index = 10, scrollOffset = 0)
            // Let LazyColumn publish the new nearby layout before measuring the settling distance.
            withFrameNanos { }
        }
    }

    val visibleItems = layoutInfo.visibleItemsInfo
    val averageItemSizePx = visibleItems
        .map { it.size }
        .filter { it > 0 }
        .average()
        .takeIf { !it.isNaN() && it > 0.0 }
        ?.toFloat()
        ?: (viewportPx / 6f).coerceAtLeast(1f)
    val remainingDistancePx = (
        firstVisibleItemIndex * averageItemSizePx + firstVisibleItemScrollOffset
        ).coerceAtLeast(1f)
    val remainingScreens = remainingDistancePx / viewportPx.toFloat()
    val settleDuration = (270f + remainingScreens.coerceAtMost(3.2f) * 48f)
        .roundToInt()
        .coerceIn(270, 425)

    animateScrollBy(
        value = -remainingDistancePx,
        animationSpec = tween(
            durationMillis = settleDuration,
            easing = BondMotionEasing.EmphasizedDecelerate,
        ),
    )

    // Rounding and variable row heights can leave a sub-pixel remainder. End at the exact origin.
    if (firstVisibleItemIndex != 0 || firstVisibleItemScrollOffset != 0) {
        scrollToItem(0)
    }
}
