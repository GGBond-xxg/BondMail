package com.bond.mail.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.bond.mail.ui.glass.components.LiquidButton
import com.bond.mail.ui.glass.components.LiquidToggle

@Composable
internal fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    destructive: Boolean = false,
    iconOnly: Boolean = false,
    contentColor: Color = Color.Unspecified,
    content: @Composable RowScope.() -> Unit,
) {
    val accent = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val foreground = if (contentColor != Color.Unspecified) contentColor else if (primary) Color.White else if (destructive) accent else MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    CompositionLocalProvider(LocalContentColor provides foreground) {
        LiquidButton(
            onClick = onClick, backdrop = glassBackdrop(),
            modifier = modifier.alpha(if (enabled) 1f else 0.38f), enabled = enabled,
            tint = if (primary) accent else Color.Unspecified,
            surfaceColor = if (primary) Color.Unspecified else surface.copy(alpha = if (LocalGlassEffects.current) 0.38f else 1f),
            contentPadding = PaddingValues(horizontal = if (iconOnly) 0.dp else 18.dp),
            content = content,
        )
    }
}

@Composable
internal fun GlassIconButton(onClick: () -> Unit, modifier: Modifier, enabled: Boolean, content: @Composable () -> Unit) {
    GlassButton(onClick, modifier.size(48.dp).padding(2.dp), enabled, iconOnly = true) { content() }
}

@Composable
internal fun GlassSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier, enabled: Boolean) {
    val currentChecked by rememberUpdatedState(checked)
    val currentCallback by rememberUpdatedState(onCheckedChange)
    LiquidToggle(
        selected = { currentChecked }, onSelect = { currentCallback(it) },
        backdrop = glassBackdrop(), modifier = modifier.alpha(if (enabled) 1f else 0.38f), enabled = enabled,
    )
}

@Composable
internal fun GlassTopAppBar(title: String, modifier: Modifier, navigationIcon: @Composable () -> Unit, actions: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 60.dp).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        navigationIcon()
        Text(title, Modifier.weight(1f).padding(horizontal = 4.dp), style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
internal fun <T> GlassSettingDropdown(
    title: String, subtitle: String?, options: List<Pair<T, String>>, selected: T,
    onSelect: (T) -> Unit, onSelectAt: ((T, Offset) -> Unit)?,
) {
    var expanded by remember { mutableStateOf(false) }
    var center by remember { mutableStateOf(Offset.Zero) }
    var pending by remember { mutableStateOf<Pair<T, Offset>?>(null) }
    LaunchedEffect(pending) {
        pending?.let { (value, origin) ->
            withFrameNanos { }
            pending = null
            onSelectAt?.invoke(value, origin) ?: onSelect(value)
        }
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        BondPopupMenu(expanded, { expanded = false }, options.map { (value, label) ->
            BondMenuEntry(label, { expanded = false; if (value != selected) pending = value to center }, selected = value == selected)
        }) {
            GlassButton({ expanded = true }, Modifier.widthIn(max = 180.dp).onGloballyPositioned { center = it.boundsInWindow().center }) {
                Text(options.firstOrNull { it.first == selected }?.second.orEmpty(), maxLines = 1, color = MaterialTheme.colorScheme.primary)
                Text("⌄", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun GlassPopup(expanded: Boolean, onDismissRequest: () -> Unit, entries: List<BondMenuEntry>, anchorHeight: Int) {
    if (!expanded) return
    val backdrop = LocalGlassWindowBackdrop.current
    Popup(
        alignment = Alignment.TopEnd, offset = IntOffset(0, anchorHeight),
        onDismissRequest = onDismissRequest, properties = PopupProperties(focusable = true),
    ) {
        CompositionLocalProvider(LocalGlassBackdrop provides null) {
            Column(
                Modifier.padding(8.dp).widthIn(min = 200.dp, max = 300.dp).width(IntrinsicSize.Max)
                    .glassSurface(backdrop, RoundedCornerShape(28.dp), prominent = true)
                    .heightIn(max = 440.dp).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
            ) {
                entries.forEachIndexed { index, entry ->
                    val foreground = if (entry.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    CompositionLocalProvider(LocalContentColor provides foreground) {
                        Row(
                            Modifier.fillMaxWidth().clickable(onClick = entry.onClick).heightIn(min = 48.dp).padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            entry.leadingContent?.invoke() ?: entry.icon?.let { Icon(it, null, Modifier.size(22.dp)) }
                            Text(entry.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            if (entry.selected) Icon(Icons.Rounded.Check, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (index < entries.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                }
            }
        }
    }
}

internal val LocalGlassDialogAction = staticCompositionLocalOf { false }

@Composable
internal fun GlassAlertDialog(
    onDismissRequest: () -> Unit, title: @Composable () -> Unit, text: @Composable (() -> Unit)?,
    confirmButton: @Composable () -> Unit, dismissButton: @Composable (() -> Unit)?, neutralButton: @Composable (() -> Unit)?,
) {
    val backdrop = LocalGlassWindowBackdrop.current
    Dialog(onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalGlassBackdrop provides null, LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Column(
                Modifier.padding(24.dp).widthIn(max = 380.dp).fillMaxWidth()
                    .glassSurface(backdrop, RoundedCornerShape(36.dp), prominent = true)
                    .heightIn(max = 620.dp).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)) { title() }
                if (text != null) {
                    Box(Modifier.weight(1f, fill = false)) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)) { text() }
                    }
                }
                // A vertical action group also fits three actions and enlarged system text.
                CompositionLocalProvider(LocalGlassDialogAction provides true) {
                    confirmButton()
                    neutralButton?.invoke()
                    dismissButton?.invoke()
                }
            }
        }
    }
}

@Composable
internal fun GlassTextField(
    value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier,
    placeholder: String?, supportingText: String?, enabled: Boolean, readOnly: Boolean, isError: Boolean,
    singleLine: Boolean, minLines: Int, maxLines: Int, keyboardOptions: KeyboardOptions, keyboardActions: KeyboardActions,
    visualTransformation: VisualTransformation, leadingIcon: @Composable (() -> Unit)?, trailingIcon: @Composable (() -> Unit)?,
) {
    TextField(
        value, onValueChange, modifier, enabled = enabled, readOnly = readOnly,
        textStyle = MaterialTheme.typography.bodyLarge,
        label = { Text(label) }, placeholder = placeholder?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } }, isError = isError,
        singleLine = singleLine, minLines = minLines, maxLines = maxLines,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, visualTransformation = visualTransformation,
        leadingIcon = leadingIcon, trailingIcon = trailingIcon, shape = RoundedCornerShape(20.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent,
        ),
    )
}
