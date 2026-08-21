package com.bond.mail.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.bond.mail.data.settings.UiStyle
import com.bond.mail.ui.i18n.LocalJsonStrings
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar as MiuixSmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.extra.SuperDialog as MiuixSuperDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SmoothRoundedCornerShape
import top.yukonga.miuix.kmp.utils.pressSink

/** Shared secondary-screen top bar that follows the selected UI system. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BondTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.bondSurfaces.page,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    when (LocalUiStyle.current) {
        UiStyle.MATERIAL3 -> TopAppBar(
            title = { Text(title) },
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = containerColor,
                scrolledContainerColor = MaterialTheme.bondSurfaces.chrome,
            ),
        )

        UiStyle.MIUIX -> MiuixSmallTopAppBar(
            title = title,
            modifier = modifier,
            color = MiuixTheme.colorScheme.background,
            navigationIcon = navigationIcon,
            actions = actions,
        )
    }
}

/** Icon actions use MIUIX's elastic press response instead of Material ripple. */
@Composable
fun BondIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    when (LocalUiStyle.current) {
        UiStyle.MATERIAL3 -> IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            content = content,
        )

        UiStyle.MIUIX -> MiuixIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            content = content,
        )
    }
}

/** One entry in a themed anchored popup menu. */
class BondMenuEntry(
    val text: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val leadingContent: (@Composable () -> Unit)? = null,
    val selected: Boolean = false,
    val destructive: Boolean = false,
)

/**
 * Anchored popup menu. Material keeps DropdownMenu; MIUIX uses its smooth floating surface,
 * sink press response and haptics instead of Material ripple.
 */
