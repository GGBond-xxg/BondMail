package com.bond.mail.ui.motion

import org.junit.Assert.*
import org.junit.Test

class BondBackMotionTest {
    @Test fun heldGestureKeepsBothEdgesOfCardOnScreen() {
        for (right in listOf(false, true)) for (step in 0..100) {
            val card = backCardTransform(step / 100f, 0f, 1080f, 2400f, right)
            val left = (1080f - 1080f * card.scale) / 2f + card.translationX
            val rightEdge = left + 1080f * card.scale
            assertTrue(left >= -0.001f)
            assertTrue(rightEdge <= 1080.001f)
            assertTrue(card.scale in 0.9f..1f)
        }
    }

    @Test fun committedGestureClearsTheWholeScreenFromEitherEdge() {
        for (right in listOf(false, true)) {
            val card = backCardTransform(1f, 1f, 1080f, 2400f, right)
            val left = (1080f - 1080f * card.scale) / 2f + card.translationX
            if (right) assertTrue(left + 1080f * card.scale < 0f)
            else assertTrue(left > 1080f)
            assertEquals(0f, card.scrimAlpha)
        }
    }

    @Test fun cancellationRestoresUntransformedPageAndVerticalMotionIsBounded() {
        val reset = backCardTransform(0f, 0f, 1080f, 2400f, true, 9999f)
        assertEquals(1f, reset.scale)
        assertEquals(0f, reset.translationX, 0.001f)
        assertEquals(0f, reset.translationY, 0.001f)
        assertEquals(0f, reset.scrimAlpha)
        assertTrue(backCardTransform(1f, 0f, 1080f, 2400f, false, 9999f).translationY <= 72f)
    }
}
