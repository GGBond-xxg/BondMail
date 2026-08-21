package com.bond.mail.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bond.mail.data.settings.UiStyle
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme
import top.yukonga.miuix.kmp.utils.SmoothRoundedCornerShape
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

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
            typography = if (style == UiStyle.MIUIX) {
                miuixCompatibleTypography()
            } else {
                MaterialCompatibleTypography
            },
        ) {
            // MIUIX popups and dialogs are hosted above the app's existing navigation tree.
            // Keeping this Box stable for both styles prevents a style switch from recreating
            // the navigation host while still enabling native MIUIX overlays.
            Box(Modifier.fillMaxSize()) {
                content()
                if (style == UiStyle.MIUIX) {
                    MiuixPopupHost()
                }
            }
        }
    }
}

private val MiuixCompatibleShapes = Shapes(
    extraSmall = SmoothRoundedCornerShape(10.dp),
    small = SmoothRoundedCornerShape(14.dp),
    medium = SmoothRoundedCornerShape(20.dp),
    large = SmoothRoundedCornerShape(28.dp),
    extraLarge = SmoothRoundedCornerShape(32.dp),
)

private val MaterialCompatibleShapes = Shapes()
private val MaterialCompatibleTypography = Typography()

@Composable
private fun miuixCompatibleTypography(): Typography {
    val textStyles = MiuixTheme.textStyles
    fun androidx.compose.ui.text.TextStyle.materialCompatible() = copy(color = Color.Unspecified)

    return remember(textStyles) {
        Typography(
            displayLarge = textStyles.title1.materialCompatible(),
            displayMedium = textStyles.title1.materialCompatible(),
            displaySmall = textStyles.title2.materialCompatible(),
            headlineLarge = textStyles.title1.materialCompatible(),
            headlineMedium = textStyles.title2.materialCompatible(),
            headlineSmall = textStyles.title3.materialCompatible(),
            titleLarge = textStyles.title2.materialCompatible(),
            titleMedium = textStyles.title3.materialCompatible(),
            titleSmall = textStyles.title4.materialCompatible(),
            bodyLarge = textStyles.paragraph.materialCompatible(),
            bodyMedium = textStyles.body1.materialCompatible(),
            bodySmall = textStyles.body2.materialCompatible(),
            labelLarge = textStyles.button.materialCompatible(),
            labelMedium = textStyles.subtitle.materialCompatible(),
            labelSmall = textStyles.footnote1.materialCompatible(),
        )
    }
}
