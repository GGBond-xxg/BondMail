package com.bond.mail.ui.motion

import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos

/**
 * Scrolls a long lazy list to its beginning with a mail-client style velocity profile.
 *
 * `LazyListState.animateScrollToItem(0)` is intentionally conservative over long distances. On a
 * large mailbox it can look almost constant-speed while forcing many intermediate rows through
 * measurement. This helper first accelerates through a short visible distance, performs the large
 * virtualized jump while the list is already moving quickly, and then decelerates through the last
 * few rows using LazyColumn's measured target. Headers, row spacing and variable row heights must
 * be included in that final approach; an average-height estimate needs a visible corrective jump.
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

    // This follows the measured first item all the way to the origin, accounting for content
    // padding and item spacing. Do not append scrollToItem(0): that turns any remainder into a snap.
    animateScrollToItem(index = 0, scrollOffset = 0)
}
