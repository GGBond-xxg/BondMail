package com.bond.mail.ui.glass.utils

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs

/** Own horizontal drags after touch slop; leave vertical scrolling to the parent. */
internal suspend fun PointerInputScope.horizontalGlassGesture(
    onStart: (PointerInputChange) -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    onDrag: (Offset) -> Unit,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    onStart(down)
    var displacement = Offset.Zero
    var dragging = false
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
        if (change == null || change.isConsumed) {
            onCancel()
            break
        }
        if (!change.pressed) {
            change.consume()
            onStop()
            break
        }
        val delta = change.positionChange()
        displacement += delta
        if (!dragging && displacement.getDistance() > viewConfiguration.touchSlop) {
            if (abs(displacement.y) >= abs(displacement.x)) {
                onCancel()
                break
            }
            dragging = true
            change.consume()
            onDrag(displacement)
        } else if (dragging) {
            change.consume()
            onDrag(delta)
        }
        // A parent can claim a vertical gesture in Main after this child saw it.
        if (!dragging) {
            val final = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull { it.id == down.id }
            if (final == null || final.isConsumed) {
                onCancel()
                break
            }
        }
    }
}
