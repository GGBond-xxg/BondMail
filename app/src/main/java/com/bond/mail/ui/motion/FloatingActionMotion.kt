package com.bond.mail.ui.motion

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Keep floating actions above visible navigation, then slide into its space when it hides. */
@Composable
fun floatingActionBottomPadding(chromeVisible: Boolean): Dp {
    val bottom by animateDpAsState(
        targetValue = if (chromeVisible) 88.dp else 18.dp,
        animationSpec = tween(BondMotionDuration.ChromeReveal, easing = BondMotionEasing.Standard),
        label = "floating-action-bottom-padding",
    )
    return bottom
}
