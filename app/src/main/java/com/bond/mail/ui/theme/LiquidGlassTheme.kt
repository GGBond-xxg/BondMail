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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bond.mail.data.settings.UiStyle
import com.bond.mail.ui.motion.bondMotionEnabled
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow

/** A separate visual system: neutral grouped content, blue actions and optical navigation. */
internal fun liquidGlassColors(dark: Boolean) = if (dark) darkColorScheme(
    primary = Color(0xFF409CFF), onPrimary = Color.White,
    primaryContainer = Color(0xFF163A60), onPrimaryContainer = Color(0xFFC5E3FF),
    secondary = Color(0xFF8E8E93), secondaryContainer = Color(0xFF38383A),
    onSecondaryContainer = Color.White, background = Color(0xFF000000),
    onBackground = Color(0xFFF5F5F7), surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFF5F5F7), onSurfaceVariant = Color(0xFFAEAEB2),
    surfaceVariant = Color(0xFF2C2C2E), outline = Color(0xFF636366),
    outlineVariant = Color(0xFF38383A), error = Color(0xFFFF6961),
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF1C1C1E),
    surfaceContainer = Color(0xFF242426), surfaceContainerHigh = Color(0xFF2C2C2E),
    surfaceContainerHighest = Color(0xFF38383A), surfaceBright = Color(0xFF2C2C2E),
) else lightColorScheme(
    primary = Color(0xFF007AFF), onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEEFF), onPrimaryContainer = Color(0xFF004488),
    secondary = Color(0xFF636366), secondaryContainer = Color(0xFFE5E5EA),
    onSecondaryContainer = Color(0xFF1C1C1E), background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1C1C1E), surface = Color.White,
    onSurface = Color(0xFF1C1C1E), onSurfaceVariant = Color(0xFF636366),
    surfaceVariant = Color(0xFFF2F2F7), outline = Color(0xFF8E8E93),
    outlineVariant = Color(0xFFD1D1D6), error = Color(0xFFD92D20),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2F2F7),
    surfaceContainer = Color(0xFFEFEFF4), surfaceContainerHigh = Color(0xFFE5E5EA),
    surfaceContainerHighest = Color(0xFFD1D1D6), surfaceBright = Color.White,
)

internal val LiquidGlassShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp), large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)
internal val LiquidGlassTypography = Typography().let {
    it.copy(
        headlineMedium = it.headlineMedium.copy(fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp),
        titleLarge = it.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        titleMedium = it.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = it.bodyLarge.copy(fontSize = 17.sp, letterSpacing = 0.sp),
        labelLarge = it.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    )
}

val LocalGlassSettings = compositionLocalOf { com.bond.mail.data.settings.GlassSettings() }

val LocalGlassEffects = staticCompositionLocalOf { false }
// Content-only sources for floating controls. Never provide a source to its own recorded subtree.
val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }
// Only separate-window dialogs/popups may read this whole-window source.
internal val LocalGlassWindowBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
private fun glassEffectsAllowed(): Boolean {
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
        ContextCompat.registerReceiver(context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        context.contentResolver.registerContentObserver(Settings.Secure.getUriFor("high_text_contrast_enabled"), false, observer)
        onDispose { context.unregisterReceiver(receiver); context.contentResolver.unregisterContentObserver(observer) }
    }
    return !constrained && bondMotionEnabled()
}

@Composable
internal fun GlassBackdropHost(content: @Composable () -> Unit) {
    val effects = glassEffectsAllowed()
    val windowBackdrop = rememberLayerBackdrop()
    CompositionLocalProvider(
        LocalGlassEffects provides effects,
        LocalGlassWindowBackdrop provides windowBackdrop.takeIf { effects },
    ) {
        Box(Modifier.fillMaxSize().then(if (effects) Modifier.layerBackdrop(windowBackdrop) else Modifier)) {
            content()
        }
    }
}

@Composable
internal fun glassBackdrop(): Backdrop {
    val surface = MaterialTheme.colorScheme.surface
    val canvas = rememberCanvasBackdrop { drawRect(surface) }
    return LocalGlassBackdrop.current ?: canvas
}

/** Stable, contrast-aware outline shared by controls and their opaque fallback. */
@Composable
internal fun glassControlBorderColor(enabled: Boolean = true, tinted: Boolean = false): Color {
    if (tinted && enabled) return Color.White.copy(alpha = 0.32f)
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return MaterialTheme.colorScheme.onSurface.copy(
        alpha = if (enabled) { if (dark) 0.22f else 0.16f } else { if (dark) 0.10f else 0.08f },
    )
}

/** Catalog optics, with stronger frosting for menus/dialogs and an opaque accessibility fallback. */
@Composable
fun Modifier.glassSurface(
    backdrop: Backdrop? = LocalGlassBackdrop.current,
    shape: Shape = RoundedCornerShape(50),
    prominent: Boolean = false,
    tint: Color = Color.Unspecified,
    chrome: Boolean = false,
    surfaceAlpha: Float? = null,
): Modifier {
    if (LocalUiStyle.current != UiStyle.LIQUID_GLASS) return this
    val optics = LocalGlassSettings.current
    val surface = MaterialTheme.colorScheme.surface
    val dark = surface.luminance() < 0.5f
    val source = backdrop ?: glassBackdrop()
    val edge = glassControlBorderColor()
    if (!LocalGlassEffects.current) return this.clip(shape).background(surface)
        .then(if (chrome) Modifier else Modifier.border(1.dp, edge, shape))
    return drawBackdrop(
        backdrop = source,
        shape = { shape },
        effects = {
            vibrancy()
            blur((optics.blurRadius * if (prominent) 16f / 6f else 1f).dp.toPx())
            if (!chrome) lens((optics.refractionHeight * if (prominent) 20f / 12f else 1f).dp.toPx(),
                (optics.refractionAmount * if (prominent) 1.5f else 1f).dp.toPx(), depthEffect = prominent,
                chromaticAberrationAmount = optics.chromaticAberration)
        },
        highlight = null,
        shadow = if (chrome) null else { { Shadow(radius = 8.dp, color = Color.Black.copy(alpha = if (dark) 0.18f else 0.08f)) } },
        onDrawSurface = {
            drawRect(surface.copy(alpha = surfaceAlpha ?: if (prominent) 0.72f else if (dark) 0.5f else 0.4f))
            if (tint != Color.Unspecified) drawRect(tint.copy(alpha = 0.14f))
        },
    ).then(if (chrome) Modifier else Modifier.border(1.dp, edge, shape))
}

/** Shared optics for the home navigation and message action dock. */
@Composable
internal fun Modifier.glassDockSurface(
    backdrop: Backdrop = glassBackdrop(),
    layerBlock: (androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit)? = null,
): Modifier {
    val optics = LocalGlassSettings.current
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val container = if (light) Color(0xFFFAFAFA) else Color(0xFF121212)
    val edge = glassControlBorderColor()
    if (!LocalGlassEffects.current) return clip(RoundedCornerShape(50)).background(container)
        .border(1.dp, edge, RoundedCornerShape(50))
    return drawBackdrop(
        backdrop = backdrop,
        shape = { RoundedCornerShape(50) },
        effects = { vibrancy(); blur((optics.blurRadius * 8f / 6f).dp.toPx())
            lens((optics.refractionHeight * 2f).dp.toPx(), optics.refractionAmount.dp.toPx(),
                chromaticAberrationAmount = optics.chromaticAberration) },
        layerBlock = layerBlock,
        highlight = null,
        onDrawSurface = { drawRect(container.copy(alpha = 0.4f)) },
    ).border(1.dp, edge, RoundedCornerShape(50))
}
