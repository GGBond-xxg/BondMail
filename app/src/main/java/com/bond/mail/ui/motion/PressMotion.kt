package com.bond.mail.ui.motion

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun rememberBondPressInteraction(): MutableInteractionSource = remember { MutableInteractionSource() }

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
