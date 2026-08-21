package com.bond.mail.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults as MaterialButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import com.bond.mail.data.settings.UiStyle
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Primary action facade backed by the selected design system's real button component. */
@Composable
fun BondPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    contentPadding: PaddingValues? = null,
    content: @Composable RowScope.() -> Unit,
) {
    when (LocalUiStyle.current) {
        UiStyle.MATERIAL3 -> androidx.compose.material3.Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape ?: MaterialButtonDefaults.shape,
            contentPadding = contentPadding ?: MaterialButtonDefaults.ContentPadding,
            content = content,
        )
        UiStyle.MIUIX -> CompositionLocalProvider(
            LocalContentColor provides MiuixTheme.colorScheme.onPrimary,
        ) {
            MiuixButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                colors = MiuixButtonDefaults.buttonColorsPrimary(),
                insideMargin = contentPadding ?: MiuixButtonDefaults.InsideMargin,
                content = content,
            )
        }
    }
}

/** Secondary/outlined action facade with MIUIX's native sink response. */
@Composable
fun BondSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    contentPadding: PaddingValues? = null,
    content: @Composable RowScope.() -> Unit,
) {
    when (LocalUiStyle.current) {
        UiStyle.MATERIAL3 -> androidx.compose.material3.OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape ?: MaterialButtonDefaults.outlinedShape,
            contentPadding = contentPadding ?: MaterialButtonDefaults.ContentPadding,
            content = content,
        )
        UiStyle.MIUIX -> CompositionLocalProvider(
            LocalContentColor provides MiuixTheme.colorScheme.onSurface,
        ) {
            MiuixButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                colors = MiuixButtonDefaults.buttonColors(),
                insideMargin = contentPadding ?: MiuixButtonDefaults.InsideMargin,
                content = content,
            )
        }
    }
}
