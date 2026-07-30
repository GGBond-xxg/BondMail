package com.bond.mail.ui.motion

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing

private const val THUNDERBIRD_SWITCH_DURATION_MS = 285
private val ThunderbirdSwitcherEasing = CubicBezierEasing(0.20f, 0f, 0f, 1f)

fun bondFadeThrough(enabled: Boolean): ContentTransform {
    if (!enabled) return EnterTransition.None togetherWith ExitTransition.None
    return (
        fadeIn(
            animationSpec = tween(
                durationMillis = 150,
                delayMillis = 90,
                easing = BondMotionEasing.EmphasizedDecelerate,
            ),
        ) + scaleIn(
            initialScale = 0.98f,
            animationSpec = tween(
                durationMillis = 150,
                delayMillis = 90,
                easing = BondMotionEasing.EmphasizedDecelerate,
            ),
        )
    ) togetherWith fadeOut(
        animationSpec = tween(
            durationMillis = 90,
            easing = BondMotionEasing.EmphasizedAccelerate,
        ),
    )
}

/**
 * Material 3 top-level destination transition.
 *
 * Navigation-bar destinations have no spatial relationship, so changing tabs uses a quick fade
 * instead of implying that the screens form a horizontally swipeable sequence.
 */
fun bondTopLevelFade(enabled: Boolean): ContentTransform {
    if (!enabled) return EnterTransition.None togetherWith ExitTransition.None
    return fadeIn(
        animationSpec = tween(
            durationMillis = 180,
            delayMillis = 45,
            easing = BondMotionEasing.Standard,
        ),
    ) togetherWith fadeOut(
        animationSpec = tween(
            durationMillis = 90,
            easing = BondMotionEasing.Standard,
        ),
    )
}

fun bondSharedZEnter(enabled: Boolean): EnterTransition {
    if (!enabled) return EnterTransition.None
    return fadeIn(
        animationSpec = tween(
            durationMillis = BondMotionDuration.SharedAxis,
            easing = BondMotionEasing.EmphasizedDecelerate,
        ),
    ) + scaleIn(
        initialScale = 0.96f,
        animationSpec = tween(
            durationMillis = BondMotionDuration.SharedAxis,
            easing = BondMotionEasing.EmphasizedDecelerate,
        ),
    )
}

fun bondSharedZExit(enabled: Boolean): ExitTransition {
    if (!enabled) return ExitTransition.None
    return fadeOut(
        animationSpec = tween(
            durationMillis = BondMotionDuration.EffectShort,
            easing = BondMotionEasing.EmphasizedAccelerate,
        ),
    ) + scaleOut(
        targetScale = 0.98f,
        animationSpec = tween(
            durationMillis = BondMotionDuration.EffectShort,
            easing = BondMotionEasing.EmphasizedAccelerate,
        ),
    )
}

/**
 * Material forward transition used for list -> message detail.
 *
 * Thunderbird's phone layout uses a ViewSwitcher with two full-width translate animations. The
 * target reader is created synchronously first, then the list and reader move together.
 */
fun bondForwardEnter(enabled: Boolean): EnterTransition {
    if (!enabled) return EnterTransition.None
    return slideInHorizontally(
        initialOffsetX = { fullWidth -> fullWidth },
        animationSpec = tween(
            durationMillis = THUNDERBIRD_SWITCH_DURATION_MS,
            easing = ThunderbirdSwitcherEasing,
        ),
    )
}

fun bondForwardBackgroundExit(enabled: Boolean): ExitTransition {
    if (!enabled) return ExitTransition.None
    return slideOutHorizontally(
        targetOffsetX = { fullWidth -> -fullWidth },
        animationSpec = tween(
            durationMillis = THUNDERBIRD_SWITCH_DURATION_MS,
            easing = ThunderbirdSwitcherEasing,
        ),
    )
}

fun bondBackwardBackgroundEnter(enabled: Boolean): EnterTransition {
    if (!enabled) return EnterTransition.None
    return slideInHorizontally(
        initialOffsetX = { fullWidth -> -fullWidth },
        animationSpec = tween(
            durationMillis = THUNDERBIRD_SWITCH_DURATION_MS,
            easing = ThunderbirdSwitcherEasing,
        ),
    )
}

fun bondBackwardExit(enabled: Boolean): ExitTransition {
    if (!enabled) return ExitTransition.None
    return slideOutHorizontally(
        targetOffsetX = { fullWidth -> fullWidth },
        animationSpec = tween(
            durationMillis = THUNDERBIRD_SWITCH_DURATION_MS,
            easing = ThunderbirdSwitcherEasing,
        ),
    )
}

/** Lightweight fade retained for FAB container targets. */
fun bondContainerEnter(enabled: Boolean): EnterTransition {
    if (!enabled) return EnterTransition.None
    return fadeIn(
        animationSpec = tween(
            durationMillis = BondMotionDuration.ElementEnter,
            delayMillis = 60,
            easing = BondMotionEasing.EmphasizedDecelerate,
        ),
    )
}

fun bondContainerExit(enabled: Boolean): ExitTransition {
    if (!enabled) return ExitTransition.None
    return fadeOut(
        animationSpec = tween(
            durationMillis = BondMotionDuration.EffectQuick,
            easing = BondMotionEasing.EmphasizedAccelerate,
        ),
    )
}

/** Bottom-origin transition for compose/mail sheets. The source page stays spatially stable. */
fun bondBottomSheetEnter(enabled: Boolean): EnterTransition {
    if (!enabled) return EnterTransition.None
    return slideInVertically(
        initialOffsetY = { fullHeight -> fullHeight },
        animationSpec = tween(
            durationMillis = BondMotionDuration.ContainerTransform,
            easing = BondMotionEasing.EmphasizedDecelerate,
        ),
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = BondMotionDuration.ElementEnter,
            delayMillis = 36,
            easing = BondMotionEasing.EmphasizedDecelerate,
        ),
    )
}

fun bondBottomSheetExit(enabled: Boolean): ExitTransition {
    if (!enabled) return ExitTransition.None
    return slideOutVertically(
        targetOffsetY = { fullHeight -> fullHeight },
        animationSpec = tween(
            durationMillis = BondMotionDuration.SharedAxis,
            easing = BondMotionEasing.EmphasizedAccelerate,
        ),
    ) + fadeOut(
        animationSpec = tween(
            durationMillis = BondMotionDuration.EffectShort,
            easing = BondMotionEasing.EmphasizedAccelerate,
        ),
    )
}

fun bondSharedXEnter(enabled: Boolean, forward: Boolean): EnterTransition {
    if (!enabled) return EnterTransition.None
    return slideInHorizontally(
        initialOffsetX = { fullWidth -> if (forward) fullWidth / 5 else -fullWidth / 5 },
        animationSpec = tween(
            durationMillis = 190,
            easing = BondMotionEasing.EmphasizedDecelerate,
        ),
    )
}

fun bondSharedXExit(enabled: Boolean, forward: Boolean): ExitTransition {
    if (!enabled) return ExitTransition.None
    return slideOutHorizontally(
        targetOffsetX = { fullWidth -> if (forward) -fullWidth / 8 else fullWidth },
        animationSpec = tween(
            durationMillis = 175,
            easing = BondMotionEasing.EmphasizedAccelerate,
        ),
    )
}
