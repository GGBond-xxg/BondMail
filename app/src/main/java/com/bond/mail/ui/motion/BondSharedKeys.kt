package com.bond.mail.ui.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.staticCompositionLocalOf

internal enum class MailSharedType {
    Container,
}

internal data class MailSharedKey(
    val messageId: String,
    val type: MailSharedType,
)

internal enum class ComposeSharedKey {
    MainFabContainer,
}

enum class ComposeLaunchOrigin {
    MainFab,
    MessageAction,
    Other,
}

@OptIn(ExperimentalSharedTransitionApi::class)
internal val LocalBondSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

internal val LocalBondNavAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }
