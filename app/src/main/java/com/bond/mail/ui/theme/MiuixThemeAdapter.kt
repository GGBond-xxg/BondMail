package com.bond.mail.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.bond.mail.data.settings.UiStyle
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme

/**
 * MIUIX is intentionally isolated here. Existing screens consume semantic BondMail surface roles
 * and Material-compatible tokens, while real MIUIX components read the native [MiuixTheme]. This
 * lets the style change in-place without replacing the navigation host or any business state.
 */
@Composable
internal fun BondMiuixTheme(
    style: UiStyle,
    dark: Boolean,
    materialColors: ColorScheme,
    content: @Composable () -> Unit,
) {
    val miuixColors = if (dark) miuixDarkColorScheme() else miuixLightColorScheme()

    MiuixTheme(colors = miuixColors) {
        val nativeColors = MiuixTheme.colorScheme
        val miuixMaterialBridge = if (dark) {
            darkColorScheme(
                primary = nativeColors.primary,
                onPrimary = nativeColors.onPrimary,
                primaryContainer = nativeColors.primaryContainer,
                onPrimaryContainer = nativeColors.onPrimaryContainer,
                secondary = nativeColors.primaryVariant,
                onSecondary = nativeColors.onPrimaryVariant,
                secondaryContainer = nativeColors.tertiaryContainer,
                onSecondaryContainer = nativeColors.onTertiaryContainer,
                tertiary = nativeColors.primaryVariant,
                onTertiary = nativeColors.onPrimaryVariant,
                tertiaryContainer = nativeColors.tertiaryContainer,
                onTertiaryContainer = nativeColors.onTertiaryContainer,
                background = nativeColors.background,
                onBackground = nativeColors.onBackground,
                surface = nativeColors.surface,
                onSurface = nativeColors.onSurface,
                surfaceVariant = nativeColors.surfaceVariant,
                onSurfaceVariant = nativeColors.onSurfaceVariantSummary,
                outline = nativeColors.outline,
                outlineVariant = nativeColors.dividerLine,
                surfaceContainerLowest = nativeColors.background,
                surfaceContainerLow = nativeColors.surfaceContainer,
                surfaceContainer = nativeColors.surface,
                surfaceContainerHigh = nativeColors.surfaceContainerHigh,
                surfaceContainerHighest = nativeColors.surfaceContainerHighest,
            )
        } else {
            lightColorScheme(
                primary = nativeColors.primary,
                onPrimary = nativeColors.onPrimary,
                primaryContainer = nativeColors.primaryContainer,
                onPrimaryContainer = nativeColors.onPrimaryContainer,
                secondary = nativeColors.primaryVariant,
                onSecondary = nativeColors.onPrimaryVariant,
                secondaryContainer = nativeColors.tertiaryContainer,
                onSecondaryContainer = nativeColors.onTertiaryContainer,
                tertiary = nativeColors.primaryVariant,
                onTertiary = nativeColors.onPrimaryVariant,
                tertiaryContainer = nativeColors.tertiaryContainer,
                onTertiaryContainer = nativeColors.onTertiaryContainer,
                background = nativeColors.background,
                onBackground = nativeColors.onBackground,
                surface = nativeColors.surface,
                onSurface = nativeColors.onSurface,
                surfaceVariant = nativeColors.surfaceVariant,
                onSurfaceVariant = nativeColors.onSurfaceVariantSummary,
                outline = nativeColors.outline,
                outlineVariant = nativeColors.dividerLine,
                surfaceContainerLowest = nativeColors.surface,
                surfaceContainerLow = nativeColors.background,
                surfaceContainer = nativeColors.surfaceContainer,
                surfaceContainerHigh = nativeColors.surfaceContainerHigh,
                surfaceContainerHighest = nativeColors.surfaceContainerHighest,
            )
        }

        BondMaterialTheme(
            style = style,
            colors = if (style == UiStyle.MIUIX) miuixMaterialBridge else materialColors,
            shapes = if (style == UiStyle.MIUIX) MiuixCompatibleShapes else MaterialCompatibleShapes,
            content = content,
        )
    }
}

private val MiuixCompatibleShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val MaterialCompatibleShapes = Shapes()
