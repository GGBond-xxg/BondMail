package com.bond.mail.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.SolidColor
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
import kotlinx.coroutines.delay

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
    val uiStyle = LocalUiStyle.current
    var miuixPopupMounted by remember { mutableStateOf(false) }
    LaunchedEffect(expanded, uiStyle) {
        if (uiStyle != UiStyle.MIUIX) {
            miuixPopupMounted = false
        } else if (expanded) {
            miuixPopupMounted = true
        } else if (miuixPopupMounted) {
            delay(150L)
            miuixPopupMounted = false
        }
    }
    Box(modifier = modifier.onSizeChanged { anchorHeight = it.height }) {
        anchor()
        when (uiStyle) {
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

            UiStyle.MIUIX -> if (miuixPopupMounted) {
                val shape = remember { SmoothRoundedCornerShape(16.dp) }
                val haptics = LocalHapticFeedback.current
                Popup(
                    alignment = Alignment.TopEnd,
                    offset = IntOffset(0, anchorHeight),
                    onDismissRequest = onDismissRequest,
                    properties = PopupProperties(focusable = true),
                ) {
                    AnimatedVisibility(
                        visible = expanded,
                        enter = fadeIn(tween(190)) + scaleIn(
                            initialScale = 0.9f,
                            transformOrigin = TransformOrigin(0.92f, 0f),
                            animationSpec = tween(220),
                        ),
                        exit = fadeOut(tween(120)) + scaleOut(
                            targetScale = 0.94f,
                            transformOrigin = TransformOrigin(0.92f, 0f),
                            animationSpec = tween(150),
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
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
}

/** Text-only actions used by sheets and confirmation dialogs. */
private val LocalBondDialogAction = staticCompositionLocalOf { false }

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
            modifier = if (LocalBondDialogAction.current) {
                modifier.fillMaxWidth().heightIn(min = 58.dp)
            } else {
                modifier
            },
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

        UiStyle.MIUIX -> BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            singleLine = true,
            textStyle = MiuixTheme.textStyles.body1.copy(
                color = MiuixTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
            decorationBox = { input ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    leadingIcon?.invoke()
                    if (leadingIcon != null) Spacer(Modifier.size(10.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            MiuixText(
                                text = placeholder,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                style = MiuixTheme.textStyles.body1,
                            )
                        }
                        input()
                    }
                    if (trailingIcon != null) Spacer(Modifier.size(10.dp))
                    trailingIcon?.invoke()
                }
            },
        )
    }
}

/** AlertDialog-compatible slot API with a caller-owned MIUIX dialog lifecycle. */
@Composable
fun BondAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    neutralButton: @Composable (() -> Unit)? = null,
    dismissButtonWeight: Float = 1f,
) {
    when (LocalUiStyle.current) {
        UiStyle.MATERIAL3 -> AlertDialog(
            onDismissRequest = onDismissRequest,
            title = title,
            text = text,
            confirmButton = confirmButton,
            dismissButton = if (dismissButton != null || neutralButton != null) {
                ({
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        dismissButton?.invoke()
                        neutralButton?.invoke()
                    }
                })
            } else {
                null
            },
        )

        UiStyle.MIUIX -> {
            val show = remember { mutableStateOf(true) }
            // SuperDialog is the MIUIX-native surface/animation. Explicitly turn its popup state
            // off on disposal as well as on outside dismissal so a caller-owned Boolean can never
            // leave an orphaned, non-interactive popup in MiuixPopupHost.
            DisposableEffect(show) {
                onDispose { show.value = false }
            }
            val jsonStrings = LocalJsonStrings.current
            MiuixSuperDialog(
                show = show,
                onDismissRequest = {
                    show.value = false
                    onDismissRequest()
                },
            ) {
                CompositionLocalProvider(
                    LocalJsonStrings provides jsonStrings,
                    LocalContentColor provides MiuixTheme.colorScheme.onSurface,
                    androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.bodyLarge.copy(
                        color = MiuixTheme.colorScheme.onSurface,
                    ),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CompositionLocalProvider(
                                androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.titleLarge.copy(
                                    color = MiuixTheme.colorScheme.onSurface,
                                ),
                            ) {
                                title()
                            }
                        }
                        text?.invoke()
                        Spacer(Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (dismissButton != null) {
                                Row(Modifier.weight(dismissButtonWeight)) {
                                    CompositionLocalProvider(LocalBondDialogAction provides true) {
                                        dismissButton()
                                    }
                                }
                            }
                            if (neutralButton != null) {
                                Box(Modifier.weight(1f)) {
                                    CompositionLocalProvider(LocalBondDialogAction provides true) {
                                        neutralButton()
                                    }
                                }
                            }
                            Box(Modifier.weight(1f)) {
                                CompositionLocalProvider(LocalBondDialogAction provides true) {
                                    confirmButton()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
