package com.bond.mail.ui.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp

/** Central Material 3 motion tokens for BondMail. Do not add animation magic numbers in screens. */
object BondMotionDuration {
    const val Instant = 0
    const val EffectQuick = 90
    const val EffectShort = 120
    const val ElementEnter = 180
    const val MailContentRevisit = 150
    const val MailContentReveal = 260
    const val ChromeReveal = 240
    const val FadeThrough = 240
    const val SharedAxis = 280
    const val ContainerTransform = 340
    const val MaximumNormal = 400
}

object BondMotionEasing {
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.70f, 0.10f, 1.00f)
    val EmphasizedAccelerate = CubicBezierEasing(0.30f, 0.00f, 0.80f, 0.15f)
    val Standard = CubicBezierEasing(0.20f, 0.00f, 0.00f, 1.00f)
}

object BondMotionSpring {
    val PressRelease = spring<Float>(
        dampingRatio = 0.90f,
        stiffness = 700f,
    )

    val Settle = spring<Float>(
        dampingRatio = 0.86f,
        stiffness = 520f,
    )

    val NavigationIndicator = spring<Dp>(
        dampingRatio = 0.90f,
        stiffness = 500f,
    )

    val ImmediateSettle = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh,
    )
}

/** Honors Android's animator-duration setting, including the system's remove-animations mode.
 *
 * This implementation deliberately uses the platform setting instead of Compose's
 * LocalMotionDurationScale because the latter isn't available in every Compose version
 * supported by this project.
 */
@Composable
fun bondMotionEnabled(): Boolean {
    val context = LocalContext.current
    var enabled by remember(context) {
        mutableStateOf(readAnimatorDurationScale(context) > 0f)
    }

    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                enabled = readAnimatorDurationScale(context) > 0f
            }
        }
        val resolver = context.contentResolver
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }

    return enabled
}

private fun readAnimatorDurationScale(context: Context): Float =
    runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
    }.getOrDefault(1f)

fun bondDuration(enabled: Boolean, durationMillis: Int): Int =
    if (enabled) durationMillis else BondMotionDuration.Instant
