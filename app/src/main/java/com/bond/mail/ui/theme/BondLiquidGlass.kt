package com.bond.mail.ui.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bond.mail.data.settings.UiStyle
import com.bond.mail.ui.motion.bondMotionEnabled
import com.kyant.liquidglass.GlassStyle
import com.kyant.liquidglass.LiquidGlassProviderState
import com.kyant.liquidglass.highlight.GlassHighlight
import com.kyant.liquidglass.liquidGlass
import com.kyant.liquidglass.material.GlassMaterial
import com.kyant.liquidglass.refraction.InnerRefraction
import com.kyant.liquidglass.refraction.RefractionAmount
import com.kyant.liquidglass.refraction.RefractionHeight
import com.kyant.liquidglass.shadow.GlassShadow

// Only provide this to controls outside the recorded content. Recording a consumer in its own
// backdrop would create a recursive RenderNode graph. Each screen owns its content-only source.
val LocalGlassBackdrop = staticCompositionLocalOf<LiquidGlassProviderState?> { null }

@Composable
fun bondGlassEffectsEnabled(): Boolean {
    if (LocalUiStyle.current != UiStyle.LIQUID_GLASS || Build.VERSION.SDK_INT < 33) return false
    val context = LocalContext.current
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    fun constrained() = power.isPowerSaveMode ||
        Settings.Secure.getInt(context.contentResolver, "high_text_contrast_enabled", 0) != 0
    var constrained by remember(context) { mutableStateOf(constrained()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { constrained = constrained() }
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { constrained = constrained() }
        }
        ContextCompat.registerReceiver(context, receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor("high_text_contrast_enabled"), false, observer,
        )
        onDispose {
            context.unregisterReceiver(receiver)
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
    return !constrained && bondMotionEnabled()
}

/** Regular, readability-first glass. Content and foreground glyphs never enter the shader. */
@Composable
fun Modifier.bondLiquidGlass(
    backdrop: LiquidGlassProviderState?,
    shape: CornerBasedShape,
    tint: Color = MaterialTheme.colorScheme.surface,
): Modifier {
    if (backdrop == null) return this
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val semanticTint = tint != MaterialTheme.colorScheme.surface
    val style = remember(shape, tint, dark, semanticTint) {
        GlassStyle(
            shape = shape,
            innerRefraction = InnerRefraction(
                height = RefractionHeight(8.dp),
                amount = RefractionAmount((-12).dp),
                depthEffect = 0.35f,
            ),
            material = GlassMaterial(
                blurRadius = 6.dp,
                brush = SolidColor(tint),
                // Primary/error actions retain their on-color contrast over arbitrary mail.
                alpha = if (semanticTint) 0.92f else if (dark) 0.78f else 0.68f,
            ),
            highlight = GlassHighlight.Dynamic(
                width = 1.dp,
                color = Color.White.copy(alpha = if (dark) 0.24f else 0.65f),
            ),
            shadow = GlassShadow.None,
        )
    }
    return liquidGlass(backdrop, style)
}