@Composable
fun BondPopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    entries: List<BondMenuEntry>,
    modifier: Modifier = Modifier,
    anchor: @Composable () -> Unit,
) {
    var anchorHeight by remember { mutableStateOf(0) }
    Box(modifier = modifier.onSizeChanged { anchorHeight = it.height }) {
        anchor()
        when (LocalUiStyle.current) {
            UiStyle.MATERIAL3 -> DropdownMenu(
                expanded = expanded,
                onDismissRequest = onDismissRequest,
            ) {
                entries.forEach { entry ->
                    DropdownMenuItem(
                        text = { Text(entry.text) },
                        leadingIcon = entry.leadingContent ?: entry.icon?.let { icon ->
                            ({ androidx.compose.material3.Icon(icon, contentDescription = null) })
                        },
                        trailingIcon = if (entry.selected) {
                            ({
                                androidx.compose.material3.Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Check,
                                    contentDescription = null,
                                )
                            })
                        } else {
                            null
                        },
                        onClick = entry.onClick,
                    )
                }
            }

            UiStyle.MIUIX -> if (expanded) {
                val shape = remember { SmoothRoundedCornerShape(16.dp) }
                val haptics = LocalHapticFeedback.current
                Popup(
                    alignment = Alignment.TopEnd,
                    offset = IntOffset(0, anchorHeight),
                    onDismissRequest = onDismissRequest,
                    properties = PopupProperties(focusable = true),
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(min = 190.dp, max = 300.dp)
                            .shadow(11.dp, shape)
                            .clip(shape)
                            .background(MiuixTheme.colorScheme.surfaceContainer)
                            .padding(vertical = 8.dp),
                    ) {
                        entries.forEach { entry ->
                            val interaction = remember(entry) { MutableInteractionSource() }
                            val contentColor = when {
                                entry.destructive -> MaterialTheme.colorScheme.error
                                entry.selected -> MiuixTheme.colorScheme.primary
                                else -> MiuixTheme.colorScheme.onSurface
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pressSink(interaction)
                                    .clickable(
                                        interactionSource = interaction,
                                        indication = null,
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                            entry.onClick()
                                        },
                                    )
                                    .padding(horizontal = 18.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                entry.leadingContent?.invoke() ?: entry.icon?.let { icon ->
                                    androidx.compose.material3.Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp),
                                        tint = contentColor,
                                    )
                                }
                                if (entry.leadingContent != null || entry.icon != null) {
                                    Spacer(Modifier.size(12.dp))
                                }
                                MiuixText(
                                    text = entry.text,
                                    modifier = Modifier.weight(1f),
                                    color = contentColor,
                                    fontSize = MiuixTheme.textStyles.body1.fontSize,
                                    fontWeight = if (entry.selected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                                if (entry.selected) {
                                    androidx.compose.material3.Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.padding(start = 12.dp).size(20.dp),
                                        tint = contentColor,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Text-only actions used by sheets and confirmation dialogs. */
@Composable
fun BondTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    destructive: Boolean = false,
) {
    when (LocalUiStyle.current) {
        UiStyle.MATERIAL3 -> TextButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        ) {
            Text(
                text = text,
                color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        }

        UiStyle.MIUIX -> MiuixTextButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = if (destructive) {
                top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColors(
                    color = MaterialTheme.colorScheme.errorContainer,
                    disabledColor = MaterialTheme.colorScheme.surfaceVariant,
                    textColor = MaterialTheme.colorScheme.onErrorContainer,
                    disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (primary) {
                top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary()
            } else {
                top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColors()
            },
        )
    }
}

/**
 * Form input adapter. MIUIX uses its filled smooth-corner field and places help/error copy below
 * the field; Material keeps the standard outlined field behavior.
 */
@Composable
fun BondTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    cornerRadius: Dp = 18.dp,
) {
    when (LocalUiStyle.current) {
        UiStyle.MATERIAL3 -> OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            label = { Text(label) },
            placeholder = placeholder?.let { hint -> ({ Text(hint) }) },
            supportingText = supportingText?.let { help -> ({ Text(help) }) },
            enabled = enabled,
            readOnly = readOnly,
            isError = isError,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            shape = top.yukonga.miuix.kmp.utils.SmoothRoundedCornerShape(cornerRadius),
        )

        UiStyle.MIUIX -> {
            val normalizedFieldModifier: (Modifier) -> Modifier = { base ->
                if (singleLine) base.heightIn(min = 64.dp) else base
            }
            val field: @Composable (Modifier) -> Unit = { fieldModifier ->
                MiuixTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = normalizedFieldModifier(fieldModifier),
                insideMargin = DpSize(16.dp, if (singleLine) 14.dp else 16.dp),
                backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                cornerRadius = cornerRadius,
                label = if (value.isEmpty() && placeholder != null) placeholder else label,
                labelColor = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MiuixTheme.colorScheme.onSecondaryContainer
                },
                borderColor = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MiuixTheme.colorScheme.primary
                },
                enabled = enabled,
                readOnly = readOnly,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                singleLine = singleLine,
                minLines = minLines,
                maxLines = maxLines,
                visualTransformation = visualTransformation,
                )
            }
            if (supportingText == null) {
                field(modifier)
            } else {
                Column(modifier = modifier) {
                    field(Modifier.fillMaxWidth())
                    MiuixText(
                        text = supportingText,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
                        color = if (isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        },
                        style = MiuixTheme.textStyles.footnote1,
                    )
                }
            }
        }
    }
}

/** Search input used by the expanding mail-search overlay. */
@Composable
fun BondSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    when (LocalUiStyle.current) {
        UiStyle.MATERIAL3 -> TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            singleLine = true,
            placeholder = { Text(placeholder) },
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        )

        UiStyle.MIUIX -> MiuixTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            insideMargin = DpSize(0.dp, 0.dp),
            backgroundColor = Color.Transparent,
            cornerRadius = 28.dp,
            label = placeholder,
            useLabelAsPlaceholder = true,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            singleLine = true,
        )
    }
}

/** AlertDialog-compatible slot API backed by MIUIX SuperDialog when MIUIX is selected. */
@Composable
fun BondAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
) {
    when (LocalUiStyle.current) {
        UiStyle.MATERIAL3 -> AlertDialog(
            onDismissRequest = onDismissRequest,
            title = title,
            text = text,
            confirmButton = confirmButton,
            dismissButton = dismissButton,
        )

        UiStyle.MIUIX -> {
            val show = remember { mutableStateOf(true) }
            // SuperDialog renders its slot through MIUIX's popup host. Capture and explicitly
            // forward the app language because popup-host content is not guaranteed to inherit
            // application-owned CompositionLocals (otherwise tr() falls back to raw key names).
            val jsonStrings = LocalJsonStrings.current
            MiuixSuperDialog(
                show = show,
                onDismissRequest = {
                    show.value = false
                    onDismissRequest()
                },
            ) {
                CompositionLocalProvider(LocalJsonStrings provides jsonStrings) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        CompositionLocalProvider(
                            androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.titleLarge,
                        ) {
                            title()
                        }
                        text?.invoke()
                        Spacer(Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (dismissButton != null) {
                                Column(Modifier.weight(1f)) { dismissButton() }
                            }
                            Column(Modifier.weight(1f)) { confirmButton() }
                        }
                    }
                }
            }
        }
    }
}
