package com.bond.mail.ui.glass.utils

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

/** Children get Main first. Consume leftover movement before the enclosing drawer sees it,
 * including gestures starting outside the selected pill and drags that initially move vertically.
 * Keep tracking until every pointer is up; moving outside the dock must not hand off to the drawer.
 */
internal fun Modifier.glassDockGestureBoundary(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var displacement = Offset.Zero
        var moved = false
        do {
            val event = awaitPointerEvent()
            event.changes.firstOrNull()?.let { displacement += it.position - it.previousPosition }
            moved = moved || displacement.getDistance() > viewConfiguration.touchSlop
            if (moved) event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}
