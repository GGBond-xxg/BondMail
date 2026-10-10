package com.bond.mail.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.bond.mail.data.settings.UiStyle

/** Dialog content stays readable while the floating container follows the current theme. */
@Composable
internal fun BondServiceSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val glass = LocalUiStyle.current == UiStyle.LIQUID_GLASS
    val shape = MaterialTheme.shapes.extraLarge
    Surface(modifier.glassSurface(LocalGlassWindowBackdrop.current, shape, prominent = true),
        shape = shape,
        color = if (glass) Color.Transparent else MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface) {
        CompositionLocalProvider(LocalGlassBackdrop provides null, content = content)
    }
}

/** Filter controls use the same glass material and preserve their selected semantics. */
@Composable
internal fun BondFilterChip(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, label: @Composable () -> Unit) {
    if (LocalUiStyle.current == UiStyle.LIQUID_GLASS) {
        GlassButton(onClick, modifier.semantics { this.selected = selected }, enabled = enabled,
            primary = selected) { label() }
    } else FilterChip(selected, onClick, modifier = modifier, enabled = enabled, label = label)
}

/** Slot-based adapter for existing service forms. */
@Composable
internal fun BondFormField(value: String, onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier, label: @Composable (() -> Unit)? = null,
    enabled: Boolean = true, singleLine: Boolean = false, minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE, isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    if (LocalUiStyle.current == UiStyle.LIQUID_GLASS) TextField(
        value, onValueChange, modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f), RoundedCornerShape(20.dp)), label = label, enabled = enabled, singleLine = singleLine,
        minLines = minLines, maxLines = maxLines, isError = isError,
        visualTransformation = visualTransformation, keyboardOptions = keyboardOptions,
        shape = RoundedCornerShape(20.dp), colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    ) else OutlinedTextField(value, onValueChange, modifier, label = label, enabled = enabled,
        singleLine = singleLine, minLines = minLines, maxLines = maxLines, isError = isError,
        visualTransformation = visualTransformation, keyboardOptions = keyboardOptions)
}

@Composable
internal fun BondFormAction(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    if (LocalUiStyle.current == UiStyle.LIQUID_GLASS) GlassButton(onClick,
        if (LocalGlassDialogAction.current) modifier.fillMaxWidth() else modifier,
        enabled = enabled, contentColor = MaterialTheme.colorScheme.primary, content = content)
    else TextButton(onClick, modifier, enabled = enabled, content = content)
}


/** Keeps menus in older service forms on the selected visual system. */
@Composable
internal fun BondDropdownMenu(expanded: Boolean, onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    if (LocalUiStyle.current != UiStyle.LIQUID_GLASS) {
        DropdownMenu(expanded, onDismissRequest, modifier, content = content)
        return
    }
    if (!expanded) return
    val position = androidx.compose.runtime.remember {
        object : androidx.compose.ui.window.PopupPositionProvider {
            override fun calculatePosition(anchorBounds: androidx.compose.ui.unit.IntRect,
                windowSize: androidx.compose.ui.unit.IntSize, layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                popupContentSize: androidx.compose.ui.unit.IntSize): androidx.compose.ui.unit.IntOffset {
                val x = (anchorBounds.right - popupContentSize.width).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                val y = (if (anchorBounds.bottom + popupContentSize.height <= windowSize.height) anchorBounds.bottom
                    else anchorBounds.top - popupContentSize.height).coerceAtLeast(0)
                return androidx.compose.ui.unit.IntOffset(x, y)
            }
        }
    }
    val backdrop = LocalGlassWindowBackdrop.current
    androidx.compose.ui.window.Popup(position, onDismissRequest, properties = androidx.compose.ui.window.PopupProperties(focusable = true)) {
        Column(modifier.widthIn(min = 200.dp, max = 300.dp).width(IntrinsicSize.Max)
            .glassSurface(backdrop, RoundedCornerShape(28.dp), prominent = true, surfaceAlpha = 0.42f)
            .heightIn(max = 380.dp).verticalScroll(rememberScrollState()).padding(vertical = 8.dp), content = content)
    }
}
