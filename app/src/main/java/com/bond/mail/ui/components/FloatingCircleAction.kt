package com.bond.mail.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bond.mail.ui.motion.bondMotionEnabled
import com.bond.mail.ui.motion.bondPressTransform
import com.bond.mail.ui.motion.rememberBondPressInteraction
import com.bond.mail.ui.motion.rememberBondPressScale
import com.bond.mail.data.settings.UiStyle
import com.bond.mail.ui.theme.LocalUiStyle

/** Shared raised circular action used by Compose and destructive detail actions. */
@Composable
fun FloatingCircleAction(
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val motionEnabled = bondMotionEnabled()
    val interactionSource = rememberBondPressInteraction()
    val pressScale by rememberBondPressScale(
        interactionSource = interactionSource,
        pressedScale = 0.94f,
        enabled = motionEnabled,
    )

    Surface(
        modifier = modifier
            .bondPressTransform(pressScale)
            .shadow(
                // The neighbouring navigation/action dock uses 6dp. A slightly larger external
                // shadow gives the smaller circle the same perceived lift in light mode without
                // making it look detached in dark mode.
                elevation = 8.dp,
                shape = CircleShape,
                clip = false,
            ),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().clickable(
                interactionSource = interactionSource,
                indication = if (LocalUiStyle.current == UiStyle.MIUIX) null else LocalIndication.current,
                onClick = onClick,
            ),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}
