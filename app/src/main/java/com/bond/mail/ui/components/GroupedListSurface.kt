package com.bond.mail.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bond.mail.ui.motion.BondMotionEasing
import com.bond.mail.ui.motion.bondMotionEnabled
import com.bond.mail.ui.motion.bondPressTransform
import com.bond.mail.ui.motion.rememberBondPressInteraction
import com.bond.mail.ui.motion.rememberBondPressScale

/**
 * Shared interactive surface for grouped mail/contact rows.
 *
 * The same live [shape] is used by the shadow, content clip and ripple. This prevents the square
 * long-press halo that appeared when click handling was attached outside a rounded Card.
 */
@Composable
internal fun GroupedListSurface(
    shape: Shape,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    val motionEnabled = bondMotionEnabled()
    val interactionSource = rememberBondPressInteraction()
    val pressed by interactionSource.collectIsPressedAsState()
    val pressedScale by rememberBondPressScale(
        interactionSource = interactionSource,
        pressedScale = 0.985f,
        enabled = motionEnabled,
    )
    val elevation by animateDpAsState(
        targetValue = when {
            selected -> 2.dp
            pressed -> 1.5.dp
            else -> 0.dp
        },
        animationSpec = tween(
            durationMillis = if (motionEnabled) 120 else 0,
            easing = BondMotionEasing.Standard,
        ),
        label = "grouped-row-elevation",
    )

    Surface(
        modifier = modifier.bondPressTransform(pressedScale),
        shape = shape,
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = border,
        tonalElevation = 0.dp,
        shadowElevation = elevation,
    ) {
        val clickableModifier = if (onLongClick != null) {
            Modifier.combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            )
        } else {
            Modifier.clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .then(clickableModifier),
        ) {
            content()
        }
    }
}
