package com.bond.mail.ui.motion

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun rememberBondPressInteraction(): MutableInteractionSource = remember { MutableInteractionSource() }

/**
 * Replaces a navigation control's interaction subtree, lets that resting state reach the screen,
 * and only then runs the navigation action.
 *
 * Back-stack destinations are paused as soon as navigation begins. Without this reset, a ripple or
 * press-release spring can be saved mid-frame and resume when the user returns.
 */
@Stable
class BondPressResetter internal constructor(
    private val scope: CoroutineScope,
) {
    var epoch by mutableIntStateOf(0)
        private set

    private var actionPending = false

    fun resetThen(action: () -> Unit) {
        if (actionPending) return
        actionPending = true
        epoch += 1
        scope.launch {
            // The first frame applies the new interaction subtree; the second proves that resting
            // pixels were drawn before a NavBackStackEntry can pause this composition.
            withFrameNanos { }
            withFrameNanos { }
            actionPending = false
            action()
        }
    }
}

@Composable
fun rememberBondPressResetter(): BondPressResetter {
    val scope = rememberCoroutineScope()
    return remember(scope) { BondPressResetter(scope) }
}

@Composable
fun rememberBondPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float,
    enabled: Boolean = true,
): State<Float> {
    val pressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (enabled && pressed) pressedScale else 1f,
        animationSpec = BondMotionSpring.PressRelease,
        label = "bond-press-scale",
    )
}

fun Modifier.bondPressTransform(scale: Float): Modifier = graphicsLayer {
    scaleX = scale
    scaleY = scale
}
