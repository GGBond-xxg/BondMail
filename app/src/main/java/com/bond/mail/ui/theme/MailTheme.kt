package com.bond.mail.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.bond.mail.data.settings.AppSettings
import com.bond.mail.data.settings.ThemeMode
import com.bond.mail.data.settings.UiStyle
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme

private val LightColors = lightColorScheme(
    primary = Color(0xFFFF6B9D),
    secondary = Color(0xFF4F5F7A),
    tertiary = Color(0xFF6E5A96),
    background = Color(0xFFF5F7FC),
    surface = Color(0xFFF8FAFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F5FB),
    surfaceContainer = Color(0xFFEDF1F8),
    surfaceContainerHigh = Color(0xFFE7ECF5),
    surfaceContainerHighest = Color(0xFFE0E6F0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB0C8),
    secondary = Color(0xFFBBC6DC),
    tertiary = Color(0xFFD8B9FF),
    background = Color(0xFF0F1217),
    surface = Color(0xFF101318),
    surfaceContainerLowest = Color(0xFF0B0E13),
    surfaceContainerLow = Color(0xFF171A20),
    surfaceContainer = Color(0xFF1B1E24),
    surfaceContainerHigh = Color(0xFF23272F),
    surfaceContainerHighest = Color(0xFF2B3039),
)

/**
 * Shared surface hierarchy for every BondMail page.
 *
 * Dynamic color exposes several close surface tones. Reading those tones directly in every screen
 * made the inbox, contacts, settings, drawer and composer look unrelated. These semantic roles keep
 * the page/title canvas identical, cards clearly separated and controls consistent in light/dark and
 * Monet themes.
 */
@Immutable
data class BondMailSurfacePalette(
    val page: Color,
    val chrome: Color,
    val content: Color,
    val contentUnread: Color,
    val dock: Color,
    val section: Color,
    val popup: Color,
    val input: Color,
    val drawer: Color,
    val sheet: Color,
)

private val FallbackSurfacePalette = BondMailSurfacePalette(
    page = LightColors.surfaceContainerLow,
    chrome = LightColors.surfaceContainerLow,
    content = LightColors.surfaceContainerLowest,
    contentUnread = LightColors.surfaceContainerLowest,
    dock = LightColors.surfaceContainer,
    section = LightColors.surfaceContainer,
    popup = LightColors.surfaceContainerHigh,
    input = LightColors.surfaceContainerLowest,
    drawer = LightColors.surfaceContainerLow,
    sheet = LightColors.surfaceContainerLow,
)

private val LocalBondMailSurfaces = staticCompositionLocalOf { FallbackSurfacePalette }

val LocalUiStyle = staticCompositionLocalOf { UiStyle.MATERIAL3 }

val MaterialTheme.bondSurfaces: BondMailSurfacePalette
    @Composable
    @ReadOnlyComposable
    get() = LocalBondMailSurfaces.current

private fun buildSurfacePalette(colors: ColorScheme): BondMailSurfacePalette {
    val darkSurface = colors.surfaceContainerLowest.luminance() < 0.5f
    val unreadBlend = if (darkSurface) 0.18f else 0.11f
    return BondMailSurfacePalette(
        // Gmail uses one tinted canvas for the status/title area and list background. The lowest
        // surface then makes each grouped message/contact card readable without heavy shadows.
        page = colors.surfaceContainerLow,
        chrome = colors.surfaceContainerLow,
        content = colors.surfaceContainerLowest,
        // Unread rows need a second cue beyond font weight, especially in dynamic dark themes.
        contentUnread = lerp(
            colors.surfaceContainerLowest,
            colors.primaryContainer,
            unreadBlend,
        ),
        dock = colors.surfaceContainer,
        section = colors.surfaceContainer,
        popup = colors.surfaceContainerHigh,
        input = colors.surfaceContainerLowest,
        drawer = colors.surfaceContainerLow,
        sheet = colors.surfaceContainerLow,
    )
}

@Composable
fun BondMailTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val seedColor = Color(settings.themeColor.argb)
    // MaterialKolor generation is CPU-heavy. Cache both variants by seed so changing only
    // light/dark mode becomes a reference swap instead of regenerating a complete tonal palette.
    val customLightColors = remember(seedColor) {
        dynamicColorScheme(
            seedColor = seedColor,
            isDark = false,
            style = PaletteStyle.TonalSpot,
        )
    }
    val customDarkColors = remember(seedColor) {
        dynamicColorScheme(
            seedColor = seedColor,
            isDark = true,
            style = PaletteStyle.TonalSpot,
        )
    }
    val wallpaperLightColors = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicLightColorScheme(context)
        } else {
            LightColors
        }
    }
    val wallpaperDarkColors = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicDarkColorScheme(context)
        } else {
            DarkColors
        }
    }
    val materialColors = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (dark) wallpaperDarkColors else wallpaperLightColors
        }
        // Turning wallpaper extraction off still keeps the complete Material You tonal
        // system. Only its seed changes, defaulting to BondMail pink.
        !settings.dynamicColor -> if (dark) customDarkColors else customLightColors
        dark -> DarkColors
        else -> LightColors
    }

    // Keep both framework providers at stable composition positions. Only their values change,
    // so switching UI style no longer tears down or relocates the entire navigation/page tree.
    BondMiuixTheme(
        style = settings.uiStyle,
        dark = dark,
        materialColors = materialColors,
        content = content,
    )
}

@Composable
internal fun BondMaterialTheme(
    style: UiStyle,
    colors: ColorScheme,
    shapes: Shapes = Shapes(),
    typography: Typography = Typography(),
    content: @Composable () -> Unit,
) {
    val surfaces = buildSurfacePalette(colors)
    CompositionLocalProvider(
        LocalBondMailSurfaces provides surfaces,
        LocalUiStyle provides style,
    ) {
        MaterialTheme(
            colorScheme = colors,
            shapes = shapes,
            typography = typography,
            content = content,
        )
    }
}
